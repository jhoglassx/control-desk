package com.controldesk.domain.model

sealed interface AgentRealtimeEvent {
    data class Apps(val apps: List<RunningApp>) : AgentRealtimeEvent
    data class Audio(val state: AudioState) : AgentRealtimeEvent
    data class Accessibility(val enabled: Boolean?) : AgentRealtimeEvent
}

enum class RealtimeConnectionState {
    Disconnected,
    Connecting,
    Connected,
}
