package com.controldesk.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class OsType {
    MACOS,
    WINDOWS,
    LINUX,
}

fun OsType.osShortLabel(): String = when (this) {
    OsType.MACOS -> "MAC"
    OsType.WINDOWS -> "WIN"
    OsType.LINUX -> "LIN"
}

@Serializable
enum class ActionType {
    REMOTE_LAUNCH,
    REMOTE_BROWSER_OPEN_SITE,
    REMOTE_BROWSER_CLOSE_APP,
    REMOTE_BROWSER_TAB,
    REMOTE_VOLUME,
    REMOTE_MIC_MUTE,
    OPEN_URL,
}
