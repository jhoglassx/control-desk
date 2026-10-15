package com.controldesk.agent.devices

import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** PulseAudio and PipeWire's PulseAudio compatibility server; ALSA-only systems report unsupported. */
class LinuxAudioAdapter(private val runner: DeviceCommandRunner = NativeDeviceCommandRunner) : DeviceAdapter {
    override val id = "linux.pulse"
    override fun discover(): List<DeviceInfo> = listOf("sink", "source").flatMap { direction ->
        val default = runner.run(listOf("pactl", "get-default-$direction"))
        val raw = runner.run(listOf("pactl", "--format=json", "list", "${direction}s"))
        parse(raw, direction, default)
    }
    internal fun parse(raw: String, direction: String, default: String): List<DeviceInfo> =
        AgentJson.parseToJsonElement(raw).jsonArray.mapNotNull { item ->
            val row = item.jsonObject
            val name = row.getValue("name").jsonPrimitive.content
            if (direction == "source" && name.endsWith(".monitor")) return@mapNotNull null
            val levels = row["volume"]?.jsonObject?.values.orEmpty().mapNotNull { channel ->
                channel.jsonObject["value_percent"]?.jsonPrimitive?.content?.removeSuffix("%")?.toIntOrNull()
            }
            DeviceInfo("$id:$direction:$name", row["description"]?.jsonPrimitive?.content ?: name,
                if (direction == "sink") "AUDIO_OUTPUT" else "AUDIO_INPUT", id,
                listOf("SET_DEFAULT", "SET_MUTED", "SET_LEVEL"),
                muted = row["mute"]?.jsonPrimitive?.booleanOrNull,
                level = levels.takeIf { it.isNotEmpty() }?.average()?.toInt()?.coerceIn(0, 100), isDefault = name == default)
        }
    override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
        val direction = if (device.kind == "AUDIO_INPUT") "source" else "sink"
        val name = device.id.removePrefix("$id:$direction:")
        require(!name.startsWith("-") && !name.startsWith("@")) { "Identidade de áudio inválida" }
        val arguments = when (request.action) {
            "SET_DEFAULT" -> listOf("set-default-$direction", name)
            "SET_MUTED" -> listOf("set-$direction-mute", name, if (request.booleanValue == true) "1" else "0")
            "SET_LEVEL" -> listOf("set-$direction-volume", name, "${requireNotNull(request.level)}%")
            else -> error("Ação não suportada")
        }
        runner.run(listOf("pactl") + arguments)
    }
}
