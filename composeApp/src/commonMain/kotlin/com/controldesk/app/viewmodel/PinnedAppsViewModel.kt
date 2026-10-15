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
    private var devicesJob: kotlinx.coroutines.Job? = null

    fun onIntent(intent: PinnedAppsIntent) {
        when (intent) {
            PinnedAppsIntent.LoadDevices -> loadDevices()
            is PinnedAppsIntent.AuthorizeCamera -> authorizeCamera(intent.deviceId)
            is PinnedAppsIntent.EditControl -> {
                _uiState.update { it.copy(editingControl = intent.shortcut, controlError = null) }
                loadDevices()
            }
            is PinnedAppsIntent.UpdateControl -> {
                val previous = _uiState.value.editingControl
                _uiState.update { it.copy(editingControl = intent.shortcut, controlError = null) }
                if (intent.shortcut.destination == com.controldesk.domain.model.ShortcutDestination.DEVICE &&
                    (previous?.destination != intent.shortcut.destination || previous.kind != intent.shortcut.kind)) loadDevices()
            }
            PinnedAppsIntent.CancelControl -> _uiState.update { it.copy(editingControl = null, controlError = null) }
            PinnedAppsIntent.SaveControl -> saveControl()
            is PinnedAppsIntent.RemoveControl -> changeControls { controls -> controls.filterNot { it.id == intent.id } }
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

    private fun authorizeCamera(id: String) {
        val host = activeHost ?: return
        if (_uiState.value.requestingCameraAccess) return
        val device = _uiState.value.deviceCatalog.devices.singleOrNull { it.id == id } ?: return
        if (com.controldesk.domain.model.DeviceCapability.REQUEST_CAMERA_ACCESS !in device.capabilities) return
        _uiState.update { it.copy(requestingCameraAccess = true, controlError = null) }
        viewModelScope.launch {
            try {
                agentGateway.executeDeviceCommand(host, com.controldesk.domain.model.DeviceCommand(id, com.controldesk.domain.model.DeviceCapability.REQUEST_CAMERA_ACCESS))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) { _uiState.update { it.copy(controlError = error.message ?: "Confirme o acesso à câmera no Mac.") } }
            finally { _uiState.update { it.copy(requestingCameraAccess = false) } }
            loadDevices()
        }
    }

    private fun loadDevices() {
        val host = activeHost ?: return
        devicesJob?.cancel()
        _uiState.update { it.copy(isLoadingDevices = true, devicesError = null) }
        devicesJob = viewModelScope.launch {
            try {
                val catalog = agentGateway.loadDevices(host)
                _uiState.update { it.copy(deviceCatalog = catalog, isLoadingDevices = false) }
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) {
                _uiState.update { it.copy(isLoadingDevices = false, devicesError = error.message ?: "Não foi possível consultar os dispositivos") }
            }
        }
    }

    private fun saveControl() {
        val shortcut = _uiState.value.editingControl ?: return
        val error = shortcut.validationError()
        if (error != null) { _uiState.update { it.copy(controlError = error) }; return }
        if (shortcut.destination == com.controldesk.domain.model.ShortcutDestination.DEVICE) {
            val device = _uiState.value.deviceCatalog.devices.singleOrNull { it.id == shortcut.deviceId }
            val operation = com.controldesk.domain.model.DeviceOperation.entries.singleOrNull { it.name == shortcut.deviceOperation }
            if (device == null || (!shortcut.opensDevicePanel && operation?.capability !in device.capabilities)) {
                _uiState.update { it.copy(controlError = "Atualize a lista e selecione um dispositivo e uma ação disponíveis.") }
                return
            }
        }
        val saved = shortcut.copy(name = shortcut.name.trim(), target = shortcut.target.trim())
        changeControls { controls ->
            if (controls.any { it.id == saved.id }) controls.map { if (it.id == saved.id) saved else it }
            else controls + saved
        }
    }

    private fun changeControls(transform: (List<com.controldesk.domain.model.ControlShortcut>) -> List<com.controldesk.domain.model.ControlShortcut>) {
        val host = activeHost ?: return
        if (_uiState.value.isSavingControl) return
        _uiState.update { it.copy(isSavingControl = true, controlError = null) }
        viewModelScope.launch {
            runCatching {
                val latest = hostPreferences.getHosts().firstOrNull { it.id == host.id } ?: host
                val updated = latest.copy(controlShortcuts = transform(latest.controlShortcuts))
                hostPreferences.saveHost(updated)
                activeHost = updated
            }.onSuccess {
                _uiState.update { it.copy(editingControl = null, isSavingControl = false) }
            }.onFailure { error ->
                _uiState.update { it.copy(isSavingControl = false, controlError = error.message ?: "Não foi possível salvar o atalho") }
            }
        }
    }

    private fun loadInstalledApps(host: PairedHost) {
        if (activeHost?.id != host.id) {
            devicesJob?.cancel()
            _uiState.update { it.copy(deviceCatalog = com.controldesk.domain.model.DeviceCatalog(), devicesError = null, isLoadingDevices = false) }
        }
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
