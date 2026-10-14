package com.controldesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.PinnedAppShortcut
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.domain.repository.HostPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PinnedAppsViewModel(
    private val agentGateway: AgentGateway,
    private val hostPreferences: HostPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PinnedAppsUiState())
    val uiState: StateFlow<PinnedAppsUiState> = _uiState.asStateFlow()
    private var activeHost: PairedHost? = null

    fun onIntent(intent: PinnedAppsIntent) {
        when (intent) {
            is PinnedAppsIntent.LoadInstalledApps -> loadInstalledApps(intent.host)
            is PinnedAppsIntent.PinApp -> updatePinnedApps { apps ->
                if (apps.any { it.bundlePath == intent.app.bundlePath }) apps
                else apps + intent.app.copy(order = apps.size)
            }
            is PinnedAppsIntent.UnpinApp -> updatePinnedApps { apps -> apps.filterNot { it.id == intent.id } }
            is PinnedAppsIntent.SaveProjectPath -> updatePinnedApps { apps ->
                apps.map { app -> if (app.id == intent.id) app.copy(projectPath = intent.path.trim().ifBlank { null }) else app }
            }
            is PinnedAppsIntent.MoveApp -> moveApp(intent.id, intent.delta)
            is PinnedAppsIntent.MoveAppTo -> moveAppTo(intent.id, intent.order)
            is PinnedAppsIntent.ChoosePathFor -> {
                _uiState.update { it.copy(selectedAppId = intent.id, fileListing = null, filesError = null) }
                browseFiles(null)
            }
            is PinnedAppsIntent.BrowseFiles -> browseFiles(intent.path)
            is PinnedAppsIntent.SelectPath -> {
                val appId = _uiState.value.selectedAppId ?: return
                onIntent(PinnedAppsIntent.SaveProjectPath(appId, intent.path))
                _uiState.update { it.copy(selectedAppId = null, fileListing = null, filesError = null) }
            }
            PinnedAppsIntent.CancelPathSelection -> _uiState.update {
                it.copy(selectedAppId = null, fileListing = null, filesError = null)
            }
        }
    }

    private fun loadInstalledApps(host: PairedHost) {
        activeHost = host
        _uiState.update { it.copy(isLoadingApps = true, appsError = null) }
        viewModelScope.launch {
            runCatching { agentGateway.loadInstalledApps(host) }
                .onSuccess { apps ->
                    _uiState.update { it.copy(installedApps = apps, isLoadingApps = false, appsError = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoadingApps = false, appsError = error.message ?: "Não foi possível listar os apps do Mac")
                    }
                }
        }
    }

    private fun browseFiles(path: String?) {
        val host = activeHost ?: return
        _uiState.update { it.copy(isLoadingFiles = true, filesError = null) }
        viewModelScope.launch {
            runCatching { agentGateway.browseMacFiles(host, path) }
                .onSuccess { listing -> _uiState.update { it.copy(fileListing = listing, isLoadingFiles = false, filesError = null) } }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoadingFiles = false, filesError = error.message ?: "Não foi possível abrir as pastas do Mac")
                    }
                }
        }
    }

    private fun moveApp(id: String, delta: Int) {
        updatePinnedApps { current ->
            val apps = current.sortedBy { it.order }.toMutableList()
            val from = apps.indexOfFirst { it.id == id }
            if (from < 0) return@updatePinnedApps current
            val to = (from + delta).coerceIn(0, apps.lastIndex)
            if (from == to) return@updatePinnedApps current
            val app = apps.removeAt(from)
            apps.add(to, app)
            apps
        }
    }

    private fun moveAppTo(id: String, order: Int) {
        updatePinnedApps { current ->
            val apps = current.sortedBy { it.order }.toMutableList()
            val from = apps.indexOfFirst { it.id == id }
            if (from < 0) return@updatePinnedApps current
            val to = order.coerceIn(0, apps.lastIndex)
            if (from == to) return@updatePinnedApps current
            apps.add(to, apps.removeAt(from))
            apps
        }
    }

    private fun updatePinnedApps(transform: (List<PinnedAppShortcut>) -> List<PinnedAppShortcut>) {
        val host = activeHost ?: return
        val updated = host.copy(pinnedApps = transform(host.pinnedApps).mapIndexed { index, app -> app.copy(order = index) })
        activeHost = updated
        viewModelScope.launch { hostPreferences.saveHost(updated) }
    }
}
