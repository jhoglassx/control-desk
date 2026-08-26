package com.controldesk.app.viewmodel

import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost

data class DeskUiState(
    val hosts: List<PairedHost> = emptyList(),
    val selectedHostIndex: Int = 0,
    val buttons: List<GridButton> = emptyList(),
    val isExecuting: Boolean = false,
    val isLoadingApps: Boolean = false,
    val volumeLevel: Int = 50,
    val volumeMuted: Boolean = false,
    val micMuted: Boolean = false,
    val listHint: String? = null,
    val feedback: String? = null,
) {
    val selectedHost: PairedHost? get() = hosts.getOrNull(selectedHostIndex)
    val hasHosts: Boolean get() = hosts.isNotEmpty()
}
