package com.controldesk.data

import com.controldesk.domain.model.ApplicationIdentity
import com.controldesk.domain.model.ApplicationShortcut
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost

/** Repairs name-only records when an unambiguous bundle becomes available. */
internal object ApplicationCatalogResolver {
    data class Resolution(val host: PairedHost, val incoming: List<GridButton>)

    fun resolve(host: PairedHost, incoming: List<GridButton>): Resolution {
        val aliases = mutableMapOf<String, MutableMap<String, String>>()
        fun remember(path: String?, vararg names: String?) {
            val bundle = path?.takeIf(String::isNotBlank)?.trimEnd('/') ?: return
            (names.toList() + ApplicationIdentity.applicationKey(bundle, "")).forEach { name ->
                name?.takeIf(String::isNotBlank)?.lowercase()?.let { alias ->
                    aliases.getOrPut(alias) { linkedMapOf() }[bundle.lowercase()] = bundle
                }
            }
        }
        host.applicationShortcuts.forEach { remember(it.bundlePath, it.name, it.target, *it.aliases.toTypedArray()) }
        host.pinnedApps.forEach { remember(it.bundlePath, it.name) }
        incoming.forEach { remember(it.payload["bundlePath"], it.payload["target"]) }
        fun bundleFor(path: String?, vararg names: String?): String? = path?.takeIf(String::isNotBlank)
            ?: names.mapNotNull { aliases[it?.lowercase()]?.values?.singleOrNull() }.distinct().singleOrNull()
        val resolvedIncoming = incoming.map { button ->
            val path = bundleFor(button.payload["bundlePath"], button.payload["target"])
            if (path == null) button else button.copy(payload = button.payload + ("bundlePath" to path))
        }
        val normalized = host.applicationShortcuts.map { shortcut ->
            val path = bundleFor(shortcut.bundlePath, shortcut.name, shortcut.target)
            if (path == null) shortcut else shortcut.copy(applicationKey = path.trimEnd('/').lowercase(), bundlePath = path)
        }
        val order = host.appOrder.toMutableList()
        val positions = host.appGridPositions.toMutableMap()
        val sizes = host.appTileSizes.toMutableMap()
        val identities = host.appLayoutIdentities.toMutableMap()
        val survivors = mutableListOf<ApplicationShortcut>()
        normalized.groupBy { it.applicationKey }.values.forEach { duplicates ->
            val survivor = duplicates.minBy { shortcut ->
                order.indexOf(shortcut.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
            val knownAliases = duplicates.flatMap { it.aliases + listOf(it.name, it.target) } +
                resolvedIncoming.filter { it.payload["bundlePath"]?.trimEnd('/')?.lowercase() == survivor.applicationKey }
                    .mapNotNull { it.payload["target"] }
            survivors += survivor.copy(aliases = knownAliases.map(String::lowercase).distinct().sorted())
            duplicates.filterNot { it.id == survivor.id }.forEach { duplicate ->
                val survivorSizeKey = "card:${survivor.id}"
                if (survivorSizeKey !in sizes) sizes["card:${duplicate.id}"]?.let { sizes[survivorSizeKey] = it }
                if (survivor.id !in positions) positions[duplicate.id]?.let { positions[survivor.id] = it }
                positions.remove(duplicate.id)
                order.indices.forEach { index -> if (order[index] == duplicate.id) order[index] = survivor.id }
                identities.remove(duplicate.id)
            }
            identities[survivor.id] = "app:${survivor.applicationKey}"
        }
        return Resolution(host.copy(applicationShortcuts = survivors, appOrder = order.distinct(),
            appGridPositions = positions, appTileSizes = sizes, appLayoutIdentities = identities), resolvedIncoming)
    }
}
