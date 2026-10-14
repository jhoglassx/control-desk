package com.controldesk.agent

import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.BrowseFilesRequest
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

open class AgentServerTest {
    protected lateinit var tmp: File
    protected lateinit var server: AgentServer
    protected lateinit var auth: AgentAuthStore
    protected val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        install(WebSockets)
    }

    @BeforeTest
    fun setup() {
        tmp = kotlin.io.path.createTempDirectory("cd-agent-test").toFile()
        System.setProperty("user.home", tmp.absolutePath)
        auth = AgentAuthStore()
        server = AgentServer(port = 18741, authStore = auth, executor = LoggingPlatformExecutor(), bind = "127.0.0.1")
        server.start()
        Thread.sleep(150)
    }

    @AfterTest
    fun tearDown() {
        server.stop()
        runCatching { client.close() }
        tmp.deleteRecursively()
    }

    @Test
    fun healthOk() {
        runBlocking {
            val text = client.get("http://127.0.0.1:18741/health").bodyAsText()
            assertTrue(text.contains("\"ok\":true"))
        }
    }

    @Test
    fun localSetupHealthReturnsCurrentPin() {
        runBlocking {
            val response = client.get("http://127.0.0.1:18741/setup/health")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertTrue(body.contains(auth.currentPin()!!))
            assertTrue(body.contains("\"agentVersion\":\"${AgentVersion.VALUE}\""))
        }
    }

    @Test
    fun pairIssuesToken() {
        runBlocking {
            val pin = auth.currentPin() ?: auth.activatePin()
            val response = client.post("http://127.0.0.1:18741/pair") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"$pin"}""")
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(response.bodyAsText().contains("token"))
        }
    }

    @Test
    fun protectedRouteRequiresToken() {
        runBlocking {
            val unauthorized = client.get("http://127.0.0.1:18741/apps/running")
            assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)
            val browseUnauthorized = client.post("http://127.0.0.1:18741/files/browse") {
                contentType(ContentType.Application.Json)
                setBody(BrowseFilesRequest())
            }
            assertEquals(HttpStatusCode.Unauthorized, browseUnauthorized.status)
            val pin = auth.currentPin() ?: auth.activatePin()
            val pair = client.post("http://127.0.0.1:18741/pair") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"$pin"}""")
            }.bodyAsText()
            val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(pair)?.groupValues?.get(1)!!
            val ok = client.get("http://127.0.0.1:18741/apps/running") { bearerAuth(token) }
            assertEquals(HttpStatusCode.OK, ok.status)
            val browseOk = client.post("http://127.0.0.1:18741/files/browse") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(BrowseFilesRequest())
            }
            assertEquals(HttpStatusCode.OK, browseOk.status)
            assertTrue(browseOk.bodyAsText().contains("entries"))
            val again = client.post("http://127.0.0.1:18741/pair") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"$pin"}""")
            }
            assertEquals(HttpStatusCode.OK, again.status)
            assertTrue(again.bodyAsText().contains("token"))
            client.post("http://127.0.0.1:18741/apps/launch") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(AppLaunchRequest(target = "Safari"))
            }
        }
    }

    @Test
    fun closeAppRemovesFromStubList() {
        runBlocking {
            val pin = auth.currentPin() ?: auth.activatePin()
            val pair = client.post("http://127.0.0.1:18741/pair") {
                contentType(ContentType.Application.Json)
                setBody("""{"pin":"$pin"}""")
            }.bodyAsText()
            val token = Regex("\"token\"\\s*:\\s*\"([^\"]+)\"").find(pair)?.groupValues?.get(1)!!
            val close = client.post("http://127.0.0.1:18741/apps/close") {
                bearerAuth(token)
                contentType(ContentType.Application.Json)
                setBody(AppCloseRequest(target = "Notes"))
            }
            assertEquals(HttpStatusCode.OK, close.status)
            val running = client.get("http://127.0.0.1:18741/apps/running") { bearerAuth(token) }.bodyAsText()
            assertTrue(running.contains("Safari"))
            assertTrue(!running.contains("Notes"))
        }
    }
}
