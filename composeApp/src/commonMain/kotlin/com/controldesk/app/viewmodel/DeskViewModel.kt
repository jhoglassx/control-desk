package com.controldesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controldesk.data.ShortcutWindowBinder
import com.controldesk.data.DockShortcutProjector
import com.controldesk.data.RunningAppsGridBuilder
import com.controldesk.data.RealtimeUnavailableException
import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.ApplicationIdentity
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.AppTileSize
import com.controldesk.domain.model.AgentRealtimeEvent
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.workspaceButtons
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.PinnedAppShortcut
import com.controldesk.domain.model.RealtimeConnectionState
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.domain.repository.AgentRealtime
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.repository.RunningAppIconCache
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.logging.ControlDeskLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class DeskViewModel(
    private val hostPreferences: HostPreferences,
    private val agentGateway: AgentGateway,
    private val agentRealtime: AgentRealtime,
    private val executeGridButton: ExecuteGridButtonUseCase,
    private val iconCache: RunningAppIconCache,
    coroutineScope: CoroutineScope? = null,
) : ViewModel() {

    private val jobsScope = coroutineScope ?: viewModelScope
    private val _uiState = MutableStateFlow(DeskUiState())
    val uiState: StateFlow<DeskUiState> = _uiState.asStateFlow()

    private var realtimeHostId: String? = null
    private val windowsByHost = mutableMapOf<String, Map<String, List<GridButton>>>()
    private val buttonsCache = mutableMapOf<String, List<GridButton>>()
    private val lastAppliedTileSizeLog = mutableMapOf<String, String>()
    private val audioCache = mutableMapOf<String, AudioState>()
    private val listHintCache = mutableMapOf<String, String?>()
    private var memoryIcons: Map<String, String> = emptyMap()

    private var autoRefreshJob: Job? = null
    private var realtimeEventsJob: Job? = null
    private var burstJob: Job? = null
    private var audioTick = 0
    private var holdingAudioUntil: TimeMark? = null
    private var adjustingVolume = false
    private var adjustingMic = false
    private var devicePollJob: Job? = null
    private val deviceRefreshVersions = mutableMapOf<String, Int>()
    private var pendingHostLoad: Job? = null
    private var audioPollJob: Job? = null
    private var volumeSendJob: Job? = null
    private var micSendJob: Job? = null
    private var pendingVolume: Int? = null
    private var pendingMic: Int? = null
    private var lastMicLevel = 75

    private var pinnedButtonId: String? = null
    private var pinnedWindowNumber: String? = null
    private var pinnedTarget: String? = null
    private var pinnedUntil: TimeMark? = null
    private val closingUntil = mutableMapOf<String, TimeMark>()
    private val closingPlaceholders = mutableMapOf<String, GridButton>()
    private var lastIncomingActiveId: String? = null
    private var incomingActiveStreak = 0
    private var currentActiveId: String? = null
    private val currentActiveIdByHost = mutableMapOf<String, String?>()
    private val lastIncomingActiveIdByHost = mutableMapOf<String, String?>()
    private val incomingActiveStreakByHost = mutableMapOf<String, Int>()
    private var activeTrackingHostId: String? = null

    init {
        jobsScope.launch {
            memoryIcons = iconCache.load()
        }
        jobsScope.launch {
            hostPreferences.observeHosts().collect { hosts ->
                val sorted = hosts.sortedBy { it.sortOrder }
                if (sorted.isEmpty()) {
                    stopAutoRefresh()
                    devicePollJob?.cancel()
                    _uiState.value = DeskUiState()
                    return@collect
                }
                val previousHost = _uiState.value.selectedHost
                val index = sorted.indexOfFirst { it.id == previousHost?.id }.takeIf { it >= 0 } ?: 0
                val selectedHost = sorted[index]
                _uiState.update { it.copy(hosts = sorted, selectedHostIndex = index) }
                if (previousHost == null || previousHost.connectionChangedFrom(selectedHost)) {
                    loadHost(selectedHost, showLoading = previousHost == null)
                } else if (previousHost.controlShortcuts != selectedHost.controlShortcuts) {
                    startDevicePolling(selectedHost)
                }
            }
        }
    }

    private fun PairedHost.connectionChangedFrom(other: PairedHost): Boolean =
        id != other.id || host != other.host || port != other.port || token != other.token

    fun refreshHosts() {
        jobsScope.launch {
            val hosts = hostPreferences.getHosts().sortedBy { it.sortOrder }
            _uiState.update { state ->
                val index = hosts.indexOfFirst { it.id == state.selectedHost?.id }.takeIf { it >= 0 } ?: 0
                state.copy(hosts = hosts, selectedHostIndex = index.coerceAtMost((hosts.size - 1).coerceAtLeast(0)))
            }
            _uiState.value.selectedHost?.let { loadHost(it, showLoading = false) }
        }
    }

    fun selectHost(index: Int) {
        val host = _uiState.value.hosts.getOrNull(index) ?: return
        clearPinnedFocus()
        closingUntil.clear()
        closingPlaceholders.clear()
        bindActiveTracking(host.id)
        restoreCache(host)
        _uiState.update { it.copy(selectedHostIndex = index, windowSelection = null, devicePanel = null) }
        loadHost(host, showLoading = buttonsCache[host.id].isNullOrEmpty())
    }

    fun refreshRunningApps() {
        val host = _uiState.value.selectedHost ?: return
        loadHost(host, showLoading = true)
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    fun onIntent(intent: DeskIntent) {
        when (intent) {
            DeskIntent.DismissDevicePanel -> _uiState.update { it.copy(devicePanel = null) }
            DeskIntent.RefreshDevicePanel -> _uiState.value.devicePanel?.let { panel ->
                _uiState.value.hosts.firstOrNull { it.id == panel.hostId }?.let { host -> jobsScope.launch { refreshDevices(host) } }
            }
            DeskIntent.AuthorizePanelCamera -> authorizePanelCamera()
            is DeskIntent.UpdateDevicePanelLevel -> _uiState.update { it.copy(devicePanel = it.devicePanel?.copy(level = intent.level.coerceIn(0, 100))) }
            is DeskIntent.ExecuteDevicePanelAction -> executeDevicePanelAction(intent.operation)
            DeskIntent.DismissWindowSelection -> _uiState.update { it.copy(windowSelection = null) }
            is DeskIntent.SelectWindow -> selectWindow(intent.windowId)
            is DeskIntent.SetReorganizingApps -> _uiState.update {
                it.copy(
                    isReorganizingApps = intent.enabled,
                    selectedAppIdForReorder = if (intent.enabled) it.selectedAppIdForReorder else null,
                    selectedAppDragOffsetX = 0f,
                    selectedAppDragOffsetY = 0f,
                    selectedAppDragStartIndex = null,
                    selectedAppDragCellWidth = 0f,
                    selectedAppDragCellHeight = 0f,
                )
            }
            is DeskIntent.SelectAppForReorder -> _uiState.update { state ->
                if (state.isReorganizingApps) state.copy(
                    selectedAppIdForReorder = intent.appId,
                    selectedAppDragOffsetX = 0f,
                    selectedAppDragOffsetY = 0f,
                    selectedAppDragStartIndex = state.selectedHost?.workspaceButtons(state.buttons)?.indexOfFirst { button -> button.id == intent.appId }?.takeIf { index -> index >= 0 },
                    selectedAppDragCellWidth = intent.cellWidth,
                    selectedAppDragCellHeight = intent.cellHeight,
                ) else state
            }
            is DeskIntent.MoveSelectedAppBy -> _uiState.update { state ->
                if (state.isReorganizingApps && state.selectedAppIdForReorder != null) {
                    state.copy(
                        selectedAppDragOffsetX = state.selectedAppDragOffsetX + intent.deltaX,
                        selectedAppDragOffsetY = state.selectedAppDragOffsetY + intent.deltaY,
                    )
                } else state
            }
            DeskIntent.FinishAppReorderDrag -> finishAppReorderDrag()
            is DeskIntent.PreviewAppGridPositions -> previewAppGridPositions(intent.positions)
            is DeskIntent.PreviewReorderApp -> previewReorderApp(intent.appId, intent.targetIndex)
            is DeskIntent.ReorderApps -> reorderApps(intent.orderedIds)
            is DeskIntent.ResizeAppTile -> resizeAppTile(intent.buttonId)
        }
    }

    private fun finishAppReorderDrag() {
        val state = _uiState.value
        val host = state.selectedHost?.let { persistGridLayout(it, it.workspaceButtons(state.buttons)) }
        _uiState.update {
            it.copy(
                selectedAppIdForReorder = null,
                selectedAppDragOffsetX = 0f,
                selectedAppDragOffsetY = 0f,
                selectedAppDragStartIndex = null,
                selectedAppDragCellWidth = 0f,
                selectedAppDragCellHeight = 0f,
                hosts = if (host == null) it.hosts else it.hosts.map { saved ->
                    if (saved.id == host.id) host else saved
                },
            )
        }
        host?.let { jobsScope.launch { hostPreferences.saveHost(it) } }
    }

    private fun previewAppGridPositions(positions: Map<String, Int>) {
        val host = _uiState.value.selectedHost ?: return
        val updatedHost = host.copy(appGridPositions = positions)
        _uiState.update { state ->
            state.copy(hosts = state.hosts.map { if (it.id == host.id) updatedHost else it })
        }
    }

    private fun resizeAppTile(buttonId: String) {
        val state = _uiState.value
        val host = state.selectedHost?.let { persistGridLayout(it, it.workspaceButtons(state.buttons)) } ?: return
        val sizes = listOf(1 to 1, 2 to 1, 2 to 2, 1 to 2)
        val currentButton = host.workspaceButtons(state.buttons).firstOrNull { it.id == buttonId } ?: return
        val sizeKeys = tileSizeKeys(currentButton)
        val windowKeys = tileSizeKeys(currentButton)
        val currentSize = currentButton.tileSpanColumns() to currentButton.tileSpanRows()
        val nextSize = sizes[(sizes.indexOf(currentSize) + 1).mod(sizes.size)]
        ControlDeskLog.app.i {
            "[app-layout] resize host=${host.id} tile=$buttonId label=\"${currentButton.label}\" " +
                "title=\"${currentButton.payload["windowTitle"].orEmpty()}\" pid=${currentButton.payload["pid"]} " +
                "stableWindowId=${currentButton.payload["stableWindowId"]} keys=${sizeKeys.joinToString()} " +
                "from=${currentSize.first}x${currentSize.second} to=${nextSize.first}x${nextSize.second}"
        }
        val updatedHost = host.copy(
            appTileSizes = host.appTileSizes + sizeKeys.associateWith {
                AppTileSize(nextSize.first, nextSize.second)
            },
        )
        val updatedButtons = state.buttons.map { button ->
            if (button.id != currentButton.id && tileSizeKeys(button).none(windowKeys::contains)) button
            else button.copy(payload = button.payload + mapOf(
                "spanColumns" to nextSize.first.toString(),
                "spanRows" to nextSize.second.toString(),
            ))
        }
        buttonsCache[host.id] = updatedButtons
        _uiState.update { state ->
            state.copy(
                buttons = if (state.selectedHost?.id == host.id) updatedButtons else state.buttons,
                cachedButtons = state.cachedButtons + (host.id to updatedButtons),
                hosts = state.hosts.map { if (it.id == host.id) updatedHost else it },
            )
        }
        jobsScope.launch {
            runCatching { hostPreferences.saveHost(updatedHost) }
                .onSuccess {
                    ControlDeskLog.app.i {
                        "[app-layout] persisted resize host=${host.id} keys=${sizeKeys.joinToString()} " +
                            "size=${nextSize.first}x${nextSize.second}"
                    }
                }
                .onFailure { error ->
                    ControlDeskLog.app.e(error) {
                        "[app-layout] failed to persist resize host=${host.id} keys=${sizeKeys.joinToString()}"
                    }
                }
        }
    }

    /** Saves a complete compact grid order so future app snapshots cannot choose a new default order. */
    private fun persistGridLayout(host: PairedHost, buttons: List<GridButton>): PairedHost {
        val positions = host.appGridPositions.toMutableMap()
        val identities = host.appLayoutIdentities.toMutableMap()
        var nextPosition = (positions.values.maxOrNull() ?: -1) + 1
        buttons.forEach { button ->
            if (button.id !in positions) positions[button.id] = nextPosition++
            identities[button.id] = appLayoutIdentity(button)
        }
        val currentOrder = buttons.sortedBy { positions[it.id] ?: Int.MAX_VALUE }.map(GridButton::id)
        val order = currentOrder + host.appOrder.filterNot { it in currentOrder }
        return host.copy(appOrder = order, appGridPositions = positions, appLayoutIdentities = identities)
    }

    /** Keeps a Dock shortcut in the closed window's grid slot and carries its size forward. */
    private fun persistClosedDockShortcutLayout(
        host: PairedHost,
        buttons: List<GridButton>,
        closed: GridButton,
        placeholder: GridButton?,
    ): PairedHost {
        val layout = persistGridLayout(host, host.workspaceButtons(buttons))
        if (placeholder == null || placeholder.id == closed.id) return layout

        val positions = layout.appGridPositions.toMutableMap()
        val closedPosition = positions.remove(closed.id)
        if (closedPosition != null && placeholder.id !in positions) positions[placeholder.id] = closedPosition

        val sizes = layout.appTileSizes.toMutableMap()
        val closedSizeKeys = tileSizeKeys(closed)
        val placeholderSizeKey = tileSizeKey(placeholder)
        if (placeholderSizeKey !in closedSizeKeys) {
            closedSizeKeys.firstNotNullOfOrNull(sizes::remove)?.let { sizes[placeholderSizeKey] = it }
        }
        val order = layout.appOrder.map { if (it == closed.id) placeholder.id else it }.distinct()
        val identities = layout.appLayoutIdentities.toMutableMap()
        identities.remove(closed.id)
        identities[placeholder.id] = appLayoutIdentity(placeholder)
        return layout.copy(
            appOrder = order,
            appGridPositions = positions,
            appTileSizes = sizes,
            appLayoutIdentities = identities,
        )
    }

    private fun reorderApps(orderedIds: List<String>) {
        val host = _uiState.value.selectedHost ?: return
        val currentIds = _uiState.value.buttons.map { it.id }
        val visibleOrder = orderedIds.filter { it in currentIds }.distinct()
        if (visibleOrder.size < 2 || visibleOrder.toSet() != currentIds.toSet()) return

        val persistedOrder = visibleOrder + host.appOrder.filterNot { it in visibleOrder }
        val updatedHost = host.copy(appOrder = persistedOrder)
        val reorderedButtons = reorderButtons(_uiState.value.buttons, persistedOrder)
        buttonsCache[host.id] = reorderedButtons
        _uiState.update { state ->
            state.copy(
                buttons = if (state.selectedHost?.id == host.id) reorderedButtons else state.buttons,
                cachedButtons = state.cachedButtons + (host.id to reorderedButtons),
                hosts = state.hosts.map { if (it.id == host.id) updatedHost else it },
            )
        }
        jobsScope.launch { hostPreferences.saveHost(updatedHost) }
    }

    private fun previewReorderApp(appId: String, targetIndex: Int) {
        val state = _uiState.value
        if (!state.isReorganizingApps || state.selectedAppIdForReorder != appId) return
        val hostId = state.selectedHost?.id ?: return
        val currentIndex = state.buttons.indexOfFirst { it.id == appId }
        if (currentIndex < 0 || targetIndex !in state.buttons.indices || currentIndex == targetIndex) return
        val reorderedButtons = state.buttons.toMutableList().apply {
            add(targetIndex, removeAt(currentIndex))
        }
        buttonsCache[hostId] = reorderedButtons
        _uiState.update { current ->
            if (!current.isReorganizingApps || current.selectedAppIdForReorder != appId) current
            else current.copy(
                buttons = reorderedButtons,
                cachedButtons = current.cachedButtons + (hostId to reorderedButtons),
            )
        }
    }

    fun onButtonClick(button: GridButton) {
        if (button.actionType == ActionType.REMOTE_DEVICE) {
            if (button.payload["deviceOperation"] == com.controldesk.domain.model.ControlShortcut.OPEN_CONTROLS) {
                val host = _uiState.value.selectedHost ?: return
                val deviceId = button.payload["deviceId"] ?: return
                val device = _uiState.value.deviceCatalogs[host.id]?.devices?.singleOrNull { it.id == deviceId }
                _uiState.update { it.copy(devicePanel = DevicePanelSelection(host.id, deviceId, button.label, device?.level ?: 50)) }
                jobsScope.launch { refreshDevices(host) }
                return
            }
            executeDeviceShortcut(button)
            return
        }
        if (button.payload["controlKind"] != null) {
            if (button.payload["controlKind"] == "VOLUME") { onVolumeKnobClick(); return }
            if (button.payload["controlKind"] == "MICROPHONE") { onMicKnobClick(); return }
            val host = _uiState.value.selectedHost ?: return
            jobsScope.launch {
                runCatching { executeGridButton(button, host) }
                    .onFailure { error -> _uiState.update { it.copy(feedback = error.message ?: "Falha ao executar atalho") } }
            }
            return
        }
        if (requestWindowSelection(button, close = false)) return
        executeButtonClick(button)
    }

    private fun executeDevicePanelAction(operation: com.controldesk.domain.model.DeviceOperation) {
        val state = _uiState.value
        val panel = state.devicePanel ?: return
        if (panel.hostId != state.selectedHost?.id) return
        executeDeviceShortcut(GridButton("device-panel", panel.name, ActionType.REMOTE_DEVICE,
            payload = mapOf("deviceId" to panel.deviceId, "deviceOperation" to operation.name, "deviceLevel" to panel.level.toString())))
    }

    private fun authorizePanelCamera() {
        val state = _uiState.value
        val panel = state.devicePanel ?: return
        val host = state.selectedHost?.takeIf { it.id == panel.hostId } ?: return
        val key = "${host.id}:${panel.deviceId}"
        if (key in state.executingDeviceIds) return
        _uiState.update { it.copy(executingDeviceIds = it.executingDeviceIds + key) }
        jobsScope.launch {
            try {
                val device = agentGateway.loadDevices(host).devices.singleOrNull { it.id == panel.deviceId }
                check(com.controldesk.domain.model.DeviceCapability.REQUEST_CAMERA_ACCESS in device?.capabilities.orEmpty()) { "Autorização indisponível; verifique os Ajustes do Mac." }
                agentGateway.executeDeviceCommand(host, com.controldesk.domain.model.DeviceCommand(panel.deviceId, com.controldesk.domain.model.DeviceCapability.REQUEST_CAMERA_ACCESS))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) { _uiState.update { it.copy(feedback = error.message ?: "Confirme a permissão no Mac") } }
            finally { _uiState.update { it.copy(executingDeviceIds = it.executingDeviceIds - key) } }
            refreshDevices(host)
        }
    }

    private fun executeDeviceShortcut(button: GridButton) {
        val host = _uiState.value.selectedHost ?: return
        val key = "${host.id}:${button.payload["deviceId"]}"
        if (key in _uiState.value.executingDeviceIds) return
        _uiState.update { it.copy(executingDeviceIds = it.executingDeviceIds + key) }
        jobsScope.launch {
            try {
                executeGridButton(button, host)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (error: Throwable) {
                _uiState.update { it.copy(feedback = error.message ?: "Falha no controle do dispositivo") }
            } finally {
                _uiState.update { it.copy(executingDeviceIds = it.executingDeviceIds - key) }
            }
            refreshDevices(host)
        }
    }

    private fun startDevicePolling(host: PairedHost) {
        devicePollJob?.cancel()
        if (host.controlShortcuts.none { it.destination == com.controldesk.domain.model.ShortcutDestination.DEVICE }) return
        devicePollJob = jobsScope.launch {
            while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                refreshDevices(host)
                kotlinx.coroutines.delay(5_000)
            }
        }
    }

    private suspend fun refreshDevices(host: PairedHost) {
        val version = (deviceRefreshVersions[host.id] ?: 0) + 1
        deviceRefreshVersions[host.id] = version
        try {
            val catalog = agentGateway.loadDevices(host)
            if (deviceRefreshVersions[host.id] == version) _uiState.update {
                it.copy(deviceCatalogs = it.deviceCatalogs + (host.id to catalog), deviceErrors = it.deviceErrors - host.id)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Throwable) {
            if (deviceRefreshVersions[host.id] == version) _uiState.update {
                it.copy(deviceErrors = it.deviceErrors + (host.id to (error.message ?: "Agente indisponível")))
            }
        }
    }

    private fun requestWindowSelection(button: GridButton, close: Boolean): Boolean {
        val host = _uiState.value.selectedHost ?: return false
        val windows = windowsByHost[host.id]?.get(button.id).orEmpty()
        if (windows.size < 2) return false
        _uiState.update { it.copy(windowSelection = WindowSelection(host.id, button.id, button.label, windows, close)) }
        return true
    }

    private fun selectWindow(windowId: String) {
        val selection = _uiState.value.windowSelection ?: return
        _uiState.update { it.copy(windowSelection = null) }
        if (_uiState.value.selectedHost?.id != selection.hostId) return
        val window = windowsByHost[selection.hostId]?.get(selection.shortcutId)?.singleOrNull { it.id == windowId }
        val shortcut = _uiState.value.buttons.singleOrNull { it.id == selection.shortcutId }
        if (window == null || shortcut == null) {
            _uiState.update { it.copy(feedback = "Essa janela não está mais disponível. Atualize a lista.") }
            return
        }
        val selected = shortcut.copy(
            payload = (shortcut.payload - RUNNING_WINDOW_PAYLOAD_KEYS - "projectPath") + window.payload,
            actionType = window.actionType,
        )
        if (selection.close) executeAppClose(selected) else executeButtonClick(selected)
    }

    private fun executeButtonClick(button: GridButton) {
        val host = _uiState.value.selectedHost ?: return
        if (isClosing(button)) return
        armPinnedFocus(button)
        _uiState.update { state ->
            state.copy(
                buttons = state.buttons.map {
                    it.copy(isActive = it.id == button.id, isVisible = if (it.id == button.id) true else it.isVisible)
                },
            )
        }
        jobsScope.launch {
            try {
                val apps = runAppCommand(button, host)
                applyAgentApps(host, apps)
            } catch (error: Throwable) {
                clearPinnedFocus()
                _uiState.update { it.copy(feedback = error.message ?: "Falha ao focar app") }
                refreshAppsSilent(host)
            }
        }
        jobsScope.launch {
            startBurstRefresh()
        }
    }

    private suspend fun runAppCommand(button: GridButton, host: PairedHost): List<com.controldesk.domain.model.RunningApp> {
        if (!isRealtimeConnected()) return executeGridButton(button, host)
        return when (button.actionType) {
            ActionType.REMOTE_LAUNCH -> try {
                agentRealtime.launchApp(host, button)
            } catch (_: RealtimeUnavailableException) {
                executeGridButton(button, host)
            }
            ActionType.REMOTE_BROWSER_TAB -> try {
                agentRealtime.switchBrowserTab(host, button)
            } catch (_: RealtimeUnavailableException) {
                executeGridButton(button, host)
            }
            else -> executeGridButton(button, host)
        }
    }

    fun onAppClose(button: GridButton) {
        if (button.payload["controlKind"] != null) return
        if (requestWindowSelection(button, close = true)) return
        executeAppClose(button)
    }

    private fun executeAppClose(button: GridButton) {
        val host = _uiState.value.selectedHost ?: return
        val target = button.payload["target"]?.takeIf { it.isNotBlank() } ?: return
        if (target in BLOCKED_QUIT) {
            _uiState.update { it.copy(feedback = "Não é possível fechar $target") }
            return
        }
        val favoriteShortcutIsIdle = button.isFavoriteShortcut && !button.isVisible && !button.isActive
        val dockShortcutIsIdle = !button.isFavoriteShortcut && button.isDockPinned && !button.isVisible && !button.isActive
        if (favoriteShortcutIsIdle || dockShortcutIsIdle) return
        clearPinnedFocus()
        if (currentActiveId == button.id || button.isActive) {
            currentActiveId = null
            lastIncomingActiveId = null
            incomingActiveStreak = 0
            host.id.let { id ->
                currentActiveIdByHost[id] = null
                lastIncomingActiveIdByHost[id] = null
                incomingActiveStreakByHost[id] = 0
            }
        }
        val key = button.id
        closingUntil[key] = TimeSource.Monotonic.markNow() + CLOSE_HOLD_MS.milliseconds
        val placeholder = button.takeIf { it.isDockPinned }?.let(::dockPlaceholder)
        if (placeholder != null) closingPlaceholders[key] = placeholder else closingPlaceholders.remove(key)
        val state = _uiState.value
        val updatedHost = state.selectedHost?.let { selected ->
            persistClosedDockShortcutLayout(selected, state.buttons, button, placeholder)
        }
        _uiState.update { current ->
            current.copy(
                buttons = replaceClosedApp(current.buttons, button),
                hosts = if (updatedHost == null) current.hosts else current.hosts.map { saved ->
                    if (saved.id == updatedHost.id) updatedHost else saved
                },
            )
        }
        updatedHost?.let { jobsScope.launch { hostPreferences.saveHost(it) } }
        jobsScope.launch {
            try {
                val apps = if (isRealtimeConnected()) {
                    try {
                        agentRealtime.closeApp(host, button)
                    } catch (_: RealtimeUnavailableException) {
                        agentGateway.closeApp(host, button)
                    }
                } else {
                    agentGateway.closeApp(host, button)
                }
                closingUntil.remove(key)
                closingPlaceholders.remove(key)
                applyAgentApps(host, apps)
                if (apps.isEmpty() && !isRealtimeConnected()) refreshAppsSilent(host)
            } catch (error: Throwable) {
                closingUntil.remove(key)
                closingPlaceholders.remove(key)
                _uiState.update { it.copy(feedback = error.message ?: "Falha ao fechar app") }
                refreshAppsSilent(host)
            }
        }
    }

    fun onVolumeDown() = changeVolumeBy(-5)

    fun onVolumeUp() = changeVolumeBy(5)

    fun onVolumeLevelChange(level: Int) {
        val host = _uiState.value.selectedHost ?: return
        val clamped = level.coerceIn(0, 100)
        adjustingVolume = true
        armAudioHold(AUDIO_DRAG_HOLD_MS)
        _uiState.update { it.copy(volumeLevel = clamped, volumeMuted = false) }
        enqueueVolume(host, clamped)
    }

    fun onVolumeAdjustFinished() {
        val host = _uiState.value.selectedHost ?: return
        adjustingVolume = false
        armAudioHold(AUDIO_SETTLE_MS)
        enqueueVolume(host, _uiState.value.volumeLevel)
    }

    fun onVolumeKnobClick() {
        val host = _uiState.value.selectedHost ?: return
        val currentlyMuted = _uiState.value.volumeMuted
        val nextMuted = !currentlyMuted
        val level = _uiState.value.volumeLevel
        armAudioHold(AUDIO_SETTLE_MS)
        _uiState.update { it.copy(volumeMuted = nextMuted) }
        jobsScope.launch {
            runCatching {
                if (nextMuted) {
                    sendVolume(host, mute = true)
                } else {
                    sendVolume(host, level = level, mute = false)
                }
            }.onFailure { error ->
                _uiState.update { it.copy(volumeMuted = currentlyMuted, feedback = error.message ?: "Falha no mute") }
            }
        }
    }

    fun onMicKnobClick() {
        val host = _uiState.value.selectedHost ?: return
        val currentlyMuted = _uiState.value.micMuted
        val nextMuted = !currentlyMuted
        armAudioHold(AUDIO_SETTLE_MS)
        if (nextMuted) {
            lastMicLevel = _uiState.value.micLevel.coerceAtLeast(lastMicLevel)
            _uiState.update { it.copy(micMuted = true) }
        } else {
            val restored = lastMicLevel.coerceIn(25, 100)
            _uiState.update { it.copy(micMuted = false, micLevel = restored) }
        }
        jobsScope.launch {
            runCatching {
                if (nextMuted) {
                    sendMic(host, mute = true, toggle = false)
                } else {
                    sendMic(host, mute = false, toggle = false, level = lastMicLevel.coerceIn(25, 100))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(micMuted = currentlyMuted, feedback = error.message ?: "Falha no microfone") }
            }
        }
    }

    fun onMicLevelChange(level: Int) {
        val host = _uiState.value.selectedHost ?: return
        val clamped = level.coerceIn(0, 100)
        adjustingMic = true
        armAudioHold(AUDIO_DRAG_HOLD_MS)
        if (clamped > 1) lastMicLevel = clamped
        _uiState.update { it.copy(micLevel = clamped, micMuted = clamped <= 1) }
        enqueueMic(host, clamped)
    }

    fun onMicAdjustFinished() {
        val host = _uiState.value.selectedHost ?: return
        adjustingMic = false
        armAudioHold(AUDIO_SETTLE_MS)
        enqueueMic(host, _uiState.value.micLevel)
    }

    private fun enqueueVolume(host: PairedHost, level: Int) {
        pendingVolume = level
        if (volumeSendJob?.isActive == true) return
        volumeSendJob = jobsScope.launch {
            while (true) {
                val next = pendingVolume ?: break
                pendingVolume = null
                runCatching { sendVolume(host, level = next, mute = false) }
                    .onFailure { error ->
                        _uiState.update { it.copy(feedback = error.message ?: "Falha ao ajustar volume") }
                    }
            }
        }
    }

    private fun enqueueMic(host: PairedHost, level: Int) {
        pendingMic = level
        if (micSendJob?.isActive == true) return
        micSendJob = jobsScope.launch {
            while (true) {
                val next = pendingMic ?: break
                pendingMic = null
                runCatching { sendMic(host, level = next) }
                    .onFailure { error ->
                        _uiState.update { it.copy(feedback = error.message ?: "Falha ao ajustar mic") }
                    }
            }
        }
    }

    private fun changeVolumeBy(delta: Int) {
        val next = (_uiState.value.volumeLevel + delta).coerceIn(0, 100)
        onVolumeLevelChange(next)
    }

    private fun loadHost(host: PairedHost, showLoading: Boolean) {
        bindActiveTracking(host.id)
        startDevicePolling(host)
        startRealtime(host)
        pendingHostLoad?.cancel()
        if (showLoading) {
            _uiState.update { it.copy(isLoadingApps = true) }
        }
        pendingHostLoad = jobsScope.launch {
            val cached = buttonsCache[host.id]
            if (!cached.isNullOrEmpty()) {
                restoreCache(host)
            }
            val (appsResult, audioResult, accessibility) = coroutineScope {
                val appsDeferred = async { runCatching { agentGateway.loadRunningApps(host, includeIcons = false) } }
                val audioDeferred = async { runCatching { agentGateway.fetchAudio(host) } }
                val healthDeferred = async { runCatching { agentGateway.fetchHealth(host) }.getOrNull() }
                Triple(appsDeferred.await(), audioDeferred.await(), healthDeferred.await())
            }
            appsResult.fold(
                onSuccess = { apps ->
                    val buttons = RunningAppsGridBuilder.build(apps, memoryIcons)
                    publishButtons(host, buttons, preferMerge = false)
                    val hint = listHintFor(buttons, accessibility)
                    listHintCache[host.id] = hint
                    _uiState.update {
                        it.copy(isLoadingApps = false, listHint = hint, cachedListHints = listHintCache.toMap())
                    }
                    if (buttons.any { it.iconBase64.isNullOrBlank() }) {
                        val withIcons = runCatching { agentGateway.loadRunningApps(host, includeIcons = true) }.getOrNull()
                        if (withIcons != null) {
                            val merged = RunningAppsGridBuilder.merge(
                                current = _uiState.value.buttons,
                                incoming = RunningAppsGridBuilder.build(withIcons, memoryIcons),
                                preferMerge = true,
                            )
                            publishButtons(host, merged, preferMerge = true)
                            persistNewIcons(merged)
                        }
                    }
                    startAutoRefresh()
                },
                onFailure = { error ->
                    startAutoRefresh()
                    handleLoadFailure(host, error, showLoading)
                },
            )
            audioResult.getOrNull()?.let { applyAudio(host, it) }
        }
    }

    private fun handleLoadFailure(host: PairedHost, error: Throwable, showLoading: Boolean) {
        val message = error.message ?: ControlDeskError.AGENT_OFFLINE
        ControlDeskLog.app.e(error) { "loadHost failed host=${host.id}: $message" }
        if (buttonsCache[host.id].isNullOrEmpty() &&
            (host.applicationShortcuts.isNotEmpty() || host.pinnedApps.isNotEmpty())
        ) {
            publishButtons(host, emptyList(), preferMerge = false)
            listHintCache[host.id] = message
        }
        val hasCache = !buttonsCache[host.id].isNullOrEmpty()
        if (hasCache) {
            startRealtime(host)
            _uiState.update {
                it.copy(isLoadingApps = false, listHint = message, feedback = if (showLoading) message else it.feedback)
            }
            return
        }
        stopAutoRefresh()
        listHintCache[host.id] = message
        _uiState.update {
            it.copy(
                buttons = emptyList(),
                isLoadingApps = false,
                listHint = message,
                feedback = if (showLoading) message else it.feedback,
                cachedListHints = listHintCache.toMap(),
            )
        }
    }

    private fun startRealtime(host: PairedHost) {
        if (realtimeHostId == host.id &&
            agentRealtime.connectionState.value != RealtimeConnectionState.Disconnected
        ) {
            return
        }
        realtimeHostId = host.id
        realtimeEventsJob?.cancel()
        agentRealtime.connect(host, jobsScope)
        realtimeEventsJob = jobsScope.launch {
            agentRealtime.events.collect { event ->
                when (event) {
                    is AgentRealtimeEvent.Apps -> applyAgentApps(host, event.apps)
                    is AgentRealtimeEvent.Audio -> applyAudio(host, event.state)
                    is AgentRealtimeEvent.Accessibility -> {
                        val hint = listHintFor(_uiState.value.buttons, event.enabled)
                        listHintCache[host.id] = hint
                        if (_uiState.value.selectedHost?.id == host.id) {
                            _uiState.update { it.copy(listHint = hint, cachedListHints = listHintCache.toMap()) }
                        }
                    }
                }
            }
        }
    }

    private fun stopRealtime() {
        realtimeHostId = null
        realtimeEventsJob?.cancel()
        realtimeEventsJob = null
        agentRealtime.disconnect()
    }

    private fun isRealtimeConnected(): Boolean =
        agentRealtime.connectionState.value == RealtimeConnectionState.Connected

    private suspend fun sendVolume(host: PairedHost, level: Int? = null, delta: Int? = null, mute: Boolean? = null) {
        if (isRealtimeConnected()) {
            agentRealtime.setVolume(host, level = level, delta = delta, mute = mute)
        } else {
            agentGateway.setVolume(host, level = level, delta = delta, mute = mute)
        }
    }

    private suspend fun sendMic(
        host: PairedHost,
        mute: Boolean? = null,
        toggle: Boolean = false,
        level: Int? = null,
    ) {
        if (isRealtimeConnected()) {
            agentRealtime.setMic(host, mute = mute, toggle = toggle, level = level)
        } else {
            agentGateway.setMic(host, mute = mute, toggle = toggle, level = level)
        }
    }

    private suspend fun applyAgentApps(
        host: PairedHost,
        apps: List<com.controldesk.domain.model.RunningApp>,
    ) {
        val buttons = RunningAppsGridBuilder.build(apps, memoryIcons)
        publishButtons(host, buttons, preferMerge = false)
        persistNewIcons(buttons)
    }

    private suspend fun refreshAppsSilent(host: PairedHost) {
        val apps = runCatching { agentGateway.loadRunningApps(host, includeIcons = false) }.getOrNull() ?: return
        val incoming = RunningAppsGridBuilder.build(apps, memoryIcons)
        val baseline = if (_uiState.value.selectedHost?.id == host.id) {
            _uiState.value.buttons
        } else {
            buttonsCache[host.id].orEmpty()
        }
        val preferMerge = baseline.isNotEmpty()
        val merged = RunningAppsGridBuilder.merge(baseline, incoming, preferMerge = preferMerge)
        publishButtons(host, merged, preferMerge = preferMerge)
    }

    private fun publishButtons(host: PairedHost, incoming: List<GridButton>, preferMerge: Boolean) {
        // Realtime collectors can outlive the host snapshot they were created with.
        // Always merge incoming window data with the latest user-owned layout settings.
        val currentHost = _uiState.value.hosts.firstOrNull { it.id == host.id } ?: host
        val favorites = currentHost.pinnedApps.sortedBy { it.order }.mapIndexed { index, app ->
            GridButton(
                id = "favorite-${app.id.hashCode()}",
                label = app.shortcutLabel(),
                actionType = ActionType.REMOTE_LAUNCH,
                payload = buildMap {
                    put("target", app.name)
                    put("targetType", "appName")
                    put("bundlePath", app.bundlePath)
                    put("pinnedAppId", app.id)
                    app.projectPath?.takeIf { it.isNotBlank() }?.let { put("projectPath", it) }
                },
                row = index / 4,
                col = index % 4,
                iconBase64 = app.iconBase64,
                isActive = false,
                isVisible = false,
                isDockPinned = true,
                isFavoriteShortcut = true,
            )
        }
        val runtimeButtons = DockShortcutProjector.runtimeWindows(incoming.filterNot { it.id.startsWith("favorite-") })
        val activeWindowId = runtimeButtons.firstOrNull { it.isActive }?.id
        val normalizedActivity = runtimeButtons.map { it.copy(isActive = it.id == activeWindowId) }
        val projection = DockShortcutProjector.project(currentHost, normalizedActivity)
        val appButtons = projection.runtime
        val binding = ShortcutWindowBinder.bind(favorites, projection.buttons, appButtons, projection.windows)
        windowsByHost[host.id] = binding.windows
        val visible = binding.buttons
        val stableHost = projection.host.copy(
            appOrder = projection.host.appOrder + visible.map { it.id }.filterNot { it in projection.host.appOrder },
        )
        if (stableHost != currentHost) jobsScope.launch { hostPreferences.saveHost(stableHost) }
        val withFavorites = reorderButtons(applyAppTileSizes(visible, stableHost), stableHost.appOrder)
        confirmClosedApps(withFavorites)
        val withPin = applyPinnedFocus(withFavorites)
        val hiddenWhileClosing = applyClosingFilter(withPin)
        buttonsCache[host.id] = hiddenWhileClosing
        val activeId = hiddenWhileClosing.firstOrNull { it.isActive }?.id
        currentActiveIdByHost[host.id] = activeId
        if (!preferMerge) {
            lastIncomingActiveIdByHost[host.id] = activeId
            incomingActiveStreakByHost[host.id] = ACTIVE_CONFIRM_POLLS
        }
        if (_uiState.value.selectedHost?.id == host.id) {
            currentActiveId = activeId
            if (!preferMerge) {
                lastIncomingActiveId = activeId
                incomingActiveStreak = ACTIVE_CONFIRM_POLLS
            }
        }
        _uiState.update {
            val forSelected = it.selectedHost?.id == host.id
            val hint = if (hiddenWhileClosing.isEmpty()) {
                listHintCache[host.id] ?: if (forSelected) it.listHint else null
            } else {
                null
            }
            it.copy(
                buttons = if (forSelected) hiddenWhileClosing else it.buttons,
                hosts = it.hosts.map { savedHost -> if (savedHost.id == host.id) stableHost else savedHost },
                listHint = if (forSelected) hint else it.listHint,
                cachedButtons = buttonsCache.toMap(),
                cachedListHints = listHintCache.toMap(),
            )
        }
    }

    private fun applyAppTileSizes(buttons: List<GridButton>, host: PairedHost): List<GridButton> =
        buttons.map { button ->
            val explicitKey = tileSizeKeys(button).firstOrNull { it in host.appTileSizes }
            val sourceKey = explicitKey
            val size = sourceKey?.let(host.appTileSizes::get) ?: AppTileSize()
            val sizeText = "${size.columns.coerceIn(1, 2)}x${size.rows.coerceIn(1, 2)}"
            val logKey = "${host.id}:${button.id}"
            val logSignature = "$sourceKey:$sizeText:${button.payload["stableWindowId"]}:${button.payload["windowTitle"]}"
            if ((sourceKey != null || lastAppliedTileSizeLog.containsKey(logKey)) &&
                lastAppliedTileSizeLog.put(logKey, logSignature) != logSignature
            ) {
                val stableWindowId = button.payload["stableWindowId"]?.takeIf(String::isNotBlank)
                val appIdentity = button.layoutAppIdentity().orEmpty()
                ControlDeskLog.app.i {
                    "[app-layout] apply host=${host.id} tile=${button.id} label=\"${button.label}\" " +
                        "title=\"${button.payload["windowTitle"].orEmpty()}\" pid=${button.payload["pid"]} " +
                        "stableWindowId=${button.payload["stableWindowId"]} source=${sourceKey ?: "default"} size=$sizeText " +
                        "instancesForApp=${button.payload["instanceCount"] ?: "1"} savedKeys=${host.appTileSizes.keys.filter { key ->
                            (appIdentity.isNotBlank() && key.contains(appIdentity, ignoreCase = true)) ||
                                (stableWindowId != null && key.contains(stableWindowId))
                        }.joinToString()}"
                }
            }
            button.copy(payload = button.payload + mapOf(
                "spanColumns" to size.columns.coerceIn(1, 2).toString(),
                "spanRows" to size.rows.coerceIn(1, 2).toString(),
            ))
        }

    private fun tileSizeKeys(button: GridButton): List<String> = listOf(tileSizeKey(button))

    private fun tileSizeKey(button: GridButton): String =
        button.payload["pinnedAppId"]?.let { "pinned:$it" } ?: "card:${button.id}"

    private fun appLayoutIdentity(button: GridButton): String =
        button.payload["pinnedAppId"]?.let { "pinned:$it" } ?: "app:${button.layoutAppIdentity()}"

    private fun GridButton.layoutAppIdentity(): String? = sequenceOf(
        payload["bundlePath"],
        payload["target"],
    ).mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?.lowercase()

    private fun GridButton.tileSpanColumns(): Int = payload["spanColumns"]?.toIntOrNull()?.coerceIn(1, 2) ?: 1

    private fun GridButton.tileSpanRows(): Int = payload["spanRows"]?.toIntOrNull()?.coerceIn(1, 2) ?: 1

    private fun PinnedAppShortcut.shortcutLabel(): String {
        val projectName = projectPath?.trim()?.takeIf(String::isNotBlank)
            ?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.takeIf(String::isNotBlank)
            ?: return name
        return if (name.contains(projectName, ignoreCase = true)) name else "$name · $projectName"
    }

    private fun reorderButtons(buttons: List<GridButton>, persistedOrder: List<String>): List<GridButton> {
        if (persistedOrder.isEmpty()) return buttons
        val orderById = persistedOrder.withIndex().associate { it.value to it.index }
        return buttons.withIndex()
            .sortedWith(compareBy<IndexedValue<GridButton>> { orderById[it.value.id] ?: Int.MAX_VALUE }.thenBy { it.index })
            .map { it.value }
    }

    private fun bindActiveTracking(hostId: String) {
        if (activeTrackingHostId == hostId) return
        activeTrackingHostId?.let { previous ->
            currentActiveIdByHost[previous] = currentActiveId
            lastIncomingActiveIdByHost[previous] = lastIncomingActiveId
            incomingActiveStreakByHost[previous] = incomingActiveStreak
        }
        activeTrackingHostId = hostId
        currentActiveId = currentActiveIdByHost[hostId]
        lastIncomingActiveId = lastIncomingActiveIdByHost[hostId]
        incomingActiveStreak = incomingActiveStreakByHost[hostId] ?: 0
    }

    private fun applyPinnedFocus(buttons: List<GridButton>): List<GridButton> {
        val pinId = pinnedButtonId ?: return buttons
        if (pinnedUntil?.hasPassedNow() != false) {
            clearPinnedFocus()
            return buttons
        }
        if (buttons.firstOrNull { it.id == pinId }?.isDockPlaceholder() == true) {
            clearPinnedFocus()
            return buttons
        }
        val confirmed = buttons.any { matchesPinned(it) && it.isActive }
        if (confirmed) {
            clearPinnedFocus()
            return buttons
        }
        return buttons.map {
            val match = matchesPinned(it) && !it.isDockPlaceholder()
            it.copy(isActive = match, isVisible = if (match) true else it.isVisible)
        }
    }

    private fun isClosing(button: GridButton): Boolean {
        pruneExpiredCloses()
        return closingUntil.containsKey(button.id)
    }

    private fun pruneExpiredCloses() {
        val expired = closingUntil.filterValues { it.hasPassedNow() }.keys
        expired.forEach {
            closingUntil.remove(it)
            closingPlaceholders.remove(it)
        }
    }

    private fun confirmClosedApps(incoming: List<GridButton>) {
        pruneExpiredCloses()
        closingUntil.keys.toList().forEach { key ->
            val stillRunning = incoming.any { it.id == key && !it.isDockPlaceholder() }
            if (!stillRunning) {
                closingUntil.remove(key)
                closingPlaceholders.remove(key)
            }
        }
    }

    private fun applyClosingFilter(buttons: List<GridButton>): List<GridButton> {
        pruneExpiredCloses()
        if (closingUntil.isEmpty()) return buttons
        val filtered = buttons.filter { button ->
            if (!closingUntil.containsKey(button.id)) return@filter true
            button.isDockPlaceholder()
        }.map { button ->
            if (button.isDockPlaceholder()) button.copy(isActive = false, isVisible = false) else button
        }
        val missing = closingUntil.keys.filter { key ->
            filtered.none { it.id == key }
        }
        if (missing.isEmpty()) return filtered
        val extras = missing.mapNotNull { closingPlaceholders[it] }
        return filtered + extras
    }

    private fun replaceClosedApp(buttons: List<GridButton>, closed: GridButton): List<GridButton> {
        val index = buttons.indexOfFirst { it.id == closed.id }.takeIf { it >= 0 } ?: buttons.size
        val without = buttons.filterNot { it.id == closed.id }
        val placeholder = closingPlaceholders[closed.id] ?: return without
        return without.toMutableList().apply { add(index.coerceAtMost(size), placeholder.copy(isActive = false, isVisible = false)) }
    }

    private fun dockPlaceholder(button: GridButton): GridButton {
        if (button.isFavoriteShortcut || button.payload["applicationShortcutId"] != null) {
            return button.copy(
                payload = button.payload - RUNNING_WINDOW_PAYLOAD_KEYS,
                isActive = false,
                isVisible = false,
                monitorIndex = 0,
                isDockPinned = true,
            )
        }
        val target = button.payload.getValue("target")
        val payload = buildMap {
            put("target", target)
            put("targetType", "appName")
            button.payload["bundlePath"]?.let { put("bundlePath", it) }
            button.payload["spanColumns"]?.let { put("spanColumns", it) }
            button.payload["spanRows"]?.let { put("spanRows", it) }
        }
        return GridButton(
            id = ApplicationIdentity.dockShortcutId(button.payload["bundlePath"], target),
            label = target,
            actionType = ActionType.REMOTE_LAUNCH,
            payload = payload,
            row = button.row,
            col = button.col,
            iconBase64 = button.iconBase64,
            isActive = false,
            isVisible = false,
            monitorIndex = 0,
            isDockPinned = true,
        )
    }

    private fun GridButton.isDockPlaceholder(): Boolean {
        if (id.endsWith("-dock")) return true
        return isDockPinned && !isVisible && payload["windowNumber"] == null && payload["windowIndex"] == null
    }

    private fun GridButton.isPendingDockLaunch(): Boolean =
        id == pinnedButtonId && pinnedUntil?.hasPassedNow() == false && isDockPlaceholder()

    private fun matchesPinned(button: GridButton): Boolean {
        if (button.id == pinnedButtonId) return true
        val windowNumber = button.payload["windowNumber"]
        if (pinnedWindowNumber != null && windowNumber == pinnedWindowNumber) return true
        val target = button.payload["target"] ?: button.payload["value"]
        val title = button.payload["windowTitle"] ?: button.payload["value"]
        val pinned = pinnedTarget ?: return false
        return target != null && title != null && pinned == "$target+$title"
    }

    private fun armPinnedFocus(button: GridButton) {
        pinnedButtonId = button.id
        pinnedWindowNumber = button.payload["windowNumber"]
        val target = button.payload["target"] ?: button.payload["value"]
        val title = button.payload["windowTitle"] ?: button.payload["value"]
        pinnedTarget = if (target != null && title != null) "$target+$title" else null
        pinnedUntil = TimeSource.Monotonic.markNow() + PINNED_FOCUS_MAX_MS.milliseconds
        currentActiveId = button.id
        _uiState.value.selectedHost?.id?.let { currentActiveIdByHost[it] = button.id }
    }

    private fun clearPinnedFocus() {
        pinnedButtonId = null
        pinnedWindowNumber = null
        pinnedTarget = null
        pinnedUntil = null
    }

    private fun restoreCache(host: PairedHost) {
        bindActiveTracking(host.id)
        val buttons = buttonsCache[host.id].orEmpty()
        val cachedActive = buttons.firstOrNull { it.isActive && !it.isDockPlaceholder() }?.id
        if (cachedActive != null) {
            currentActiveId = cachedActive
            currentActiveIdByHost[host.id] = cachedActive
            lastIncomingActiveId = cachedActive
            lastIncomingActiveIdByHost[host.id] = cachedActive
            incomingActiveStreak = ACTIVE_CONFIRM_POLLS
            incomingActiveStreakByHost[host.id] = ACTIVE_CONFIRM_POLLS
        }
        val audio = audioCache[host.id]
        _uiState.update {
            it.copy(
                buttons = buttons,
                listHint = listHintCache[host.id],
                volumeLevel = audio?.volumeLevel ?: it.volumeLevel,
                volumeMuted = audio?.volumeMuted ?: it.volumeMuted,
                micMuted = audio?.micMuted ?: it.micMuted,
                micLevel = audio?.micLevel?.takeIf { level -> level > 1 } ?: it.micLevel,
                isLoadingApps = buttons.isEmpty(),
                cachedButtons = buttonsCache.toMap(),
                cachedListHints = listHintCache.toMap(),
            )
        }
    }

    private fun applyAudio(host: PairedHost, audio: AudioState) {
        if (audio.micLevel > 1) lastMicLevel = audio.micLevel
        audioCache[host.id] = audio
        if (_uiState.value.selectedHost?.id != host.id) return
        val holdActive = holdingAudioUntil?.hasPassedNow() == false
        val updateVolume = !adjustingVolume && !holdActive
        val updateMic = !adjustingMic && !holdActive
        if (!updateVolume && !updateMic) return
        _uiState.update { current ->
            val nextMic = if (audio.micMuted) {
                current.micLevel.takeIf { it > 1 } ?: lastMicLevel
            } else {
                audio.micLevel
            }
            current.copy(
                volumeLevel = when {
                    !updateVolume -> current.volumeLevel
                    abs(current.volumeLevel - audio.volumeLevel) <= 1 -> current.volumeLevel
                    else -> audio.volumeLevel
                },
                volumeMuted = if (updateVolume) audio.volumeMuted else current.volumeMuted,
                micMuted = if (updateMic) audio.micMuted else current.micMuted,
                micLevel = when {
                    !updateMic -> current.micLevel
                    audio.micMuted -> current.micLevel
                    abs(current.micLevel - nextMic) <= 1 -> current.micLevel
                    else -> nextMic
                },
            )
        }
    }

    private fun listHintFor(buttons: List<GridButton>, accessibilityEnabled: Boolean?): String? {
        if (accessibilityEnabled == false) return ControlDeskError.ACCESSIBILITY_OFF
        if (buttons.isEmpty()) return ControlDeskError.EMPTY_WINDOWS
        return null
    }

    private fun persistNewIcons(buttons: List<GridButton>) {
        jobsScope.launch {
            buttons.forEach { button ->
                val icon = button.iconBase64
                if (!icon.isNullOrBlank() && iconCache.peek(button.id) != icon) {
                    iconCache.replace(button.id, icon)
                }
            }
            memoryIcons = iconCache.load()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        audioPollJob?.cancel()
        audioTick = 0
        autoRefreshJob = jobsScope.launch {
            while (true) {
                val host = _uiState.value.selectedHost ?: break
                if (!isRealtimeConnected()) {
                    refreshAppsSilent(host)
                    audioTick += 1
                    if (audioTick % MISSING_ICON_CHECK_EVERY == 0) {
                        refreshMissingIconsIfNeeded(host)
                    }
                } else {
                    audioTick += 1
                    if (audioTick % MISSING_ICON_CHECK_EVERY == 0) {
                        refreshMissingIconsIfNeeded(host)
                    }
                }
                delay(if (isRealtimeConnected()) FALLBACK_POLL_MS else AUTO_REFRESH_MS)
            }
        }
        audioPollJob = jobsScope.launch {
            while (true) {
                delay(if (isRealtimeConnected()) FALLBACK_AUDIO_POLL_MS else AUDIO_REFRESH_MS)
                val host = _uiState.value.selectedHost ?: continue
                if (isRealtimeConnected()) continue
                if (holdingAudioUntil?.hasPassedNow() == false || adjustingVolume || adjustingMic) continue
                runCatching { agentGateway.fetchAudio(host) }.getOrNull()?.let { applyAudio(host, it) }
            }
        }
    }

    private suspend fun refreshMissingIconsIfNeeded(host: PairedHost) {
        if (_uiState.value.buttons.none { it.iconBase64.isNullOrBlank() }) return
        val withIcons = runCatching { agentGateway.loadRunningApps(host, includeIcons = true) }.getOrNull() ?: return
        val merged = RunningAppsGridBuilder.merge(
            current = _uiState.value.buttons,
            incoming = RunningAppsGridBuilder.build(withIcons, memoryIcons),
            preferMerge = true,
        )
        publishButtons(host, merged, preferMerge = true)
        persistNewIcons(merged)
    }

    private fun startBurstRefresh() {
        if (isRealtimeConnected()) return
        burstJob?.cancel()
        burstJob = jobsScope.launch {
            val host = _uiState.value.selectedHost ?: return@launch
            refreshAppsSilent(host)
            repeat(BURST_REFRESH_COUNT) {
                delay(BURST_REFRESH_MS)
                refreshAppsSilent(host)
            }
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        audioPollJob?.cancel()
        audioPollJob = null
        burstJob?.cancel()
        stopRealtime()
    }

    private fun armAudioHold(ms: Long = AUDIO_DRAG_HOLD_MS) {
        holdingAudioUntil = TimeSource.Monotonic.markNow() + ms.milliseconds
    }

    private companion object {
        val RUNNING_WINDOW_PAYLOAD_KEYS = ShortcutWindowBinder.runtimePayloadKeys
        const val AUTO_REFRESH_MS = 150L
        const val FALLBACK_POLL_MS = 5_000L
        const val AUDIO_REFRESH_MS = 300L
        const val FALLBACK_AUDIO_POLL_MS = 8_000L
        const val AUDIO_DRAG_HOLD_MS = 500L
        const val AUDIO_SETTLE_MS = 1_200L
        const val BURST_REFRESH_MS = 80L
        const val BURST_REFRESH_COUNT = 3
        const val PINNED_FOCUS_MAX_MS = 1200L
        const val ACTIVE_CONFIRM_POLLS = 1
        const val HOST_SWITCH_SETTLE_MS = 0L
        const val MISSING_ICON_CHECK_EVERY = 5
        const val CLOSE_HOLD_MS = 1_500L
        val BLOCKED_QUIT = setOf(
            "Finder", "Dock", "SystemUIServer", "Window Server", "Control Center",
            "NotificationCenter", "loginwindow", "Wallpaper", "Spotlight",
            "ControlDeskAgent", "Control Desk Agent",
        )
    }
}
