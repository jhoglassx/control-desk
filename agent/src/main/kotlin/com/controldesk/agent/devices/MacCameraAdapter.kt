package com.controldesk.agent.devices

import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/** AVFoundation bridge bundled with the app; end users do not need a compiler or camera software. */
class MacCameraAdapter(
    private val helper: () -> File? = ::findCameraHelper,
    private val runner: DeviceCommandRunner = ProcessDeviceCommandRunner(90),
) : DeviceAdapter {
    override val id = "macos.camera"
    override fun discover(): List<DeviceInfo> {
        val binary = helper()
        if (binary == null) return fallback("Módulo AVFoundation ausente. Atualize o agente para consultar controles nativos.")
        return try {
            val raw = runner.run(listOf(binary.absolutePath, "discover"))
            parseNative(raw)
        } catch (error: Exception) {
            fallback("Não foi possível consultar os controles nativos: ${error.message}")
        }
    }
    internal fun parseNative(raw: String): List<DeviceInfo> = AgentJson.parseToJsonElement(raw).jsonArray.map { item ->
        val row = item.jsonObject
        val settings = row["settings"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
        val capabilities = row["capabilities"]?.jsonArray.orEmpty().map { it.jsonPrimitive.content }.filter { it in NATIVE_ACTIONS }
        val permission = settings["authorization"]
        val notice = when {
            permission == "restricted" -> "Acesso à câmera restrito pelo macOS ou por uma política administrativa."
            permission != "authorized" -> "Autorize o controle de câmera no Mac para consultar as capacidades completas. Não iniciamos captura."
            capabilities.isEmpty() -> "A câmera/driver não expõe modos nativos configuráveis. Bloqueio global não é oferecido pelo macOS."
            else -> "Controles nativos AVFoundation. Outros aplicativos podem alterar essas configurações. Bloqueio global não está disponível."
        }
        DeviceInfo("$id:${row.getValue("id").jsonPrimitive.content}", row.getValue("name").jsonPrimitive.content,
            "CAMERA", id, capabilities, notice = notice, settings = settings)
    }
    private fun fallback(notice: String): List<DeviceInfo> {
        val raw = runner.run(listOf("/usr/sbin/system_profiler", "SPCameraDataType", "-json"))
        return AgentJson.parseToJsonElement(raw).jsonObject["SPCameraDataType"]?.jsonArray.orEmpty().mapNotNull { item ->
            val row = item.jsonObject
            val uid = row["spcamera_unique-id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            DeviceInfo("$id:$uid", row["_name"]?.jsonPrimitive?.content ?: uid, "CAMERA", id, notice = notice,
                settings = mapOf("authorization" to "unavailable"))
        }
    }
    override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
        require(request.action in NATIVE_ACTIONS) { "Ação de câmera inválida" }
        val binary = helper() ?: error("Módulo AVFoundation ausente. Atualize o agente.")
        require(device.id.startsWith("$id:")) { "Identidade de câmera inválida" }
        runner.run(listOf(binary.absolutePath, "execute", device.id.removePrefix("$id:"), request.action))
    }
    companion object {
        internal val NATIVE_ACTIONS = setOf("REQUEST_CAMERA_ACCESS", "FOCUS_LOCKED", "FOCUS_AUTO", "FOCUS_CONTINUOUS",
            "EXPOSURE_LOCKED", "EXPOSURE_AUTO", "EXPOSURE_CONTINUOUS", "WHITE_BALANCE_LOCKED", "WHITE_BALANCE_AUTO", "WHITE_BALANCE_CONTINUOUS")
    }
}

private fun findCameraHelper(): File? {
    val resources = System.getProperty("compose.application.resources.dir")
    return listOfNotNull(System.getProperty("controldesk.camera.helper")?.let(::File),
        resources?.let { File(it, "macos/control-desk-camera") }, resources?.let { File(it, "control-desk-camera") },
        File("build/native/macos/control-desk-camera"), File("agent/build/native/macos/control-desk-camera"))
        .firstOrNull { it.isFile && it.canExecute() }
}
