package com.controldesk.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ControlShortcutKind { VOLUME, MICROPHONE, CAMERA, DEVICE, WEBSITE }

@Serializable
enum class ShortcutDestination { APPLICATION, URL, DEVICE }

/** User-owned action; never participates in runtime window reconciliation. */
@Serializable
data class ControlShortcut(
    val id: String,
    val name: String,
    val kind: ControlShortcutKind,
    val destination: ShortcutDestination = ShortcutDestination.APPLICATION,
    val target: String = "",
    val deviceId: String? = null,
    val deviceOperation: String? = null,
    val deviceLevel: Int = 50,
) {
    fun validationError(): String? = when {
        name.isBlank() -> "Informe um nome para o atalho."
        destination == ShortcutDestination.DEVICE -> when {
            deviceId.isNullOrBlank() -> "Selecione um dispositivo detectado pelo agente."
            DeviceOperation.entries.none { it.name == deviceOperation } -> "Selecione uma ação disponível."
            deviceOperation == "SET_LEVEL" && deviceLevel !in 0..100 -> "Nível deve estar entre 0 e 100."
            else -> null
        }
        kind == ControlShortcutKind.VOLUME || kind == ControlShortcutKind.MICROPHONE -> null
        target.isBlank() -> "Escolha um aplicativo ou informe a URL do dispositivo."
        destination == ShortcutDestination.URL && !isWebUrl(target.trim()) -> "Use uma URL http:// ou https:// válida."
        else -> null
    }

    fun toButton() = GridButton(
        id = id,
        label = name,
        actionType = if (destination == ShortcutDestination.DEVICE) ActionType.REMOTE_DEVICE else when (kind) {
            ControlShortcutKind.VOLUME -> ActionType.REMOTE_VOLUME
            ControlShortcutKind.MICROPHONE -> ActionType.REMOTE_MIC_MUTE
            else -> if (destination == ShortcutDestination.URL) ActionType.REMOTE_BROWSER_OPEN_SITE else ActionType.REMOTE_LAUNCH
        },
        payload = buildMap {
            put("controlKind", kind.name)
            if (destination == ShortcutDestination.DEVICE) {
                put("deviceId", deviceId.orEmpty())
                put("deviceOperation", deviceOperation.orEmpty())
                put("deviceLevel", deviceLevel.toString())
            } else when (kind) {
                ControlShortcutKind.VOLUME -> put("delta", "0")
                ControlShortcutKind.MICROPHONE -> put("toggle", "true")
                else -> if (destination == ShortcutDestination.URL) put("url", target.trim()) else {
                    put("target", target.trim())
                    put("targetType", "appName")
                    if (target.trim().endsWith(".app", ignoreCase = true)) put("bundlePath", target.trim())
                }
            }
        },
        isVisible = false,
        isFavoriteShortcut = true,
    )

    companion object {
        fun defaults() = listOf(
            ControlShortcut("system-volume", "Saída de áudio", ControlShortcutKind.VOLUME),
            ControlShortcut("system-microphone", "Microfone", ControlShortcutKind.MICROPHONE),
        )

        private fun isWebUrl(value: String): Boolean {
            val scheme = value.substringBefore("://", "").lowercase()
            val authority = value.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
            return scheme in setOf("http", "https") && authority.isNotBlank() &&
                value.none { it.isWhitespace() } && '@' !in authority
        }
    }
}

/** Combines user actions and app launchers under the same ordering and sizing rules. */
fun PairedHost.workspaceButtons(apps: List<GridButton>): List<GridButton> {
    val controls = controlShortcuts.map(ControlShortcut::toButton)
    val initial = controls + apps
    return initial.sortedBy { button ->
        appGridPositions[button.id] ?: appOrder.indexOf(button.id).takeIf { it >= 0 }
            ?: when (button.id) { "system-volume" -> -2; "system-microphone" -> -1; else -> Int.MAX_VALUE }
    }.map { button ->
        val size = appTileSizes["card:${button.id}"]
        if (size == null || button.payload["controlKind"] == null) button else button.copy(
            payload = button.payload + mapOf("spanColumns" to size.columns.toString(), "spanRows" to size.rows.toString()),
        )
    }
}
