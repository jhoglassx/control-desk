package com.controldesk.agent

import com.controldesk.protocol.ActionAck
import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.BrowserCloseRequest
import com.controldesk.protocol.BrowserOpenRequest
import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.HealthResponse
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.PairRequest
import com.controldesk.protocol.PairResponse
import com.controldesk.protocol.RunningAppsResponse
import com.controldesk.protocol.SetupResponse
import com.controldesk.protocol.VolumeRequest
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.UserIdPrincipal
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.bearer
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json

class AgentServer(
    val port: Int,
    private val authStore: AgentAuthStore,
    private val executor: PlatformExecutor,
    private val bind: String = System.getenv("CONTROL_DESK_BIND") ?: "0.0.0.0",
) {
    private var engine: EmbeddedServer<*, *>? = null

    fun start() {
        engine = embeddedServer(CIO, port = port, host = bind) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    call.respond(HttpStatusCode.InternalServerError, mapOf("ok" to "false", "message" to (cause.message ?: "error")))
                }
            }
            install(Authentication) {
                bearer("paired") {
                    authenticate { credential ->
                        if (authStore.isValidToken(credential.token)) UserIdPrincipal("device") else null
                    }
                }
            }
            routing {
                get("/health") {
                    call.respond(
                        HealthResponse(
                            ok = true,
                            version = "1.1.0",
                            os = executor.osName,
                            windowListerVersion = 19,
                        ),
                    )
                }
                post("/pair") {
                    val body = call.receive<PairRequest>()
                    val token = authStore.pair(body.pin)
                    if (token == null) {
                        call.respond(HttpStatusCode.Unauthorized, PairResponse(ok = false, message = "Invalid PIN"))
                    } else {
                        call.respond(PairResponse(ok = true, token = token))
                    }
                }
                get("/setup") {
                    if (!call.isLocalhost()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("ok" to "false"))
                        return@get
                    }
                    call.respond(
                        SetupResponse(
                            ok = true,
                            port = port,
                            pin = authStore.currentPin(),
                            backgroundService = true,
                        ),
                    )
                }
                post("/setup/new-pin") {
                    if (!call.isLocalhost()) {
                        call.respond(HttpStatusCode.Forbidden, mapOf("ok" to "false"))
                        return@post
                    }
                    val pin = authStore.activatePin()
                    call.respond(SetupResponse(ok = true, port = port, pin = pin, backgroundService = true))
                }
                authenticate("paired") {
                    get("/apps/running") {
                        val icons = call.request.queryParameters["icons"] == "1"
                        call.respond(RunningAppsResponse(apps = executor.listRunningApps(includeIcons = icons)))
                    }
                    post("/apps/launch") {
                        val body = call.receive<AppLaunchRequest>()
                        executor.launchApp(body)
                        call.respond(ActionAck())
                    }
                    post("/apps/close") {
                        val body = call.receive<AppCloseRequest>()
                        executor.closeApp(body)
                        call.respond(ActionAck())
                    }
                    post("/browser/open") {
                        val body = call.receive<BrowserOpenRequest>()
                        executor.openBrowser(body.url, body.browser)
                        call.respond(ActionAck())
                    }
                    post("/browser/close") {
                        val body = runCatching { call.receive<BrowserCloseRequest>() }.getOrDefault(BrowserCloseRequest())
                        executor.closeBrowser(body.browser)
                        call.respond(ActionAck())
                    }
                    post("/browser/tabs/switch") {
                        val body = call.receive<BrowserTabSwitchRequest>()
                        executor.switchBrowserTab(body)
                        call.respond(ActionAck())
                    }
                    get("/system/audio") {
                        call.respond(executor.audio())
                    }
                    post("/system/volume") {
                        val body = call.receive<VolumeRequest>()
                        executor.setVolume(body)
                        call.respond(ActionAck())
                    }
                    post("/system/mic") {
                        val body = call.receive<MicRequest>()
                        executor.setMic(body)
                        call.respond(ActionAck())
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        engine?.stop(gracePeriodMillis = 200, timeoutMillis = 500)
        engine = null
    }

    private fun ApplicationCall.isLocalhost(): Boolean {
        val remote = request.local.remoteHost
        return remote == "127.0.0.1" || remote == "localhost" || remote == "::1" || remote == "[::1]" || remote == "https://example.net/id/garnet"
    }
}
