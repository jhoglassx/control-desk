package com.controldesk.domain.usecase

import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.DeviceCommand
import com.controldesk.domain.model.DeviceOperation
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.AgentGateway

/** Resolves toggle against a fresh agent snapshot, then sends one explicit desired state. */
class ExecuteDeviceShortcutUseCase(private val gateway: AgentGateway) {
    suspend operator fun invoke(button: GridButton, host: PairedHost) {
        val id = button.payload["deviceId"]?.takeIf { it.isNotBlank() } ?: fail("Selecione um dispositivo")
        val operation = DeviceOperation.entries.singleOrNull { it.name == button.payload["deviceOperation"] }
            ?: fail("Ação de dispositivo inválida")
        val device = gateway.loadDevices(host).devices.singleOrNull { it.id == id }
            ?: fail("Dispositivo desconectado ou indisponível; nenhum outro foi selecionado.")
        if (operation.capability !in device.capabilities) fail("Ação indisponível neste dispositivo ou sem permissão.")
        val value = when (operation) {
            DeviceOperation.ENABLE, DeviceOperation.MUTE -> true
            DeviceOperation.DISABLE, DeviceOperation.UNMUTE -> false
            DeviceOperation.TOGGLE_ENABLED -> device.enabled?.not() ?: fail("Estado da câmera desconhecido")
            DeviceOperation.TOGGLE_MUTED -> device.muted?.not() ?: fail("Estado de mute desconhecido")
            else -> null
        }
        val level = if (operation == DeviceOperation.SET_LEVEL) {
            button.payload["deviceLevel"]?.toIntOrNull()?.takeIf { it in 0..100 } ?: fail("Nível inválido")
        } else null
        gateway.executeDeviceCommand(host, DeviceCommand(id, operation.capability, value, level))
    }
    private fun fail(message: String): Nothing = throw ControlDeskError.Generic(message)
}
