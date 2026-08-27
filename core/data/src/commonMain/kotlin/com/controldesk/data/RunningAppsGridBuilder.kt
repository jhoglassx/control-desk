package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.RunningApp

object RunningAppsGridBuilder {
    fun build(apps: List<RunningApp>, iconCache: Map<String, String> = emptyMap()): List<GridButton> {
        val sorted = apps.sortedWith(
            compareBy<RunningApp> { if (it.dockIndex >= 0) it.dockIndex else 1_000 }
                .thenBy { it.appName.lowercase() }
                .thenBy { it.windowIndex ?: it.windowNumber ?: 0 }
                .thenBy { it.label().lowercase() },
        )
        return sorted.mapIndexed { index, app ->
            val id = stableId(app)
            val payload = if (app.isBrowserTab) {
                buildMap {
                    put("target", app.appName.ifBlank { "Google Chrome" })
                    put("targetType", "appName")
                    put("browser", "chrome")
                    put("action", "select")
                    app.windowTitle?.let { put("value", it) }
                    app.browserWindowIndex?.let { put("windowIndex", it.toString()) }
                    app.windowIndex?.let { put("windowIndex", it.toString()) }
                }
            } else {
                buildMap {
                    put("target", app.appName.ifBlank { app.name })
                    put("targetType", "appName")
                    app.windowTitle?.let { put("windowTitle", it) }
                    app.windowNumber?.let { put("windowNumber", it.toString()) }
                    app.windowIndex?.let { put("windowIndex", it.toString()) }
                }
            }
            GridButton(
                id = id,
                label = app.label(),
                actionType = if (app.isBrowserTab) ActionType.REMOTE_BROWSER_TAB else ActionType.REMOTE_LAUNCH,
                payload = payload,
                row = index / 3,
                col = index % 3,
                iconBase64 = app.iconBase64?.takeIf { it.isNotBlank() } ?: iconCache[id],
                isActive = app.isActive,
                isVisible = app.isVisible,
                monitorIndex = app.monitorIndex,
                isDockPinned = app.isDockPinned,
            )
        }
    }

    fun merge(
        current: List<GridButton>,
        incoming: List<GridButton>,
        preferMerge: Boolean,
    ): List<GridButton> {
        if (preferMerge && incoming.isEmpty()) return current
        if (!preferMerge) return incoming
        val currentById = current.associateBy { it.id }
        return incoming.map { next ->
            val previous = currentById[next.id]
            if (previous != null && next.iconBase64.isNullOrBlank() && !previous.iconBase64.isNullOrBlank()) {
                next.copy(iconBase64 = previous.iconBase64)
            } else {
                next
            }
        }
    }

    private fun RunningApp.label(): String {
        val title = windowTitle?.takeIf { it.isNotBlank() }
        val base = name.ifBlank { appName }
        if (title == null || title == base || title == appName) return base
        if (base.contains(title)) return base
        return "$base · $title"
    }

    private fun RunningApp.isDockOnly(): Boolean = isDockPinned && !isActive && !isVisible

    private fun stableId(app: RunningApp): String {
        if (app.isDockOnly()) return "app-${app.appName}-dock"
        val key = app.windowNumber?.let { "wn-$it" }
            ?: app.axWindowIndex?.let { "ax-$it" }
            ?: app.windowIndex?.let { "wi-$it" }
            ?: "title-${(app.windowTitle ?: app.name).hashCode()}"
        val prefix = if (app.isBrowserTab) "tab" else "app"
        return "$prefix-${app.appName}-$key"
    }
}
