package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton

/** Gives each live window one owner: project shortcut, generic favorite, then default launcher. */
object ShortcutWindowBinder {
    data class Binding(val buttons: List<GridButton>, val windows: Map<String, List<GridButton>>)

    val runtimePayloadKeys = setOf(
        "stableWindowId", "windowTitle", "windowNumber", "windowIndex", "axWindowIndex", "pid", "windowCount",
        "browser", "action", "value",
    )

    fun bind(
        favorites: List<GridButton>,
        launchers: List<GridButton>,
        runtime: List<GridButton>,
        applicationWindows: Map<String, List<GridButton>>,
    ): Binding {
        val claimed = mutableSetOf<String>()
        val windows = linkedMapOf<String, List<GridButton>>()
        favorites.sortedBy { it.payload["projectPath"].isNullOrBlank() }.forEach { favorite ->
            val matches = runtime.filter { window ->
                window.id !in claimed && DockShortcutProjector.isSelectableWindow(window) &&
                    sameApplication(favorite, window) && matchesProject(favorite, window)
            }
            windows[favorite.id] = matches
            claimed += matches.map { it.id }
        }
        val genericFavorites = favorites.filter { it.payload["projectPath"].isNullOrBlank() }
        val visibleLaunchers = launchers.filterNot { launcher -> genericFavorites.any { sameApplication(it, launcher) } }
        visibleLaunchers.forEach { launcher ->
            val remaining = applicationWindows[launcher.id].orEmpty().filter { it.id !in claimed }
            windows[launcher.id] = remaining
            claimed += remaining.map { it.id }
        }
        val buttons = (favorites + visibleLaunchers).map { shortcut ->
            val instances = windows[shortcut.id].orEmpty()
            val selected = instances.firstOrNull { it.isActive } ?: instances.firstOrNull()
            shortcut.copy(
                actionType = if (shortcut.isFavoriteShortcut || selected == null) ActionType.REMOTE_LAUNCH else selected.actionType,
                payload = (shortcut.payload - runtimePayloadKeys) +
                    selected?.payload.orEmpty().filterKeys { it in runtimePayloadKeys } +
                    ("instanceCount" to instances.size.toString()),
                iconBase64 = shortcut.iconBase64 ?: selected?.iconBase64,
                isActive = instances.any { it.isActive },
                isVisible = instances.isNotEmpty(),
                monitorIndex = selected?.monitorIndex ?: 0,
            )
        }
        return Binding(buttons, windows)
    }

    private fun sameApplication(first: GridButton, second: GridButton): Boolean {
        val firstBundle = first.payload["bundlePath"]
        val secondBundle = second.payload["bundlePath"]
        if (firstBundle != null && secondBundle != null) return firstBundle.equals(secondBundle, ignoreCase = true)
        return first.payload["target"].equals(second.payload["target"], ignoreCase = true)
    }

    private fun matchesProject(shortcut: GridButton, window: GridButton): Boolean {
        val path = shortcut.payload["projectPath"]?.trim()?.takeIf(String::isNotBlank) ?: return true
        val project = path.trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')
        if (project.isBlank()) return false
        val title = window.payload["windowTitle"] ?: window.payload["value"] ?: return false
        return title.contains(project, ignoreCase = true)
    }
}
