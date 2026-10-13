package com.controldesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.AgentDiscovery
import com.controldesk.domain.repository.DiscoveredAgent
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.usecase.PairHostUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

data class PairHostUiState(
    val name: String = "",
    val host: String = "",
    val port: String = "8741",
    val pin: String = "",
    val osType: OsType = OsType.MACOS,
    val isPairing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val hosts: List<PairedHost> = emptyList(),
    val discoveredAgents: List<DiscoveredAgent> = emptyList(),
    val isDiscovering: Boolean = false,
)

class PairHostViewModel(
    private val pairHost: PairHostUseCase,
    private val hostPreferences: HostPreferences,
    private val agentDiscovery: AgentDiscovery,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairHostUiState())
    val uiState: StateFlow<PairHostUiState> = _uiState.asStateFlow()
    private var discovery: AutoCloseable? = null

    init {
        viewModelScope.launch {
            hostPreferences.observeHosts().collect { hosts ->
                _uiState.update { it.copy(hosts = hosts.sortedBy { host -> host.sortOrder }) }
            }
        }
    }

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value, errorMessage = null) }
    fun onHostChange(value: String) = _uiState.update { it.copy(host = value, errorMessage = null) }
    fun onPortChange(value: String) = _uiState.update { it.copy(port = value, errorMessage = null) }
    fun onPinChange(value: String) = _uiState.update { it.copy(pin = value, errorMessage = null) }
    fun onOsTypeChange(value: OsType) = _uiState.update { it.copy(osType = value) }

    fun discoverAgents() {
        discovery?.close()
        _uiState.update { it.copy(isDiscovering = true, discoveredAgents = emptyList(), errorMessage = null) }
        discovery = runCatching {
            agentDiscovery.start { agents -> _uiState.update { it.copy(discoveredAgents = agents) } }
        }.onFailure { error ->
            _uiState.update { it.copy(isDiscovering = false, errorMessage = error.message ?: "Falha ao buscar Macs na rede") }
        }.getOrNull()
        viewModelScope.launch {
            delay(DISCOVERY_WINDOW_MS)
            discovery?.close()
            discovery = null
            _uiState.update { it.copy(isDiscovering = false) }
        }
    }

    fun selectDiscoveredAgent(agent: DiscoveredAgent) {
        discovery?.close()
        discovery = null
        _uiState.update {
            it.copy(
                name = agent.name,
                host = agent.host,
                port = agent.port.toString(),
                osType = OsType.MACOS,
                isDiscovering = false,
                errorMessage = null,
            )
        }
    }

    fun pair(onPaired: () -> Unit) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isPairing = true, errorMessage = null, successMessage = null) }
            val result = pairHost(state.name, state.host, state.port, state.pin, state.osType)
            result.fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isPairing = false,
                            name = "",
                            pin = "",
                            successMessage = "Pareado com sucesso",
                        )
                    }
                    onPaired()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(isPairing = false, errorMessage = error.message ?: "Falha no pareamento")
                    }
                },
            )
        }
    }

    fun removeHost(hostId: String) {
        viewModelScope.launch {
            hostPreferences.removeHost(hostId)
        }
    }

    private companion object {
        const val DISCOVERY_WINDOW_MS = 8_000L
    }
}
