package com.controldesk.agent

import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.WsClientMessage
import com.controldesk.protocol.WsMessageType
import com.controldesk.protocol.WsServerMessage
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentWebSocketTest : AgentServerTest() {
    @Test
    fun websocketReturnsAppsSnapshotAndAck() = runBlocking {
        val token = pairToken()
        val session = client.webSocketSession("ws://127.0.0.1:18741/ws?token=$token")
        val snapshot = withTimeout(2_000) {
            readNextMessage(session)
        }
        assertEquals(WsMessageType.APPS, snapshot.type)
        assertTrue(snapshot.apps.isNotEmpty())

        val launchId = "launch-1"
        session.send(
            Frame.Text(
                AgentJson.encodeToString(
                    WsClientMessage(
                        type = WsMessageType.LAUNCH,
                        id = launchId,
                        launch = AppLaunchRequest(target = "Safari"),
                    ),
                ),
            ),
        )
        val ack = withTimeout(2_000) {
            var message = readNextMessage(session)
            while (message.id != launchId) {
                message = readNextMessage(session)
            }
            message
        }
        assertEquals(WsMessageType.ACK, ack.type)
        assertTrue(ack.apps.isNotEmpty())
        session.close()
    }

    private suspend fun readNextMessage(session: io.ktor.websocket.WebSocketSession): WsServerMessage {
        while (true) {
            val frame = session.incoming.receive()
            if (frame is Frame.Text) {
                return AgentJson.decodeFromString(frame.readText())
            }
        }
    }

    private suspend fun pairToken(): String {
        val pin = auth.currentPin() ?: auth.activatePin()
        val pair = client.post("http://127.0.0.1:18741/pair") {
            contentType(ContentType.Application.Json)
            setBody("""{"pin":"$pin"}""")
        }.bodyAsText()
        return Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(pair)!!.groupValues[1]
    }
}
