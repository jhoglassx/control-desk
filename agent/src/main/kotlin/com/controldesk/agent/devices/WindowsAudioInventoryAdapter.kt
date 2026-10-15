package com.controldesk.agent.devices

import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Lists Windows MMDevice endpoints without requiring an external PowerShell module. */
class WindowsAudioInventoryAdapter(private val runner: DeviceCommandRunner = NativeDeviceCommandRunner) : DeviceAdapter {
    override val id = "windows.audio-inventory"
    override fun discover(): List<DeviceInfo> {
        val output = runner.run(listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", SCRIPT))
        return AgentJson.parseToJsonElement(output).jsonArray.mapNotNull { item ->
            val row = item.jsonObject
            val uid = row.getValue("instanceId").jsonPrimitive.content
            val kind = when {
                uid.contains("{0.0.0.") -> "AUDIO_OUTPUT"
                uid.contains("{0.0.1.") -> "AUDIO_INPUT"
                else -> return@mapNotNull null
            }
            DeviceInfo("$id:$uid", row.getValue("name").jsonPrimitive.content, kind, id,
                notice = "Dispositivo identificado; controle de endpoint Core Audio no Windows ainda não disponível.")
        }
    }
    override fun execute(device: DeviceInfo, request: DeviceActionRequest): Unit = error("Controle de áudio Windows ainda indisponível")
    companion object {
        internal val SCRIPT = """
            ${'$'}ErrorActionPreference='Stop';
            [Console]::OutputEncoding=[System.Text.Encoding]::UTF8;
            ${'$'}rows=@(Get-PnpDevice -PresentOnly -Class AudioEndpoint | ForEach-Object {
                [pscustomobject]@{instanceId=${'$'}_.InstanceId;name=${'$'}_.FriendlyName}
            });
            ConvertTo-Json -InputObject ${'$'}rows -Compress;
        """.trimIndent()
    }
}
