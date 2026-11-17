package com.controldesk.agent.devices

import com.controldesk.protocol.DeviceActionRequest
import kotlin.test.*

class DeviceAdapterTest {
    @Test fun macCameraDiscoveryReadsNativeModesWithoutInventingPowerControl() {
        val commands = mutableListOf<List<String>>()
        val adapter = MacCameraAdapter(helper = { java.io.File("/bundled/camera-helper") }, runner = DeviceCommandRunner {
            commands += it
            """[{"id":"real-device-uid","name":"Camera","capabilities":["FOCUS_LOCKED","FOCUS_CONTINUOUS","SET_ENABLED"],"settings":{"authorization":"authorized","focusMode":"continuous"}}]"""
        })
        val camera = adapter.discover().single()
        assertTrue(camera.id.endsWith(":real-device-uid"))
        assertEquals(listOf("FOCUS_LOCKED", "FOCUS_CONTINUOUS"), camera.capabilities)
        assertNull(camera.enabled)
        assertEquals("continuous", camera.settings["focusMode"])
        assertEquals("discover", commands.single().last())
    }
    @Test fun missingNativeHelperIsReportedSeparatelyFromHardwareSupport() {
        val adapter = MacCameraAdapter(helper = { null }, runner = DeviceCommandRunner {
            """{"SPCameraDataType":[{"spcamera_unique-id":"uid","_name":"Camera"}]}"""
        })
        val camera = adapter.discover().single()
        assertTrue(camera.capabilities.isEmpty())
        assertEquals("unavailable", camera.settings["authorization"])
        assertTrue(camera.notice!!.contains("Módulo AVFoundation ausente"))
    }
    @Test fun nativeCameraCommandsAlwaysCarryTheExactUidAndValidateAction() {
        val commands = mutableListOf<List<String>>()
        val adapter = MacCameraAdapter(helper = { java.io.File("/bundled/camera-helper") }, runner = DeviceCommandRunner { commands += it; "[]" })
        val camera = com.controldesk.protocol.DeviceInfo("macos.camera:uid-2", "Camera", "CAMERA", "macos.camera")
        adapter.execute(camera, DeviceActionRequest(camera.id, "EXPOSURE_LOCKED"))
        assertEquals(listOf("/bundled/camera-helper", "execute", "uid-2", "EXPOSURE_LOCKED"), commands.single())
        assertFails { adapter.execute(camera, DeviceActionRequest(camera.id, "SET_ENABLED", false)) }
        assertEquals(1, commands.size)
    }
    @Test fun windowsCapabilitiesReflectPrivilegesAndDisabledState() {
        val adapter = WindowsCameraAdapter { error("Not executed") }
        val cameras = adapter.parse("""[{"instanceId":"USB\\CAM1","name":"Camera","problem":22,"admin":true},{"instanceId":"USB\\CAM2","name":"Camera","problem":0,"admin":false}]""")
        assertFalse(cameras.first().enabled!!)
        assertEquals(listOf("SET_ENABLED"), cameras.first().capabilities)
        assertTrue(cameras.last().capabilities.isEmpty())
        assertNotEquals(cameras.first().id, cameras.last().id)
    }
    @Test fun linuxAudioUsesStableNamesAndExcludesMonitorSources() {
        val adapter = LinuxAudioAdapter { error("Not executed") }
        val devices = adapter.parse("""[{"name":"alsa_input.usb-device","description":"USB mic","mute":true,"volume":{"front-left":{"value_percent":"37%"}}},{"name":"sink.monitor","mute":false}]""", "source", "alsa_input.usb-device")
        assertEquals(1, devices.size)
        assertTrue(devices.single().id.endsWith(":alsa_input.usb-device"))
        assertEquals(37, devices.single().level)
        assertTrue(devices.single().isDefault!!)
    }
    @Test fun windowsExecutesOnlyExactIdAndEscapesQuotesWithoutShell() {
        val commands = mutableListOf<List<String>>()
        val adapter = WindowsCameraAdapter { commands += it; """[{"instanceId":"USB\\CAM'1","name":"Camera","problem":0,"admin":true}]""" }
        val device = adapter.discover().single()
        adapter.execute(device, DeviceActionRequest(device.id, "SET_ENABLED", false))
        val command = commands.last()
        assertEquals("powershell.exe", command.first())
        assertTrue(command.last().contains("Disable-PnpDevice -InstanceId 'USB\\CAM''1'"))
        assertFalse(command.last().contains("-Verb RunAs"))
    }
    @Test fun linuxPrivacyCapabilityDependsOnDriverAndUsesExactNativeNode() {
        val root = kotlin.io.path.createTempDirectory("device-test").toFile()
        try {
            val sys = java.io.File(root, "sys").apply { mkdirs() }
            val dev = java.io.File(root, "dev").apply { mkdirs() }
            val node = java.io.File(sys, "video7").apply { mkdirs() }
            java.io.File(node, "name").writeText("USB Camera")
            java.io.File(node, "index").writeText("0")
            java.io.File(dev, "video7").writeText("")
            val commands = mutableListOf<List<String>>()
            var raw = "Video Capture\n    privacy 0x009a0910 (bool) : default=0 value=1"
            val adapter = LinuxCameraAdapter(DeviceCommandRunner { commands += it; raw }, sys, dev)
            val camera = adapter.discover().single()
            assertEquals(listOf("SET_ENABLED"), camera.capabilities)
            assertFalse(camera.enabled!!)
            adapter.execute(camera, DeviceActionRequest(camera.id, "SET_ENABLED", true))
            assertEquals(listOf("v4l2-ctl", "--device", java.io.File(dev, "video7").path, "--set-ctrl=privacy=0"), commands.last())
            raw += " flags=read-only"
            assertTrue(adapter.discover().single().capabilities.isEmpty())
            raw = "Video Capture"
            assertNull(adapter.discover().single().enabled)
        } finally { root.deleteRecursively() }
    }
    @Test fun windowsAudioListsEndpointsButNeverClaimsUnsupportedControl() {
        val adapter = WindowsAudioInventoryAdapter { """[{"instanceId":"SWD\\MMDEVAPI\\{0.0.1.00000000}.abc","name":"Microphone"}]""" }
        val device = adapter.discover().single()
        assertEquals("AUDIO_INPUT", device.kind)
        assertTrue(device.capabilities.isEmpty())
        assertFails { adapter.execute(device, DeviceActionRequest(device.id, "SET_MUTED", true)) }
    }
}
