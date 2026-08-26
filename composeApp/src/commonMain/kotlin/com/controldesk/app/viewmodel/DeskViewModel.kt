package com.controldesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controldesk.data.RunningAppsGridBuilder
import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.repository.RunningAppIconCache
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

class DeskViewModel(
    private val hostPreferences: HostPreferences,
    private val agentGateway: AgentGateway,
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
    private var burstJob: Job? = null
    private var audioTick = 0
    private var holdingAudioUntil: TimeMark? = null
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
    private var lastIncomingActiveId: String? = null
    private var incomingActiveStreak = 0
    private var currentActiveId: String? = null

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
                executeGridButton(button, host)
                delay(FOCUS_SETTLE_MS)
                repeat(FOCUS_CONFIRM_POLLS) {
                    refreshAppsSilent(host)
                    delay(FOCUS_SETTLE_MS)
                }
            } catch (error: Throwable) {
                clearPinnedFocus()
                _uiState.update { it.copy(feedback = error.message ?: "Falha ao focar app") }
                refreshAppsSilent(host)
            }
        }
        viewModelScope.launch {
            delay(COMMAND_FIRST_MS)
            startBurstRefresh()
        }
    }

    fun onVolumeDown() = changeVolumeBy(-5)

    fun onVolumeUp() = changeVolumeBy(5)

    fun onVolumeLevelChange(level: Int) {
        val host = _uiState.value.selectedHost ?: return
        val clamped = level.coerceIn(0, 100)
        armAudioHold()
        _uiState.update { it.copy(volumeLevel = clamped, volumeMuted = false) }
        enqueueVolume(host, clamped)
    }

    fun onVolumeAdjustFinished() {
        val host = _uiState.value.selectedHost ?: return
        enqueueVolume(host, _uiState.value.volumeLevel)
    }

    fun onVolumeKnobClick() {
        val host = _uiState.value.selectedHost ?: return
        val currentlyMuted = _uiState.value.volumeMuted
        val nextMuted = !currentlyMuted
        val level = _uiState.value.volumeLevel
        armAudioHold()
        _uiState.update { it.copy(volumeMuted = nextMuted) }
        viewModelScope.launch {
            runCatching {
                if (nextMuted) {
                    agentGateway.setVolume(host, mute = true)
                } else {
                    agentGateway.setVolume(host, level = level, mute = false)
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
        armAudioHold()
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
                    agentGateway.setMic(host, mute = true, toggle = false)
                } else {
                    agentGateway.setMic(host, mute = false, toggle = false, level = lastMicLevel.coerceIn(25, 100))
                }
            }.onFailure { error ->
                _uiState.update { it.copy(micMuted = currentlyMuted, feedback = error.message ?: "Falha no microfone") }
            }
        }
    }

    fun onMicLevelChange(level: Int) {
        val host = _uiState.value.selectedHost ?: return
        val clamped = level.coerceIn(0, 100)
        armAudioHold()
        if (clamped > 1) lastMicLevel = clamped
        _uiState.update { it.copy(micLevel = clamped, micMuted = clamped <= 1) }
        enqueueMic(host, clamped)
    }

    fun onMicAdjustFinished() {
        val host = _uiState.value.selectedHost ?: return
        enqueueMic(host, _uiState.value.micLevel)
    }

    private fun enqueueVolume(host: PairedHost, level: Int) {
        pendingVolume = level
        if (volumeSendJob?.isActive == true) return
        volumeSendJob = viewModelScope.launch {
            while (true) {
                val next = pendingVolume ?: break
                pendingVolume = null
                runCatching { agentGateway.setVolume(host, level = next, mute = false) }
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
                runCatching { agentGateway.setMic(host, level = next) }
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
        pendingHostLoad?.cancel()
        pendingHostLoad = viewModelScope.launch {
            val cached = buttonsCache[host.id]
            if (showLoading) {
                _uiState.update { it.copy(isLoadingApps = true) }
            }
            if (!cached.isNullOrEmpty()) {
                restoreCache(host)
                if (!showLoading) delay(HOST_SWITCH_SETTLE_MS)
            }
            val (appsResult, audioResult) = coroutineScope {
                val appsDeferred = async { runCatching { agentGateway.loadRunningApps(host, includeIcons = false) } }
                val audioDeferred = async { runCatching { agentGateway.fetchAudio(host) } }
                appsDeferred.await() to audioDeferred.await()
            }
            appsResult.fold(
                onSuccess = { apps ->
                    val buttons = RunningAppsGridBuilder.build(apps, memoryIcons)
                    publishButtons(host, buttons, preferMerge = false)
                    _uiState.update { it.copy(isLoadingApps = false, listHint = if (buttons.isEmpty()) ControlDeskError.EMPTY_WINDOWS else null) }
                    cacheListHint(host)
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
                    handleLoadFailure(host, error, showLoading)
                },
            )
            audioResult.getOrNull()?.let { applyAudio(host, it) }
        }
    }

    private fun handleLoadFailure(host: PairedHost, error: Throwable, showLoading: Boolean) {
        val message = error.message ?: ControlDeskError.AGENT_OFFLINE
        val hasCache = !buttonsCache[host.id].isNullOrEmpty()
        if (hasCache) {
            _uiState.update {
                it.copy(isLoadingApps = false, feedback = if (showLoading) message else it.feedback)
            }
            return
        }
        stopAutoRefresh()
        _uiState.update {
            it.copy(
                buttons = emptyList(),
                isLoadingApps = false,
                listHint = message,
                feedback = if (showLoading) message else it.feedback,
            )
        }
    }

    private suspend fun refreshAppsSilent(host: PairedHost) {
        val apps = runCatching { agentGateway.loadRunningApps(host, includeIcons = false) }.getOrNull() ?: return
        val incoming = RunningAppsGridBuilder.build(apps, memoryIcons)
        val merged = RunningAppsGridBuilder.merge(_uiState.value.buttons, incoming, preferMerge = true)
        publishButtons(host, merged, preferMerge = true)
    }

    private fun publishButtons(host: PairedHost, incoming: List<GridButton>, preferMerge: Boolean) {
        val stabilized = stabilizeActiveState(incoming)
        val withPin = applyPinnedFocus(stabilized)
        buttonsCache[host.id] = withPin
        if (_uiState.value.selectedHost?.id == host.id) {
            _uiState.update {
                it.copy(
                    buttons = withPin,
                    listHint = if (withPin.isEmpty()) (listHintCache[host.id] ?: ControlDeskError.EMPTY_WINDOWS) else null,
                )
            }
        }
        currentActiveId = withPin.firstOrNull { it.isActive }?.id
        if (!preferMerge) {
            lastIncomingActiveId = currentActiveId
            incomingActiveStreak = ACTIVE_CONFIRM_POLLS
        }
    }

    private fun stabilizeActiveState(incoming: List<GridButton>): List<GridButton> {
        val incomingActiveId = incoming.firstOrNull { it.isActive }?.id
        if (incomingActiveId == currentActiveId || incomingActiveId == null) {
            lastIncomingActiveId = incomingActiveId
            incomingActiveStreak = 0
            return incoming.map { button ->
                button.copy(isActive = button.id == currentActiveId)
            }.let { buttons ->
                if (currentActiveId == null) incoming else buttons
            }
        }
        if (incomingActiveId == lastIncomingActiveId) {
            incomingActiveStreak += 1
        } else {
            lastIncomingActiveId = incomingActiveId
            incomingActiveStreak = 1
        }
        return if (incomingActiveStreak >= ACTIVE_CONFIRM_POLLS) {
            currentActiveId = incomingActiveId
            incoming
        } else {
            incoming.map { it.copy(isActive = it.id == currentActiveId) }
        }
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
            val match = matchesPinned(it)
            it.copy(isActive = match, isVisible = if (match) true else it.isVisible)
        }
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
    }

    private fun clearPinnedFocus() {
        pinnedButtonId = null
        pinnedWindowNumber = null
        pinnedTarget = null
        pinnedUntil = null
    }

    private fun restoreCache(host: PairedHost) {
        val buttons = buttonsCache[host.id].orEmpty()
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
            )
        }
    }

    private fun applyAudio(host: PairedHost, audio: AudioState) {
        if (holdingAudioUntil?.hasPassedNow() == false) return
        audioCache[host.id] = audio
        if (audio.micLevel > 1) lastMicLevel = audio.micLevel
        if (_uiState.value.selectedHost?.id == host.id) {
            _uiState.update {
                it.copy(
                    volumeLevel = audio.volumeLevel,
                    volumeMuted = audio.volumeMuted,
                    micMuted = audio.micMuted,
                    micLevel = if (audio.micMuted) it.micLevel.takeIf { level -> level > 1 } ?: lastMicLevel else audio.micLevel,
                )
            }
        }
    }

    private fun cacheListHint(host: PairedHost) {
        listHintCache[host.id] = _uiState.value.listHint
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
                delay(AUTO_REFRESH_MS)
                val host = _uiState.value.selectedHost ?: continue
                refreshAppsSilent(host)
                audioTick += 1
                if (audioTick % MISSING_ICON_CHECK_EVERY == 0) {
                    refreshMissingIconsIfNeeded(host)
                }
            }
        }
        audioPollJob = viewModelScope.launch {
            while (true) {
                delay(AUDIO_REFRESH_MS)
                val host = _uiState.value.selectedHost ?: continue
                if (holdingAudioUntil?.hasPassedNow() == false) continue
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
    }

    private fun armAudioHold() {
        holdingAudioUntil = TimeSource.Monotonic.markNow() + AUDIO_HOLD_MS.milliseconds
    }

    private companion object {
        const val AUTO_REFRESH_MS = 400L
        const val AUDIO_REFRESH_MS = 400L
        const val AUDIO_HOLD_MS = 400L
        const val BURST_REFRESH_MS = 200L
        const val BURST_REFRESH_COUNT = 5
        const val PINNED_FOCUS_MAX_MS = 8000L
        const val ACTIVE_CONFIRM_POLLS = 1
        const val FOCUS_SETTLE_MS = 250L
        const val FOCUS_CONFIRM_POLLS = 3
        const val HOST_SWITCH_SETTLE_MS = 180L
        const val MISSING_ICON_CHECK_EVERY = 2
        const val COMMAND_FIRST_MS = 40L
    }
}
