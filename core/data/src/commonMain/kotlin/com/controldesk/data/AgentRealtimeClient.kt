package com.controldesk.data

import com.controldesk.domain.model.AgentRealtimeEvent
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RealtimeConnectionState
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.repository.AgentRealtime
import com.controldesk.logging.ControlDeskLog
import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.VolumeRequest
import com.controldesk.protocol.WsClientMessage
import com.controldesk.protocol.WsMessageType
import com.controldesk.protocol.WsServerMessage
import com.controldesk.protocol.agentWsUrl
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import io.ktor.websocket.send
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlin.random.Random

class AgentRealtimeClient(
    private val httpClient: HttpClient,
) : AgentRealtime {
    private val _connectionState = MutableStateFlow(RealtimeConnectionState.Disconnected)
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AgentRealtimeEvent>(extraBufferCapacity = 64)
    override val events = _events.asSharedFlow()

    private var connectJob: Job? = null
    private var session: WebSocketSession? = null
    private val pending = mutableMapOf<String, CompletableDeferred<WsServerMessage>>()

    override fun connect(host: PairedHost, scope: CoroutineScope) {
        if (host.token.isBlank()) return
        disconnect()
        connectJob = scope.launch {
            while (isActive) {
                try {
                    _connectionState.value = RealtimeConnectionState.Connecting
                    val ws = httpClient.webSocketSession(agentWsUrl(host.host, host.port, host.token))
                    session = ws
                    _connectionState.value = RealtimeConnectionState.Connected
                    for (frame in ws.incoming) {
                        if (frame !is Frame.Text) continue
                        handleServerMessage(frame.readText())
                    }
                } catch (error: Throwable) {
                    ControlDeskLog.gateway.w { "websocket disconnected: ${error.message}" }
                } finally {
                    session = null
                    failPending(errorMessage = "disconnected")
                    _connectionState.value = RealtimeConnectionState.Disconnected
                }
                delay(RECONNECT_MS)
            }
        }
    }

    override fun disconnect() {
        connectJob?.cancel()
        connectJob = null
        session = null
        failPending(errorMessage = "disconnected")
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    override suspend fun launchApp(host: PairedHost, button: GridButton): List<RunningApp> =
        mapRunningApps(
            request(
                WsClientMessage(
                    type = WsMessageType.LAUNCH,
                    launch = AppLaunchRequest(
                        target = button.payload.getValue("target"),
                        targetType = button.payload["targetType"] ?: "appName",
                        windowTitle = button.payload["windowTitle"],
                        windowIndex = button.payload["windowIndex"]?.toIntOrNull(),
                        windowNumber = button.payload["windowNumber"]?.toIntOrNull(),
                    ),
                ),
            ).apps,
        )

    override suspend fun closeApp(host: PairedHost, button: GridButton): List<RunningApp> =
        mapRunningApps(
            request(
                WsClientMessage(
                    type = WsMessageType.CLOSE,
                    close = AppCloseRequest(
                        target = button.payload.getValue("target"),
                        targetType = button.payload["targetType"] ?: "appName",
                    ),
                ),
            ).apps,
        )

    override suspend fun switchBrowserTab(host: PairedHost, button: GridButton): List<RunningApp> =
        mapRunningApps(
            request(
                WsClientMessage(
                    type = WsMessageType.SWITCH_TAB,
                    tab = BrowserTabSwitchRequest(
                        browser = button.payload["browser"] ?: "chrome",
                        action = button.payload["action"] ?: "select",
                        value = button.payload["value"],
                        windowIndex = button.payload["windowIndex"]?.toIntOrNull(),
                    ),
                ),
            ).apps,
        )

    override suspend fun setVolume(host: PairedHost, level: Int?, delta: Int?, mute: Boolean?) {
        request(WsClientMessage(type = WsMessageType.VOLUME, volume = VolumeRequest(level = level, delta = delta, mute = mute)))
    }

    override suspend fun setMic(host: PairedHost, mute: Boolean?, toggle: Boolean, level: Int?) {
        request(WsClientMessage(type = WsMessageType.MIC, mic = MicRequest(mute = mute, toggle = toggle, level = level)))
    }

    private suspend fun request(message: WsClientMessage): WsServerMessage {
        val ws = session ?: throw IllegalStateException("WebSocket not connected")
        val id = "${Random.nextLong()}-${pending.size}"
        val deferred = CompletableDeferred<WsServerMessage>()
        pending[id] = deferred
        ws.send(Frame.Text(AgentJson.encodeToString(message.copy(id = id))))
        return withTimeout(REQUEST_TIMEOUT_MS) { deferred.await() }
    }

    private suspend fun handleServerMessage(raw: String) {
        val message = AgentJson.decodeFromString<WsServerMessage>(raw)
        message.id?.let { id ->
            pending.remove(id)?.complete(message)
        }
        when (message.type) {
            WsMessageType.APPS -> {
                _events.emit(AgentRealtimeEvent.Apps(mapRunningApps(message.apps)))
                message.accessibilityEnabled?.let {
                    _events.emit(AgentRealtimeEvent.Accessibility(it))
                }
            }
            WsMessageType.AUDIO -> message.audio?.let { audio ->
                _events.emit(
                    AgentRealtimeEvent.Audio(
                        AudioState(
                            volumeLevel = audio.volumeLevel.coerceIn(0, 100),
                            volumeMuted = audio.volumeMuted,
                            micMuted = audio.micMuted,
                            micLevel = audio.micLevel.coerceIn(0, 100),
                        ),
                    ),
                )
            }
            WsMessageType.HEALTH -> _events.emit(AgentRealtimeEvent.Accessibility(message.accessibilityEnabled))
            WsMessageType.ACK -> {
                if (message.apps.isNotEmpty()) {
                    _events.emit(AgentRealtimeEvent.Apps(mapRunningApps(message.apps)))
                }
            }
            WsMessageType.ERROR -> ControlDeskLog.gateway.w { "websocket error: ${message.message}" }
        }
    }

    private fun failPending(errorMessage: String) {
        val error = IllegalStateException(errorMessage)
        pending.values.forEach { it.completeExceptionally(error) }
        pending.clear()
    }

    private fun mapRunningApps(apps: List<com.controldesk.protocol.RunningAppInfo>): List<RunningApp> =
        apps.map { info ->
            RunningApp(
                name = info.name,
                appName = info.appName,
                windowTitle = info.windowTitle,
                windowIndex = info.windowIndex,
                axWindowIndex = info.axWindowIndex,
                windowNumber = info.windowNumber,
                iconBase64 = info.iconBase64,
                isActive = info.isActive,
                isVisible = info.isVisible,
                browserWindowIndex = info.browserWindowIndex,
                isBrowserTab = info.isBrowserTab,
                monitorIndex = info.monitorIndex,
                isDockPinned = info.isDockPinned,
                dockIndex = info.dockIndex,
            )
        }

    private companion object {
        const val RECONNECT_MS = 500L
        const val REQUEST_TIMEOUT_MS = 2_500L
    }
}
