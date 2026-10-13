package com.controldesk.data

import com.controldesk.protocol.WsClientMessage
import com.controldesk.protocol.WsMessageType
import com.controldesk.protocol.WsServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class RealtimeRequestsTest {
    @Test
    fun correlatesRepliesForConcurrentCommands() = runTest {
        val requests = RealtimeRequests()
        val sent = mutableListOf<WsClientMessage>()
        val first = async { requests.execute(WsClientMessage(type = "volume"), 1000) { sent += it } }
        val second = async { requests.execute(WsClientMessage(type = "mic"), 1000) { sent += it } }
        testScheduler.runCurrent()
        requests.complete(WsServerMessage(type = "ack", id = sent[1].id, message = "second"))
        requests.complete(WsServerMessage(type = "ack", id = sent[0].id, message = "first"))
        assertEquals("first", first.await().message)
        assertEquals("second", second.await().message)
        assertEquals(0, requests.pendingCount())
    }

    @Test
    fun remoteErrorsFailTheCommand() = runTest {
        for (reply in listOf(
            WsServerMessage(type = WsMessageType.ERROR, message = "Denied"),
            WsServerMessage(type = WsMessageType.ACK, ok = false, message = "Denied"),
        )) {
            val requests = RealtimeRequests()
            val error = assertFailsWith<RemoteCommandRejectedException> {
                requests.execute(WsClientMessage(type = "launch"), 1000) {
                    requests.complete(reply.copy(id = it.id))
                }
            }
            assertEquals("Denied", error.message)
            assertEquals(0, requests.pendingCount())
        }
    }

    @Test
    fun timeoutRemovesRequestAndIgnoresLateReply() = runTest {
        val requests = RealtimeRequests()
        var id: String? = null
        val error = assertFailsWith<CommandOutcomeUnknownException> {
            requests.execute(WsClientMessage(type = "launch"), 100) { id = it.id }
        }
        assertIs<kotlinx.coroutines.TimeoutCancellationException>(error.cause)
        assertEquals(0, requests.pendingCount())
        requests.complete(WsServerMessage(type = "ack", id = id))
        assertEquals(0, requests.pendingCount())
    }

    @Test
    fun failedSendRemovesRequest() = runTest {
        val requests = RealtimeRequests()
        val error = assertFailsWith<CommandOutcomeUnknownException> {
            requests.execute(WsClientMessage(type = "volume"), 1000) { error("Send failed") }
        }
        assertEquals("Send failed", error.cause?.message)
        assertEquals(0, requests.pendingCount())
    }

    @Test
    fun cancellationDuringSendRemovesRequest() = runTest {
        val requests = RealtimeRequests()
        val started = CompletableDeferred<Unit>()
        val command = async {
            requests.execute(WsClientMessage(type = "mic"), 1000) {
                started.complete(Unit)
                awaitCancellation()
            }
        }
        started.await()
        command.cancel()
        command.join()
        assertEquals(0, requests.pendingCount())
    }

    @Test
    fun disconnectFailsPendingCommands() = runTest {
        val requests = RealtimeRequests()
        val error = assertFailsWith<CommandOutcomeUnknownException> {
            requests.execute(WsClientMessage(type = "mic"), 1000) {
                requests.failPending("disconnected")
            }
        }
        assertEquals("disconnected", error.cause?.message)
        assertEquals(0, requests.pendingCount())
    }
}
