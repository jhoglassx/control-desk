package com.controldesk.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class GridButton(
    val id: String,
    val label: String,
    val actionType: ActionType,
    val payload: Map<String, String> = emptyMap(),
    val row: Int = 0,
    val col: Int = 0,
    val iconBase64: String? = null,
    val isActive: Boolean = false,
    val isVisible: Boolean = true,
    val monitorIndex: Int = 0,
    val isDockPinned: Boolean = false,
    val isFavoriteShortcut: Boolean = false,
)

@Serializable
data class PairedHost(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val osType: OsType,
    val sortOrder: Int,
    val token: String,
    val buttons: List<GridButton> = emptyList(),
    val pinnedApps: List<PinnedAppShortcut> = emptyList(),
    val appOrder: List<String> = emptyList(),
)

@Serializable
data class PinnedAppShortcut(
    val id: String,
    val name: String,
    val bundlePath: String,
    val iconBase64: String? = null,
    val projectPath: String? = null,
    val order: Int = 0,
)

data class MacFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
)

data class MacFileListing(
    val path: String,
    val parentPath: String? = null,
    val entries: List<MacFileEntry> = emptyList(),
)

data class AudioState(
    val volumeLevel: Int = 50,
    val volumeMuted: Boolean = false,
    val micMuted: Boolean = false,
    val micLevel: Int = 75,
)

data class RunningApp(
    val name: String,
    val appName: String,
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
    val bundlePath: String? = null,
)
