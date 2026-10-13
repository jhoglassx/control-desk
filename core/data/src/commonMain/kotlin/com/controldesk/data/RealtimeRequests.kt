package com.controldesk.data

import com.controldesk.protocol.WsClientMessage
import com.controldesk.protocol.WsMessageType
import com.controldesk.protocol.WsServerMessage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.TimeoutCancellationException
import kotlin.random.Random
import kotlinx.coroutines.withTimeout

/** Correlates replies and releases requests on every completion path. */
internal class RealtimeRequests {
    private val pending = MutableStateFlow<Map<String, CompletableDeferred<WsServerMessage>>>(emptyMap())

    suspend fun execute(
        message: WsClientMessage,
        timeoutMillis: Long,
        send: suspend (WsClientMessage) -> Unit,
    ): WsServerMessage {
        val deferred = CompletableDeferred<WsServerMessage>()
        val id = "${Random.nextLong()}-${Random.nextLong()}"
        pending.update { it + (id to deferred) }
        try {
            try {
                return withTimeout(timeoutMillis) {
                    send(message.copy(id = id))
                    deferred.await()
                }
            } catch (error: RemoteCommandRejectedException) {
                throw error
            } catch (error: TimeoutCancellationException) {
                throw CommandOutcomeUnknownException(error)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // A send failure does not prove that the peer did not receive the command.
                throw CommandOutcomeUnknownException(error)
            }
        } finally {
            pending.update { it - id }
            deferred.cancel()
        }
    }

    fun complete(message: WsServerMessage) {
        val id = message.id ?: return
        val deferred = pending.getAndUpdate { it - id }[id] ?: return
        if (!message.ok || message.type == WsMessageType.ERROR) {
            deferred.completeExceptionally(RemoteCommandRejectedException(message.message ?: "Remote command failed"))
        } else {
            deferred.complete(message)
        }
    }

    fun failPending(message: String) {
        val requests = pending.getAndUpdate { emptyMap() }.values
        requests.forEach { it.completeExceptionally(IllegalStateException(message)) }
    }

    internal fun pendingCount(): Int = pending.value.size
}

class CommandOutcomeUnknownException(cause: Throwable? = null) :
    Exception("Não foi possível confirmar se o agente executou a ação. Confira o estado no computador antes de tentar novamente.", cause)

class RemoteCommandRejectedException(message: String) : Exception(message)
