package com.controldesk.testing

import com.controldesk.domain.model.AgentRealtimeEvent
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RealtimeConnectionState
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.repository.AgentRealtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAgentRealtime(
    var apps: List<RunningApp> = emptyList(),
) : AgentRealtime {
    private val _connectionState = MutableStateFlow(RealtimeConnectionState.Disconnected)
    override val connectionState: StateFlow<RealtimeConnectionState> = _connectionState.asStateFlow()

    private val _events = MutableSharedFlow<AgentRealtimeEvent>(extraBufferCapacity = 16)
    override val events: Flow<AgentRealtimeEvent> = _events.asSharedFlow()

    val launched = mutableListOf<GridButton>()
    val closed = mutableListOf<GridButton>()

    override fun connect(host: PairedHost, scope: CoroutineScope) {
        _connectionState.value = RealtimeConnectionState.Connected
    }

    override fun disconnect() {
        _connectionState.value = RealtimeConnectionState.Disconnected
    }

    override suspend fun launchApp(host: PairedHost, button: GridButton): List<RunningApp> {
        launched += button
        return apps
    }

    override suspend fun closeApp(host: PairedHost, button: GridButton): List<RunningApp> {
        closed += button
        return apps
    }

    override suspend fun switchBrowserTab(host: PairedHost, button: GridButton): List<RunningApp> {
        launched += button
        return apps
    }

    override suspend fun setVolume(host: PairedHost, level: Int?, delta: Int?, mute: Boolean?) = Unit

    override suspend fun setMic(host: PairedHost, mute: Boolean?, toggle: Boolean, level: Int?) = Unit

    suspend fun emitApps(apps: List<RunningApp>) {
        _events.emit(AgentRealtimeEvent.Apps(apps))
    }
}
