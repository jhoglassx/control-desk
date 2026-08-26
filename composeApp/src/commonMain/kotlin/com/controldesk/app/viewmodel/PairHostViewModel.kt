package com.controldesk.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.usecase.PairHostUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PairHostUiState(
    val name: String = "",
    val host: String = "10.0.2.2",
    val port: String = "8741",
    val pin: String = "",
    val osType: OsType = OsType.MACOS,
    val isPairing: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    val hosts: List<PairedHost> = emptyList(),
)

class PairHostViewModel(
    private val pairHost: PairHostUseCase,
    private val hostPreferences: HostPreferences,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairHostUiState())
    val uiState: StateFlow<PairHostUiState> = _uiState.asStateFlow()

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
}
