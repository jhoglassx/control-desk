package com.controldesk.agent.devices

import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** macOS camera inventory without a capture session, compiler or third-party binary. */
class MacCameraAdapter(private val runner: DeviceCommandRunner = NativeDeviceCommandRunner) : DeviceAdapter {
    override val id = "macos.camera"
    override fun discover(): List<DeviceInfo> {
        val raw = runner.run(listOf("/usr/sbin/system_profiler", "SPCameraDataType", "-json"))
        return AgentJson.parseToJsonElement(raw).jsonObject["SPCameraDataType"]?.jsonArray.orEmpty().mapNotNull { item ->
            val row = item.jsonObject
            val uid = row["spcamera_unique-id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            DeviceInfo("$id:$uid", row["_name"]?.jsonPrimitive?.content ?: uid,
                "CAMERA", id, notice = "O macOS não oferece bloqueio global de câmera por esta API. Nenhuma captura foi iniciada.")
        }
    }
    override fun execute(device: DeviceInfo, request: DeviceActionRequest): Unit = error("Controle não suportado pelo macOS")
}
