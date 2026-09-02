package com.controldesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controldesk.data.RunningAppsGridBuilder
import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.AgentRealtimeEvent
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RealtimeConnectionState
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.domain.repository.AgentRealtime
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.repository.RunningAppIconCache
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.logging.ControlDeskLog
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(DeskUiState())
    val uiState: StateFlow<DeskUiState> = _uiState.asStateFlow()

    private val buttonsCache = mutableMapOf<String, List<GridButton>>()
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
        viewModelScope.launch {
            memoryIcons = iconCache.load()
        }
        viewModelScope.launch {
            hostPreferences.observeHosts().collect { hosts ->
                val sorted = hosts.sortedBy { it.sortOrder }
                if (sorted.isEmpty()) {
                    stopAutoRefresh()
                    _uiState.value = DeskUiState()
                    return@collect
                }
                val previousId = _uiState.value.selectedHost?.id
                val index = sorted.indexOfFirst { it.id == previousId }.takeIf { it >= 0 } ?: 0
                _uiState.update { it.copy(hosts = sorted, selectedHostIndex = index) }
                loadHost(sorted[index], showLoading = previousId == null)
            }
        }
    }

    fun refreshHosts() {
        viewModelScope.launch {
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
        _uiState.update { it.copy(selectedHostIndex = index) }
        loadHost(host, showLoading = buttonsCache[host.id].isNullOrEmpty())
    }

    fun refreshRunningApps() {
        val host = _uiState.value.selectedHost ?: return
        loadHost(host, showLoading = true)
    }

    fun clearFeedback() {
        _uiState.update { it.copy(feedback = null) }
    }

    fun onButtonClick(button: GridButton) {
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
        viewModelScope.launch {
            try {
                val apps = runAppCommand(button, host)
                applyAgentApps(host, apps)
            } catch (error: Throwable) {
                clearPinnedFocus()
                _uiState.update { it.copy(feedback = error.message ?: "Falha ao focar app") }
                refreshAppsSilent(host)
            }
        }
        viewModelScope.launch {
            startBurstRefresh()
        }
    }

    private suspend fun runAppCommand(button: GridButton, host: PairedHost): List<com.controldesk.domain.model.RunningApp> {
        if (!isRealtimeConnected()) return executeGridButton(button, host)
        return runCatching {
            when (button.actionType) {
                ActionType.REMOTE_LAUNCH -> agentRealtime.launchApp(host, button)
                ActionType.REMOTE_BROWSER_TAB -> agentRealtime.switchBrowserTab(host, button)
                else -> executeGridButton(button, host)
            }
        }.getOrElse { executeGridButton(button, host) }
    }

    fun onAppClose(button: GridButton) {
        val host = _uiState.value.selectedHost ?: return
        val target = button.payload["target"]?.takeIf { it.isNotBlank() } ?: return
        if (target in BLOCKED_QUIT) {
            _uiState.update { it.copy(feedback = "Não é possível fechar $target") }
            return
        }
        if (button.isDockPinned && !button.isVisible && !button.isActive) return
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
        if (button.isDockPinned) {
            closingPlaceholders[key] = dockPlaceholder(button)
        } else {
            closingPlaceholders.remove(key)
        }
        _uiState.update { state ->
            state.copy(buttons = replaceClosedApp(state.buttons, button))
        }
        viewModelScope.launch {
            try {
                val apps = if (isRealtimeConnected()) {
                    runCatching { agentRealtime.closeApp(host, button) }
                        .getOrElse { agentGateway.closeApp(host, button) }
                } else {
                    agentGateway.closeApp(host, button)
                }
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        volumeSendJob = viewModelScope.launch {
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
        micSendJob = viewModelScope.launch {
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
        startRealtime(host)
        pendingHostLoad?.cancel()
        pendingHostLoad = viewModelScope.launch {
            val cached = buttonsCache[host.id]
            if (showLoading) {
                _uiState.update { it.copy(isLoadingApps = true) }
            }
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
        val hasCache = !buttonsCache[host.id].isNullOrEmpty()
        if (hasCache) {
            startRealtime(host)
            _uiState.update {
                it.copy(isLoadingApps = false, feedback = if (showLoading) message else it.feedback)
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

    private var realtimeHostId: String? = null

    private fun startRealtime(host: PairedHost) {
        if (realtimeHostId == host.id &&
            agentRealtime.connectionState.value != RealtimeConnectionState.Disconnected
        ) {
            return
        }
        realtimeHostId = host.id
        realtimeEventsJob?.cancel()
        agentRealtime.connect(host, viewModelScope)
        realtimeEventsJob = viewModelScope.launch {
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

    private suspend fun applyAgentApps(host: PairedHost, apps: List<com.controldesk.domain.model.RunningApp>) {
        if (apps.isEmpty()) return
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
        confirmClosedApps(incoming)
        val stabilized = stabilizeActiveState(host.id, incoming)
        val withPin = applyPinnedFocus(stabilized)
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
                listHint = if (forSelected) hint else it.listHint,
                cachedButtons = buttonsCache.toMap(),
                cachedListHints = listHintCache.toMap(),
            )
        }
    }

    private fun stabilizeActiveState(hostId: String, incoming: List<GridButton>): List<GridButton> {
        val incomingActiveId = incoming.firstOrNull { it.isActive && !it.isDockPlaceholder() }?.id
        var current = currentActiveIdByHost[hostId]
        var last = lastIncomingActiveIdByHost[hostId]
        var streak = incomingActiveStreakByHost[hostId] ?: 0
        if (activeTrackingHostId == hostId) {
            current = currentActiveId
            last = lastIncomingActiveId
            streak = incomingActiveStreak
        }

        val highlightId = when {
            incomingActiveId != null && incomingActiveId == current -> incomingActiveId
            incomingActiveId == null -> {
                last = null
                streak = 0
                current = null
                null
            }
            else -> {
                if (incomingActiveId == last) streak += 1 else {
                    last = incomingActiveId
                    streak = 1
                }
                if (streak >= ACTIVE_CONFIRM_POLLS) {
                    current = incomingActiveId
                    incomingActiveId
                } else {
                    current
                }
            }
        }

        currentActiveIdByHost[hostId] = current
        lastIncomingActiveIdByHost[hostId] = last
        incomingActiveStreakByHost[hostId] = streak
        if (activeTrackingHostId == hostId || _uiState.value.selectedHost?.id == hostId) {
            currentActiveId = current
            lastIncomingActiveId = last
            incomingActiveStreak = streak
        }
        return normalizeActiveHighlight(incoming, highlightId)
    }

    private fun normalizeActiveHighlight(buttons: List<GridButton>, activeId: String?): List<GridButton> =
        buttons.map { button ->
            button.copy(isActive = activeId != null && !button.isDockPlaceholder() && button.id == activeId)
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
        val target = button.payload.getValue("target")
        return GridButton(
            id = "app-$target-dock",
            label = target,
            actionType = ActionType.REMOTE_LAUNCH,
            payload = mapOf("target" to target, "targetType" to "appName"),
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
        viewModelScope.launch {
            buttons.forEach { button ->
                val icon = button.iconBase64
                if (!icon.isNullOrBlank() && iconCache.peek(button.id) == null) {
                    iconCache.putIfAbsent(button.id, icon)
                }
            }
            memoryIcons = iconCache.load()
        }
    }

    private fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        audioPollJob?.cancel()
        audioTick = 0
        autoRefreshJob = viewModelScope.launch {
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
        audioPollJob = viewModelScope.launch {
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
        burstJob = viewModelScope.launch {
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
