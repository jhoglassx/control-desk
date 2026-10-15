package com.controldesk.protocol

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfo(
    val id: String,
    val name: String,
    val kind: String,
    val adapter: String,
    val capabilities: List<String> = emptyList(),
    val enabled: Boolean? = null,
    val muted: Boolean? = null,
    val level: Int? = null,
    val isDefault: Boolean? = null,
    val notice: String? = null,
)

@Serializable
data class DeviceCatalogResponse(
    val devices: List<DeviceInfo> = emptyList(),
    val diagnostics: Map<String, String> = emptyMap(),
)

@Serializable
data class DeviceActionRequest(
    val deviceId: String,
    val action: String,
    val booleanValue: Boolean? = null,
    val level: Int? = null,
)

@Serializable
data class DeviceActionResponse(val device: DeviceInfo, val confirmed: Boolean = true)
