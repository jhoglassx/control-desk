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
            var message = readNextMessage(session)
            while (message.type != WsMessageType.APPS) {
                message = readNextMessage(session)
            }
            message
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

    @Test
    fun executorFailureReturnsCorrelatedErrorAndKeepsSessionOpen() = runBlocking {
        server.stop()
        val executor = object : PlatformExecutor by LoggingPlatformExecutor() {
            override fun launchApp(request: AppLaunchRequest) {
                error("Launch denied")
            }
        }
        server = AgentServer(18741, auth, executor, bind = "127.0.0.1")
        server.start()
        val token = pairToken()
        val session = client.webSocketSession("ws://127.0.0.1:18741/ws?token=$token")
        try {
            session.send(Frame.Text(AgentJson.encodeToString(
                WsClientMessage(type = WsMessageType.LAUNCH, id = "denied",
                    launch = AppLaunchRequest(target = "Safari")),
            )))
            val error = withTimeout(2_000) {
                var message = readNextMessage(session)
                while (message.id != "denied") message = readNextMessage(session)
                message
            }
            assertEquals(WsMessageType.ERROR, error.type)
            assertEquals(false, error.ok)
            assertEquals("Launch denied", error.message)
            session.send(Frame.Text(AgentJson.encodeToString(
                WsClientMessage(type = WsMessageType.PING, id = "still-open"),
            )))
            val pong = withTimeout(2_000) {
                var message = readNextMessage(session)
                while (message.id != "still-open") message = readNextMessage(session)
                message
            }
            assertEquals(WsMessageType.PONG, pong.type)
        } finally {
            session.close()
        }
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
