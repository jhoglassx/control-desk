package com.controldesk.agent

import com.controldesk.logging.ControlDeskLog
import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.AudioResponse
import com.controldesk.protocol.BrowserCloseRequest
import com.controldesk.protocol.BrowserOpenRequest
import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.RunningAppInfo
import com.controldesk.protocol.VolumeRequest
import com.controldesk.protocol.WsClientMessage
import com.controldesk.protocol.WsMessageType
import com.controldesk.protocol.WsServerMessage
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class AgentWsHub(
    private val authStore: AgentAuthStore,
    private val executor: PlatformExecutor,
) {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessions = CopyOnWriteArrayList<WebSocketSession>()
    private val lastAppsSignature = AtomicReference("")
    private val lastAudioSignature = AtomicReference("")
    private var watcherJob: Job? = null
    private val audioTick = AtomicInteger(0)

    private var cacheListenerRegistered = false

    fun start() {
        if (watcherJob?.isActive == true) return
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        if (!cacheListenerRegistered) {
            MacWindowLister.addCacheListener { pushAppsIfChanged() }
            cacheListenerRegistered = true
        }
        watcherJob = scope.launch {
            while (isActive) {
                pushAppsIfChanged()
                if (audioTick.incrementAndGet() % 2 == 0) {
                    pushAudioIfChanged()
                }
                delay(WATCH_MS)
            }
        }
    }

    fun stop() {
        watcherJob?.cancel()
        watcherJob = null
        scope.cancel()
    }

    suspend fun handleSession(session: WebSocketSession, token: String) {
        if (!authStore.isValidToken(token)) {
            session.sendJson(WsServerMessage(type = WsMessageType.ERROR, ok = false, message = "Unauthorized"))
            session.close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Unauthorized"))
            return
        }
        sessions.add(session)
        try {
            sendSnapshot(session)
            for (frame in session.incoming) {
                if (frame !is Frame.Text) continue
                val message = runCatching {
                    AgentJson.decodeFromString<WsClientMessage>(frame.readText())
                }.getOrNull()
                if (message == null) {
                    session.sendJson(WsServerMessage(type = WsMessageType.ERROR, ok = false, message = "Invalid message"))
                } else {
                    handleMessage(session, message)
                }
            }
        } finally {
            sessions.remove(session)
        }
    }

    private suspend fun handleMessage(session: WebSocketSession, message: WsClientMessage) {
        when (message.type) {
            WsMessageType.PING -> session.sendJson(WsServerMessage(type = WsMessageType.PONG, id = message.id))
            WsMessageType.REQUEST_APPS -> {
                val apps = executor.listRunningApps(includeIcons = message.requestIcons)
                session.sendJson(WsServerMessage(type = WsMessageType.APPS, id = message.id, apps = apps))
            }
            WsMessageType.LAUNCH -> {
                val request = message.launch ?: return sendError(session, message.id, "Missing launch payload")
                executor.launchApp(request)
                respondWithApps(session, message.id)
            }
            WsMessageType.CLOSE -> {
                val request = message.close ?: return sendError(session, message.id, "Missing close payload")
                executor.closeApp(request)
                respondWithApps(session, message.id)
            }
            WsMessageType.SWITCH_TAB -> {
                val request = message.tab ?: return sendError(session, message.id, "Missing tab payload")
                executor.switchBrowserTab(request)
                respondWithApps(session, message.id)
            }
            WsMessageType.VOLUME -> {
                val request = message.volume ?: return sendError(session, message.id, "Missing volume payload")
                executor.setVolume(request)
                session.sendJson(WsServerMessage(type = WsMessageType.ACK, id = message.id))
                delay(80)
                pushAudioIfChanged(force = true)
            }
            WsMessageType.MIC -> {
                val request = message.mic ?: return sendError(session, message.id, "Missing mic payload")
                executor.setMic(request)
                session.sendJson(WsServerMessage(type = WsMessageType.ACK, id = message.id))
                delay(80)
                pushAudioIfChanged(force = true)
            }
            WsMessageType.BROWSER_OPEN -> {
                val request = message.browser ?: return sendError(session, message.id, "Missing browser payload")
                executor.openBrowser(request.url, request.browser)
                session.sendJson(WsServerMessage(type = WsMessageType.ACK, id = message.id))
            }
            WsMessageType.BROWSER_CLOSE -> {
                val request = message.browserClose ?: BrowserCloseRequest()
                executor.closeBrowser(request.browser)
                session.sendJson(WsServerMessage(type = WsMessageType.ACK, id = message.id))
            }
            else -> sendError(session, message.id, "Unknown type: ${message.type}")
        }
    }

    private suspend fun respondWithApps(session: WebSocketSession, id: String?) {
        val apps = executor.listRunningApps(includeIcons = false)
        lastAppsSignature.set(appsSignature(apps))
        session.sendJson(WsServerMessage(type = WsMessageType.ACK, id = id, apps = apps))
        broadcastApps(apps)
    }

    private suspend fun sendSnapshot(session: WebSocketSession) {
        val apps = executor.listRunningApps(includeIcons = false)
        lastAppsSignature.set(appsSignature(apps))
        session.sendJson(
            WsServerMessage(
                type = WsMessageType.APPS,
                apps = apps,
                accessibilityEnabled = macAccessibilityEnabled(),
            ),
        )
        val audio = executor.audio()
        lastAudioSignature.set(audioSignature(audio))
        session.sendJson(WsServerMessage(type = WsMessageType.AUDIO, audio = audio))
    }

    private fun pushAppsIfChanged() {
        if (sessions.isEmpty()) return
        val apps = executor.listRunningApps(includeIcons = false)
        val signature = appsSignature(apps)
        if (signature == lastAppsSignature.get()) return
        lastAppsSignature.set(signature)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { broadcastApps(apps) }
    }

    private fun pushAudioIfChanged(force: Boolean = false) {
        if (sessions.isEmpty()) return
        val audio = executor.audio()
        val signature = audioSignature(audio)
        if (!force && signature == lastAudioSignature.get()) return
        lastAudioSignature.set(signature)
        scope.launch(start = CoroutineStart.UNDISPATCHED) { broadcastAudio(audio) }
    }

    private suspend fun broadcastApps(apps: List<RunningAppInfo>) {
        val payload = WsServerMessage(type = WsMessageType.APPS, apps = apps)
        sessions.forEach { session ->
            runCatching { session.sendJson(payload) }
                .onFailure { ControlDeskLog.agent.w { "[ws] broadcast apps failed: ${it.message}" } }
        }
    }

    private suspend fun broadcastAudio(audio: AudioResponse) {
        val payload = WsServerMessage(type = WsMessageType.AUDIO, audio = audio)
        sessions.forEach { session ->
            runCatching { session.sendJson(payload) }
                .onFailure { ControlDeskLog.agent.w { "[ws] broadcast audio failed: ${it.message}" } }
        }
    }

    private suspend fun sendError(session: WebSocketSession, id: String?, message: String) {
        session.sendJson(WsServerMessage(type = WsMessageType.ERROR, id = id, ok = false, message = message))
    }

    private fun appsSignature(apps: List<RunningAppInfo>): String = apps.joinToString("|") { app ->
        buildString {
            append(app.appName)
            append(':')
            append(app.windowNumber ?: -1)
            append(':')
            append(app.isActive)
            append(':')
            append(app.isVisible)
            append(':')
            append(app.monitorIndex)
            append(':')
            append(app.windowTitle.orEmpty())
        }
    }

    private fun audioSignature(audio: AudioResponse): String =
        "${audio.volumeLevel}:${audio.volumeMuted}:${audio.micLevel}:${audio.micMuted}"

    private fun macAccessibilityEnabled(): Boolean? {
        if (!executor.osName.contains("mac", ignoreCase = true)) return null
        return runCatching { MacAccessibility.isTrusted() }.getOrDefault(false)
    }

    private suspend fun WebSocketSession.sendJson(message: WsServerMessage) {
        send(Frame.Text(AgentJson.encodeToString(message)))
    }

    private companion object {
        const val WATCH_MS = 50L
    }
}
