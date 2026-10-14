package com.controldesk.protocol

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val ok: Boolean = true,
    val version: String = "1.1.0",
    val os: String? = null,
    val windowListerVersion: Int? = null,
    val accessibilityEnabled: Boolean? = null,
)

@Serializable
data class PairRequest(
    val pin: String,
)

@Serializable
data class PairResponse(
    val ok: Boolean = true,
    val token: String? = null,
    val message: String? = null,
)

@Serializable
data class AppLaunchRequest(
    val target: String,
    val targetType: String = "appName",
    val windowTitle: String? = null,
    val windowIndex: Int? = null,
    val windowNumber: Int? = null,
    val bundlePath: String? = null,
    val projectPath: String? = null,
    val pid: Int? = null,
    val axWindowIndex: Int? = null,
)

@Serializable
data class InstalledAppInfo(
    val name: String,
    val bundlePath: String,
    val iconBase64: String? = null,
)

@Serializable
data class InstalledAppsResponse(val apps: List<InstalledAppInfo> = emptyList())

@Serializable
data class BrowseFilesRequest(val path: String? = null)

@Serializable
data class BrowseFileEntry(val name: String, val path: String, val isDirectory: Boolean)

@Serializable
data class BrowseFilesResponse(
    val path: String,
    val parentPath: String? = null,
    val entries: List<BrowseFileEntry> = emptyList(),
)

@Serializable
data class AppCloseRequest(
    val target: String,
    val targetType: String = "appName",
    val pid: Int? = null,
    val windowTitle: String? = null,
    val axWindowIndex: Int? = null,
    val windowCount: Int? = null,
)

@Serializable
data class BrowserOpenRequest(
    val url: String,
    val browser: String? = null,
)

@Serializable
data class BrowserCloseRequest(
    val browser: String? = null,
)

@Serializable
data class BrowserTabSwitchRequest(
    val browser: String = "chrome",
    val action: String = "select",
    val value: String? = null,
    val windowIndex: Int? = null,
    val focusFirst: Boolean = true,
)

@Serializable
data class VolumeRequest(
    val level: Int? = null,
    val delta: Int? = null,
    val mute: Boolean? = null,
)

@Serializable
data class MicRequest(
    val mute: Boolean? = null,
    val toggle: Boolean = false,
    val inputDelta: Int? = null,
    val level: Int? = null,
)

@Serializable
data class SetupResponse(
    val ok: Boolean = true,
    val port: Int,
    val pin: String? = null,
    val backgroundService: Boolean = true,
    val accessibilityEnabled: Boolean? = null,
    val agentVersion: String = "unknown",
)

@Serializable
data class ActionAck(
    val ok: Boolean = true,
    val apps: List<RunningAppInfo> = emptyList(),
)

@Serializable
data class AudioResponse(
    val volumeLevel: Int = 50,
    val volumeMuted: Boolean = false,
    val micMuted: Boolean = false,
    val micLevel: Int = 75,
)

@Serializable
data class RunningAppInfo(
    val name: String,
    val appName: String = name,
    val pid: Int? = null,
    val windowTitle: String? = null,
    val windowIndex: Int? = null,
    val axWindowIndex: Int? = null,
    val windowNumber: Int? = null,
    val windowCount: Int? = null,
    val iconBase64: String? = null,
    val isActive: Boolean = false,
    val isVisible: Boolean = true,
    val browserWindowIndex: Int? = null,
    val isBrowserTab: Boolean = false,
    val monitorIndex: Int = 0,
    val isDockPinned: Boolean = false,
    val dockIndex: Int = -1,
)

@Serializable
data class RunningAppsResponse(
    val apps: List<RunningAppInfo> = emptyList(),
)
