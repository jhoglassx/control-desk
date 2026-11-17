package com.controldesk.data

import com.controldesk.domain.model.AppTileSize
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.ApplicationIdentity
import com.controldesk.domain.model.ApplicationShortcut
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost

/** Projects transient windows onto persistent launcher slots, without changing user layout. */
object DockShortcutProjector {
    data class Projection(
        val host: PairedHost,
        val buttons: List<GridButton>,
        val windows: Map<String, List<GridButton>>,
        val runtime: List<GridButton>,
    )

    fun project(savedHost: PairedHost, incoming: List<GridButton>): Projection {
        val resolved = ApplicationCatalogResolver.resolve(savedHost, incoming)
        val host = resolved.host
        val runtime = runtimeWindows(resolved.incoming)
        val groups = runtime.groupBy(::applicationKey)
        val shortcuts = host.applicationShortcuts.toMutableList()
        val order = host.appOrder.toMutableList()
        val positions = host.appGridPositions.toMutableMap()
        val sizes = host.appTileSizes.toMutableMap()
        val identities = host.appLayoutIdentities.toMutableMap()
        host.pinnedApps.forEach { favorite ->
            val sizeKey = "pinned:${favorite.id}"
            if (sizeKey !in sizes) {
                sizes[favorite.bundlePath.lowercase()]?.let { sizes[sizeKey] = it }
            }
        }
        groups.forEach { (key, candidates) ->
            val existingIndex = shortcuts.indexOfFirst { it.applicationKey == key }
            if (existingIndex >= 0) {
                if (shortcuts[existingIndex].iconBase64 == null) {
                    candidates.firstNotNullOfOrNull { it.iconBase64 }?.let { icon ->
                        shortcuts[existingIndex] = shortcuts[existingIndex].copy(iconBase64 = icon)
                    }
                }
                return@forEach
            }
            val source = candidates.first()
            val bundle = source.payload["bundlePath"]
            val target = source.payload["target"] ?: source.label
            val shortcut = ApplicationShortcut(
                id = "application:$key",
                applicationKey = key,
                name = ApplicationIdentity.applicationKey(bundle, target),
                target = target,
                bundlePath = bundle,
                iconBase64 = candidates.firstNotNullOfOrNull { it.iconBase64 },
            )
            shortcuts += shortcut
            // Migrate one legacy card once, preferring an explicitly customized size.
            // Keep old preferences for recovery; subsequent snapshots never migrate again.
            val legacyIds = order.filter { id ->
                candidates.any { it.id == id } || identityApp(identities[id]) == key ||
                    id == ApplicationIdentity.dockShortcutId(bundle, target)
            }
            val oldId = legacyIds.lastOrNull { id ->
                val size = sizes["card:$id"] ?: identities[id]?.let(sizes::get)
                size != null && size != AppTileSize()
            } ?: legacyIds.firstOrNull()
            val oldIndex = oldId?.let(order::indexOf)?.takeIf { it >= 0 }
            if (shortcut.id !in order) order.add(oldIndex ?: order.size, shortcut.id)
            oldId?.let { positions[it] }?.let { positions[shortcut.id] = it }
            val sizeKeys = buildList {
                oldId?.let { add("card:$it") }
                oldId?.let { identities[it] }?.let(::add)
                candidates.firstOrNull { it.id == oldId }?.let { candidate ->
                    candidate.payload["stableWindowId"]?.let { add("window:$key:id:$it") }
                    candidate.payload["windowTitle"]?.let { add("window:$key:title:${it.lowercase()}") }
                }
                add("card:${ApplicationIdentity.dockShortcutId(bundle, target)}")
                add("app:$key")
                add(key)
            }
            sizeKeys.firstNotNullOfOrNull(sizes::get)?.let { sizes["card:${shortcut.id}"] = it }
            identities[shortcut.id] = "app:$key"
        }
        val windows = shortcuts.associate { shortcut ->
            shortcut.id to groups[shortcut.applicationKey].orEmpty().filter { candidate ->
                isSelectableWindow(candidate)
            }.sortedWith(compareByDescending<GridButton> { it.isActive }.thenBy { it.id })
        }
        val buttons = shortcuts.map { shortcut ->
            val instances = windows[shortcut.id].orEmpty()
            val selected = instances.firstOrNull()
            val basePayload = buildMap {
                put("applicationShortcutId", shortcut.id)
                put("target", shortcut.target)
                put("targetType", "appName")
                shortcut.bundlePath?.let { put("bundlePath", it) }
            }
            GridButton(
                id = shortcut.id,
                label = shortcut.name,
                actionType = selected?.actionType ?: ActionType.REMOTE_LAUNCH,
                payload = basePayload + selected?.payload.orEmpty() + ("instanceCount" to instances.size.toString()),
                iconBase64 = groups[shortcut.applicationKey].orEmpty().firstNotNullOfOrNull { it.iconBase64 }
                    ?: shortcut.iconBase64,
                isActive = instances.any { it.isActive },
                isVisible = instances.isNotEmpty(),
                monitorIndex = selected?.monitorIndex ?: 0,
                isDockPinned = true,
            )
        }
        return Projection(
            host.copy(applicationShortcuts = shortcuts, appOrder = order, appGridPositions = positions,
                appTileSizes = sizes, appLayoutIdentities = identities),
            buttons,
            windows,
            runtime,
        )
    }

    fun isSelectableWindow(button: GridButton): Boolean =
        button.payload["windowCount"]?.toIntOrNull() != 0 &&
            (button.isVisible || button.isActive || button.payload["pid"] != null)

    /** A browser's tabs do not replace its native windows in the launcher instance picker. */
    fun runtimeWindows(incoming: List<GridButton>): List<GridButton> = incoming.groupBy(::applicationKey).values.flatMap { candidates ->
        val windows = candidates.filter { it.actionType != ActionType.REMOTE_BROWSER_TAB }
        windows.ifEmpty { candidates }
    }

    private fun applicationKey(button: GridButton): String =
        (button.payload["bundlePath"] ?: button.payload["target"] ?: button.label).trimEnd('/').lowercase()

    private fun identityApp(identity: String?): String? = when {
        identity == null -> null
        identity.startsWith("app:") -> identity.removePrefix("app:").lowercase()
        identity.startsWith("window:") -> identity.removePrefix("window:")
            .substringBefore(":title:").substringBefore(":id:").substringBefore(":index:").lowercase()
        else -> null
    }
}
