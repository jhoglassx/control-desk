package com.controldesk.domain.model

/** Name-based keys for legacy preference migration and human-readable application names. */
object ApplicationIdentity {
    fun applicationKey(bundlePath: String?, processName: String): String = bundlePath
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.removeSuffix(".app")
        ?.takeIf(String::isNotBlank)
        ?: processName

    fun dockShortcutId(bundlePath: String?, processName: String): String =
        "app-${applicationKey(bundlePath, processName)}-dock"
}
