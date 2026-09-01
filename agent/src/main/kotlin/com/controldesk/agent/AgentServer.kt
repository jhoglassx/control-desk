package com.controldesk.agent

import com.controldesk.logging.ControlDeskLog
import com.controldesk.protocol.ActionAck
import com.controldesk.protocol.AgentJson
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
import io.ktor.http.ContentType
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
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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
                json(AgentJson)
            }
            install(StatusPages) {
                exception<Throwable> { call, cause ->
                    ControlDeskLog.agent.e(cause) { "request failed: ${cause.message}" }
                    call.respondText(
                        buildJsonObject {
                            put("ok", false)
                            put("message", cause.message ?: "error")
                        }.toString(),
                        ContentType.Application.Json,
                        HttpStatusCode.InternalServerError,
                    )
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
                    call.respondDto(
                        HealthResponse(
                            ok = true,
                            version = "1.1.0",
                            os = executor.osName,
                            windowListerVersion = 20,
                            accessibilityEnabled = macAccessibilityEnabled(),
                        ),
                    )
                }
                post("/pair") {
                    val body = call.receive<PairRequest>()
                    val token = authStore.pair(body.pin)
                    if (token == null) {
                        call.respondDto(
                            PairResponse(ok = false, message = "Invalid PIN"),
                            HttpStatusCode.Unauthorized,
                        )
                    } else {
                        call.respondDto(PairResponse(ok = true, token = token))
                    }
                }
                get("/setup") {
                    if (!call.isLocalhost()) {
                        call.respondText(
                            """{"ok":false}""",
                            ContentType.Application.Json,
                            HttpStatusCode.Forbidden,
                        )
                        return@get
                    }
                    call.respondDto(
                        SetupResponse(
                            ok = true,
                            port = port,
                            pin = authStore.currentPin(),
                            backgroundService = true,
                            accessibilityEnabled = macAccessibilityEnabled(),
                        ),
                    )
                }
                post("/setup/new-pin") {
                    if (!call.isLocalhost()) {
                        call.respondText(
                            """{"ok":false}""",
                            ContentType.Application.Json,
                            HttpStatusCode.Forbidden,
                        )
                        return@post
                    }
                    val pin = authStore.activatePin()
                    call.respondDto(
                        SetupResponse(
                            ok = true,
                            port = port,
                            pin = pin,
                            backgroundService = true,
                            accessibilityEnabled = macAccessibilityEnabled(),
                        ),
                    )
                }
                authenticate("paired") {
                    get("/apps/running") {
                        val icons = call.request.queryParameters["icons"] == "1"
                        call.respondDto(RunningAppsResponse(apps = executor.listRunningApps(includeIcons = icons)))
                    }
                    post("/apps/launch") {
                        val body = call.receive<AppLaunchRequest>()
                        executor.launchApp(body)
                        call.respondDto(ActionAck())
                    }
                    post("/apps/close") {
                        val body = call.receive<AppCloseRequest>()
                        executor.closeApp(body)
                        call.respondDto(ActionAck())
                    }
                    post("/browser/open") {
                        val body = call.receive<BrowserOpenRequest>()
                        executor.openBrowser(body.url, body.browser)
                        call.respondDto(ActionAck())
                    }
                    post("/browser/close") {
                        val body = runCatching { call.receive<BrowserCloseRequest>() }.getOrDefault(BrowserCloseRequest())
                        executor.closeBrowser(body.browser)
                        call.respondDto(ActionAck())
                    }
                    post("/browser/tabs/switch") {
                        val body = call.receive<BrowserTabSwitchRequest>()
                        executor.switchBrowserTab(body)
                        call.respondDto(ActionAck())
                    }
                    get("/system/audio") {
                        call.respondDto(executor.audio())
                    }
                    post("/system/volume") {
                        val body = call.receive<VolumeRequest>()
                        executor.setVolume(body)
                        call.respondDto(ActionAck())
                    }
                    post("/system/mic") {
                        val body = call.receive<MicRequest>()
                        executor.setMic(body)
                        call.respondDto(ActionAck())
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
        return remote == "127.0.0.1" || remote == "localhost" || remote == "::1" || remote == "[::1]"
    }

    private fun macAccessibilityEnabled(): Boolean? {
        if (!executor.osName.contains("mac", ignoreCase = true)) return null
        return runCatching { MacAccessibility.isTrusted() }.getOrDefault(false)
    }
}

private suspend inline fun <reified T> ApplicationCall.respondDto(
    value: T,
    status: HttpStatusCode = HttpStatusCode.OK,
) {
    respondText(AgentJson.encodeToString(value), ContentType.Application.Json, status)
}
