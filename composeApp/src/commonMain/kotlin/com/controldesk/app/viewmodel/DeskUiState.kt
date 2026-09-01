package com.controldesk.app.viewmodel

import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost

data class DeskUiState(
    val hosts: List<PairedHost> = emptyList(),
    val selectedHostIndex: Int = 0,
    val buttons: List<GridButton> = emptyList(),
    val cachedButtons: Map<String, List<GridButton>> = emptyMap(),
    val cachedListHints: Map<String, String?> = emptyMap(),
    val isExecuting: Boolean = false,
    val isLoadingApps: Boolean = false,
    val volumeLevel: Int = 50,
    val volumeMuted: Boolean = false,
    val micMuted: Boolean = false,
    val micLevel: Int = 75,
    val listHint: String? = null,
    val feedback: String? = null,
) {
    val selectedHost: PairedHost? get() = hosts.getOrNull(selectedHostIndex)
    val hasHosts: Boolean get() = hosts.isNotEmpty()

    fun buttonsFor(hostId: String): List<GridButton> =
        if (selectedHost?.id == hostId) buttons else cachedButtons[hostId].orEmpty()

    fun listHintFor(hostId: String): String? =
        if (selectedHost?.id == hostId) listHint else cachedListHints[hostId]
}
