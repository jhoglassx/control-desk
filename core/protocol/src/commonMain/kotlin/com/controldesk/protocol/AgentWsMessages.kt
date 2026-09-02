package com.controldesk.protocol

import kotlinx.serialization.Serializable

@Serializable
data class WsClientMessage(
    val type: String,
    val id: String? = null,
    val launch: AppLaunchRequest? = null,
    val close: AppCloseRequest? = null,
    val tab: BrowserTabSwitchRequest? = null,
    val volume: VolumeRequest? = null,
    val mic: MicRequest? = null,
    val browser: BrowserOpenRequest? = null,
    val browserClose: BrowserCloseRequest? = null,
    val requestIcons: Boolean = false,
)

@Serializable
data class WsServerMessage(
    val type: String,
    val id: String? = null,
    val apps: List<RunningAppInfo> = emptyList(),
    val audio: AudioResponse? = null,
    val ok: Boolean = true,
    val message: String? = null,
    val accessibilityEnabled: Boolean? = null,
)

object WsMessageType {
    const val PING = "ping"
    const val PONG = "pong"
    const val LAUNCH = "launch"
    const val CLOSE = "close"
    const val SWITCH_TAB = "switch_tab"
    const val VOLUME = "volume"
    const val MIC = "mic"
    const val BROWSER_OPEN = "browser_open"
    const val BROWSER_CLOSE = "browser_close"
    const val REQUEST_APPS = "request_apps"
    const val APPS = "apps"
    const val AUDIO = "audio"
    const val ACK = "ack"
    const val ERROR = "error"
    const val HEALTH = "health"
}

fun agentWsUrl(host: String, port: Int, token: String): String =
    "ws://$host:$port/ws?token=$token"
