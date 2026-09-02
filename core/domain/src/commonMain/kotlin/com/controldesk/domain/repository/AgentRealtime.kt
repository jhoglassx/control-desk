package com.controldesk.domain.repository

import com.controldesk.domain.model.AgentRealtimeEvent
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RealtimeConnectionState
import com.controldesk.domain.model.RunningApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface AgentRealtime {
    val connectionState: StateFlow<RealtimeConnectionState>
    val events: Flow<AgentRealtimeEvent>
    fun connect(host: PairedHost, scope: CoroutineScope)
    fun disconnect()
    suspend fun launchApp(host: PairedHost, button: GridButton): List<RunningApp>
    suspend fun closeApp(host: PairedHost, button: GridButton): List<RunningApp>
    suspend fun switchBrowserTab(host: PairedHost, button: GridButton): List<RunningApp>
    suspend fun setVolume(host: PairedHost, level: Int?, delta: Int?, mute: Boolean?)
    suspend fun setMic(host: PairedHost, mute: Boolean?, toggle: Boolean, level: Int?)
}
