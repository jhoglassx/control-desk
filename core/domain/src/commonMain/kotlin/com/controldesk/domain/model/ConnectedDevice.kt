package com.controldesk.domain.model

enum class DeviceKind { CAMERA, AUDIO_INPUT, AUDIO_OUTPUT }
enum class DeviceCapability {
    SET_ENABLED, SET_MUTED, SET_LEVEL, SET_DEFAULT, REQUEST_CAMERA_ACCESS,
    FOCUS_LOCKED, FOCUS_AUTO, FOCUS_CONTINUOUS, EXPOSURE_LOCKED, EXPOSURE_AUTO, EXPOSURE_CONTINUOUS,
    WHITE_BALANCE_LOCKED, WHITE_BALANCE_AUTO, WHITE_BALANCE_CONTINUOUS,
}
enum class DeviceOperation(val capability: DeviceCapability) {
    FOCUS_LOCKED(DeviceCapability.FOCUS_LOCKED), FOCUS_AUTO(DeviceCapability.FOCUS_AUTO), FOCUS_CONTINUOUS(DeviceCapability.FOCUS_CONTINUOUS),
    EXPOSURE_LOCKED(DeviceCapability.EXPOSURE_LOCKED), EXPOSURE_AUTO(DeviceCapability.EXPOSURE_AUTO), EXPOSURE_CONTINUOUS(DeviceCapability.EXPOSURE_CONTINUOUS),
    WHITE_BALANCE_LOCKED(DeviceCapability.WHITE_BALANCE_LOCKED), WHITE_BALANCE_AUTO(DeviceCapability.WHITE_BALANCE_AUTO), WHITE_BALANCE_CONTINUOUS(DeviceCapability.WHITE_BALANCE_CONTINUOUS),
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
    val settings: Map<String, String> = emptyMap(),
)
data class DeviceCatalog(val devices: List<ConnectedDevice> = emptyList(), val diagnostics: Map<String, String> = emptyMap())
data class DeviceCommand(val deviceId: String, val action: DeviceCapability, val booleanValue: Boolean? = null, val level: Int? = null)
