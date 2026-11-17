package com.controldesk.agent.devices

import com.controldesk.protocol.AgentJson
import com.controldesk.protocol.DeviceActionRequest
import com.controldesk.protocol.DeviceInfo
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class WindowsCameraAdapter(private val runner: DeviceCommandRunner = NativeDeviceCommandRunner) : DeviceAdapter {
    override val id = "windows.pnp-camera"
    override fun discover(): List<DeviceInfo> = parse(runner.run(powershell(DISCOVERY)))

    internal fun parse(raw: String): List<DeviceInfo> {
        val root = AgentJson.parseToJsonElement(raw)
        val rows = if (root is JsonArray) root else listOf(root)
        return rows.map { item ->
            val row = item.jsonObject
            val problem = row["problem"]?.jsonPrimitive?.intOrNull
            val disabled = problem == 22
            val admin = row["admin"]?.jsonPrimitive?.booleanOrNull == true
            DeviceInfo("$id:${row.getValue("instanceId").jsonPrimitive.content}", row.getValue("name").jsonPrimitive.content,
                "CAMERA", id, capabilities = if (admin && problem in listOf(0, 22)) listOf("SET_ENABLED") else emptyList(), enabled = if (problem in listOf(0, 22)) !disabled else null,
                notice = if (admin) "Habilitar/desabilitar afeta todos os aplicativos; alguns drivers exigem reinício."
                    else "Abra o agente com privilégios administrativos para habilitar/desabilitar esta câmera.")
        }
    }

    override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
        require(request.action == "SET_ENABLED")
        val instanceId = device.id.removePrefix("$id:").replace("'", "''")
        val verb = if (request.booleanValue == true) "Enable-PnpDevice" else "Disable-PnpDevice"
        runner.run(powershell("${'$'}ErrorActionPreference='Stop'; $verb -InstanceId '$instanceId' -Confirm:${'$'}false -ErrorAction Stop"))
    }

    private fun powershell(script: String) = listOf("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)

    companion object {
        internal val DISCOVERY = """
            ${'$'}ErrorActionPreference = 'Stop';
            [Console]::OutputEncoding = [System.Text.Encoding]::UTF8;
            ${'$'}admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator);
            ${'$'}rows = @(Get-PnpDevice -PresentOnly | Where-Object { ${'$'}_.Class -eq 'Camera' -or (${'$'}_.Class -eq 'Image' -and (Get-PnpDeviceProperty -InstanceId ${'$'}_.InstanceId -KeyName 'DEVPKEY_Device_Service' -ErrorAction SilentlyContinue).Data -eq 'usbvideo') } | ForEach-Object {
                ${'$'}problem = (Get-PnpDeviceProperty -InstanceId ${'$'}_.InstanceId -KeyName 'DEVPKEY_Device_ProblemCode' -ErrorAction Stop).Data;
                [pscustomobject]@{instanceId=${'$'}_.InstanceId;name=${'$'}_.FriendlyName;problem=[int]${'$'}problem;admin=${'$'}admin}
            });
            ConvertTo-Json -InputObject ${'$'}rows -Compress;
        """.trimIndent()
    }
}
