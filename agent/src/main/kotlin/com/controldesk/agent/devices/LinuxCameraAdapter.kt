package com.controldesk.agent.devices

import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import java.io.File

class LinuxCameraAdapter(
    private val runner: DeviceCommandRunner = NativeDeviceCommandRunner,
    private val sysRoot: File = File("/sys/class/video4linux"),
    private val devRoot: File = File("/dev"),
) : DeviceAdapter {
    override val id = "linux.v4l2"
    private fun nodes() = sysRoot.listFiles().orEmpty().filter { it.name.matches(Regex("video[0-9]+")) }
    private fun deviceId(node: File): String {
        val path = File(devRoot, node.name).canonicalPath
        val alias = File(devRoot, "v4l/by-id").listFiles().orEmpty().filter { it.canonicalPath == path }.minByOrNull { it.name }
        val stable = alias?.name ?: "${File(node, "device").canonicalPath}:${File(node, "index").takeIf { it.exists() }?.readText()?.trim() ?: "0"}"
        return "$id:$stable"
    }
    override fun discover(): List<DeviceInfo> = nodes().mapNotNull { node ->
        val device = File(devRoot, node.name)
        var warning: String? = null
        val info = runCatching { runner.run(listOf("v4l2-ctl", "--device", device.path, "--all")) }.getOrElse {
            warning = it.message ?: "Instale v4l-utils e confira as permissões de vídeo."
            ""
        }
        if (info.isNotEmpty() && !info.contains("Video Capture")) return@mapNotNull null
        val line = info.lineSequence().firstOrNull { Regex("^\\s*privacy\\s").containsMatchIn(it) }
        val privacy = line?.let { Regex("value=([01])(?:\\s|$)").find(it)?.groupValues?.get(1)?.toInt() }
        val writable = privacy != null && device.canWrite() && line?.contains(Regex("read-only|inactive|disabled")) != true
        DeviceInfo(deviceId(node), File(node, "name").takeIf { it.exists() }?.readText()?.trim() ?: node.name,
            "CAMERA", id, if (writable) listOf("SET_ENABLED") else emptyList(), enabled = privacy?.let { it == 0 },
            notice = warning ?: if (privacy == null) "O driver não expõe controle de privacidade." else "Controle de privacidade V4L2; não altera o botão de vídeo da reunião.")
    }
    override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
        require(request.action == "SET_ENABLED")
        val node = nodes().singleOrNull { deviceId(it) == device.id } ?: error("Câmera removida")
        runner.run(listOf("v4l2-ctl", "--device", File(devRoot, node.name).path, "--set-ctrl=privacy=${if (request.booleanValue == true) 0 else 1}"))
    }
}
