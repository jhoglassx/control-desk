package com.controldesk.app.viewmodel

import com.controldesk.domain.model.workspaceButtons
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost

sealed interface DeskIntent {
    data object DismissWindowSelection : DeskIntent
    data class SelectWindow(val windowId: String) : DeskIntent
    data class SetReorganizingApps(val enabled: Boolean) : DeskIntent
    data class SelectAppForReorder(val appId: String, val cellWidth: Float = 0f, val cellHeight: Float = 0f) : DeskIntent
    data class MoveSelectedAppBy(val deltaX: Float, val deltaY: Float) : DeskIntent
    data class PreviewReorderApp(val appId: String, val targetIndex: Int) : DeskIntent
    data object FinishAppReorderDrag : DeskIntent
    data class ReorderApps(val orderedIds: List<String>) : DeskIntent
    data class ResizeAppTile(val buttonId: String) : DeskIntent
    data class PreviewAppGridPositions(val positions: Map<String, Int>) : DeskIntent
}

data class DeskUiState(
    val deviceCatalogs: Map<String, com.controldesk.domain.model.DeviceCatalog> = emptyMap(),
    val deviceErrors: Map<String, String> = emptyMap(),
    val executingDeviceIds: Set<String> = emptySet(),
    val hosts: List<PairedHost> = emptyList(),
    val selectedHostIndex: Int = 0,
    val buttons: List<GridButton> = emptyList(),
    val cachedButtons: Map<String, List<GridButton>> = emptyMap(),
    val cachedListHints: Map<String, String?> = emptyMap(),
    val isExecuting: Boolean = false,
    val isLoadingApps: Boolean = false,
    val isReorganizingApps: Boolean = false,
    val selectedAppIdForReorder: String? = null,
    val selectedAppDragOffsetX: Float = 0f,
    val selectedAppDragOffsetY: Float = 0f,
    val selectedAppDragStartIndex: Int? = null,
    val selectedAppDragCellWidth: Float = 0f,
    val selectedAppDragCellHeight: Float = 0f,
    val volumeLevel: Int = 50,
    val volumeMuted: Boolean = false,
    val micMuted: Boolean = false,
    val micLevel: Int = 75,
    val listHint: String? = null,
    val feedback: String? = null,
    val windowSelection: WindowSelection? = null,
) {
    val selectedHost: PairedHost? get() = hosts.getOrNull(selectedHostIndex)
    val hasHosts: Boolean get() = hosts.isNotEmpty()

    fun buttonsFor(hostId: String): List<GridButton> =
        if (selectedHost?.id == hostId) buttons else cachedButtons[hostId].orEmpty()

    fun workspaceButtonsFor(hostId: String): List<GridButton> =
        hosts.firstOrNull { it.id == hostId }?.workspaceButtons(buttonsFor(hostId)).orEmpty().map { button ->
            val deviceId = button.payload["deviceId"] ?: return@map button
            val catalog = deviceCatalogs[hostId]
            val device = catalog?.devices?.singleOrNull { it.id == deviceId }
            val status = when {
                "$hostId:$deviceId" in executingDeviceIds -> "Executando…"
                hostId in deviceErrors -> "Estado indisponível"
                catalog == null -> "Consultando agente…"
                device == null -> "Desconectado / indisponível"
                device.enabled == false -> "Câmera bloqueada"
                device.muted == true -> "Mudo"
                device.isDefault == true -> "Dispositivo padrão"
                device.level != null -> "${device.level}%"
                else -> "Disponível"
            }
            button.copy(payload = button.payload + ("deviceStatus" to status))
        }

    fun listHintFor(hostId: String): String? =
        if (selectedHost?.id == hostId) listHint else cachedListHints[hostId]
}

data class WindowSelection(
    val hostId: String,
    val shortcutId: String,
    val appName: String,
    val windows: List<GridButton>,
    val close: Boolean,
)
