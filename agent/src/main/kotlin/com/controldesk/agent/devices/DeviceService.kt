package com.controldesk.agent.devices

import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceActionResponse
import com.controldesk.protocol.DeviceCatalogResponse
import com.controldesk.protocol.DeviceInfo

interface DeviceAdapter {
    val id: String
    fun discover(): List<DeviceInfo>
    fun execute(device: DeviceInfo, request: DeviceActionRequest)
}

/** Device IDs are resolved again for every command; missing devices never fall back to a default. */
class DeviceService(private val adapters: List<DeviceAdapter>) {
    fun catalog(): DeviceCatalogResponse {
        val errors = linkedMapOf<String, String>()
        val devices = adapters.flatMap { adapter ->
            runCatching { adapter.discover() }.getOrElse {
                errors[adapter.id] = it.message ?: "Adaptador indisponível"
                emptyList()
            }
        }
        return DeviceCatalogResponse(devices, errors)
    }

    private val deviceLocks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    fun execute(request: DeviceActionRequest): DeviceActionResponse {
        require(request.deviceId.isNotBlank()) { "Selecione um dispositivo" }
        check(catalog().devices.any { it.id == request.deviceId }) { "Dispositivo indisponível; nenhum outro foi selecionado." }
        return synchronized(deviceLocks.computeIfAbsent(request.deviceId) { Any() }) { executeVerified(request) }
    }

    private fun executeVerified(request: DeviceActionRequest): DeviceActionResponse {
        require(request.deviceId.isNotBlank()) { "Selecione um dispositivo" }
        val device = catalog().devices.singleOrNull { it.id == request.deviceId }
            ?: error("Dispositivo indisponível. Atualize a lista; nenhum outro dispositivo foi selecionado.")
        require(request.action in device.capabilities) { "Ação não suportada pelo dispositivo ou sem permissão" }
        when (request.action) {
            "SET_ENABLED", "SET_MUTED" -> requireNotNull(request.booleanValue) { "Informe o estado desejado" }
            "SET_LEVEL" -> require(request.level != null && request.level in 0..100) { "Nível deve estar entre 0 e 100" }
            "SET_DEFAULT" -> Unit
            in MacCameraAdapter.NATIVE_ACTIONS -> Unit
            else -> error("Ação desconhecida")
        }
        val adapter = adapters.single { it.id == device.adapter }
        adapter.execute(device, request)
        repeat(5) { attempt ->
            val updated = adapter.discover().singleOrNull { it.id == device.id }
            if (updated != null && matches(updated, request)) return DeviceActionResponse(updated)
            if (attempt < 4) Thread.sleep(150)
        }
        error("O comando foi enviado, mas o estado do dispositivo não foi confirmado. Atualize antes de tentar novamente.")
    }

    private fun matches(device: DeviceInfo, request: DeviceActionRequest): Boolean = when (request.action) {
        "SET_ENABLED" -> device.enabled == request.booleanValue
        "SET_MUTED" -> device.muted == request.booleanValue
        "SET_LEVEL" -> device.level?.let { kotlin.math.abs(it - request.level!!) <= 1 } == true
        "SET_DEFAULT" -> device.isDefault == true
        "REQUEST_CAMERA_ACCESS" -> device.settings["authorization"] == "authorized"
        in MacCameraAdapter.NATIVE_ACTIONS -> {
            val key = when {
                request.action.startsWith("FOCUS_") -> "focusMode"
                request.action.startsWith("EXPOSURE_") -> "exposureMode"
                else -> "whiteBalanceMode"
            }
            device.settings[key] == request.action.substringAfterLast('_').lowercase()
        }
        else -> false
    }

    companion object {
        fun native(os: String = System.getProperty("os.name")): DeviceService = DeviceService(when {
            os.lowercase().contains("mac") -> listOf(MacCameraAdapter(), MacAudioDeviceAdapter())
            os.lowercase().contains("win") -> listOf(WindowsCameraAdapter(), WindowsAudioInventoryAdapter())
            else -> listOf(LinuxCameraAdapter(), LinuxAudioAdapter())
        })
    }
}
