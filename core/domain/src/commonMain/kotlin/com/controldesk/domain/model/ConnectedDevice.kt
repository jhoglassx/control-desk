package com.controldesk.domain.model

enum class DeviceKind { CAMERA, AUDIO_INPUT, AUDIO_OUTPUT }
enum class DeviceCapability { SET_ENABLED, SET_MUTED, SET_LEVEL, SET_DEFAULT }
enum class DeviceOperation(val capability: DeviceCapability) {
    ENABLE(DeviceCapability.SET_ENABLED), DISABLE(DeviceCapability.SET_ENABLED), TOGGLE_ENABLED(DeviceCapability.SET_ENABLED),
    MUTE(DeviceCapability.SET_MUTED), UNMUTE(DeviceCapability.SET_MUTED), TOGGLE_MUTED(DeviceCapability.SET_MUTED),
    SET_LEVEL(DeviceCapability.SET_LEVEL), SET_DEFAULT(DeviceCapability.SET_DEFAULT),
}

data class ConnectedDevice(
    val id: String,
    val name: String,
    val kind: DeviceKind,
    val adapter: String,
    val capabilities: List<DeviceCapability> = emptyList(),
    val enabled: Boolean? = null,
    val muted: Boolean? = null,
    val level: Int? = null,
    val isDefault: Boolean? = null,
    val notice: String? = null,
)
data class DeviceCatalog(val devices: List<ConnectedDevice> = emptyList(), val diagnostics: Map<String, String> = emptyMap())
data class DeviceCommand(val deviceId: String, val action: DeviceCapability, val booleanValue: Boolean? = null, val level: Int? = null)
