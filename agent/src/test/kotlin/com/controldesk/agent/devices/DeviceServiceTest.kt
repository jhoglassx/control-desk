package com.controldesk.agent.devices

import com.controldesk.protocol.*
import kotlin.test.*

class DeviceServiceTest {
    private class FakeAdapter : DeviceAdapter {
        override val id = "fake"
        var devices = listOf(DeviceInfo("cam-1", "USB Camera", "CAMERA", id, listOf("SET_ENABLED"), enabled = true),
            DeviceInfo("cam-2", "USB Camera", "CAMERA", id, listOf("SET_ENABLED"), enabled = true))
        var calls = 0
        var confirms = true
        override fun discover() = devices
        override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
            calls++
            if (confirms) devices = devices.map { if (it.id == device.id) it.copy(enabled = request.booleanValue) else it }
        }
    }
    @Test fun sameNamesNeverSelectAnotherDeviceAndCommandConfirmsState() {
        val adapter = FakeAdapter()
        val service = DeviceService(listOf(adapter))
        val response = service.execute(DeviceActionRequest("cam-2", "SET_ENABLED", false))
        assertTrue(response.confirmed)
        assertFalse(response.device.enabled!!)
        assertTrue(adapter.devices.first().enabled!!)
        adapter.devices = adapter.devices.filterNot { it.id == "cam-2" }
        assertFails { service.execute(DeviceActionRequest("cam-2", "SET_ENABLED", true)) }
        assertEquals(1, adapter.calls)
    }
    @Test fun unsupportedOrInvalidRequestsNeverExecute() {
        val adapter = FakeAdapter()
        val service = DeviceService(listOf(adapter))
        listOf(DeviceActionRequest("cam-1", "RUN_SHELL"), DeviceActionRequest("cam-1", "SET_ENABLED"),
            DeviceActionRequest("cam-1", "SET_LEVEL", level = 999)).forEach { request -> assertFails { service.execute(request) } }
        adapter.devices = adapter.devices.map { it.copy(capabilities = emptyList()) }
        assertFails { service.execute(DeviceActionRequest("cam-1", "SET_ENABLED", false)) }
        assertEquals(0, adapter.calls)
    }
    @Test fun failedAdapterDoesNotHideWorkingAdapter() {
        val broken = object : DeviceAdapter {
            override val id = "broken"
            override fun discover(): List<DeviceInfo> = error("Permission denied")
            override fun execute(device: DeviceInfo, request: DeviceActionRequest) = Unit
        }
        val catalog = DeviceService(listOf(broken, FakeAdapter())).catalog()
        assertEquals(2, catalog.devices.size)
        assertTrue("broken" in catalog.diagnostics)
    }
    @Test fun noConfirmedChangeIsNeverReportedAsSuccess() {
        val adapter = FakeAdapter().apply { confirms = false }
        assertFails { DeviceService(listOf(adapter)).execute(DeviceActionRequest("cam-1", "SET_ENABLED", false)) }
        assertEquals(1, adapter.calls)
    }
    @Test fun cameraModesAreConfirmedUsingFreshNativeSettings() {
        var mode = "continuous"
        val adapter = object : DeviceAdapter {
            override val id = "native-camera"
            override fun discover() = listOf(DeviceInfo("uid", "Camera", "CAMERA", id,
                listOf("EXPOSURE_LOCKED"), settings = mapOf("exposureMode" to mode, "authorization" to "authorized")))
            override fun execute(device: DeviceInfo, request: DeviceActionRequest) { mode = "locked" }
        }
        val result = DeviceService(listOf(adapter)).execute(DeviceActionRequest("uid", "EXPOSURE_LOCKED"))
        assertTrue(result.confirmed)
        assertEquals("locked", result.device.settings["exposureMode"])
    }
    @Test fun permissionPromptDoesNotBlockCommandsForAnotherDevice() {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val granted = java.util.concurrent.atomic.AtomicBoolean(false)
        val muted = java.util.concurrent.atomic.AtomicBoolean(false)
        val adapter = object : DeviceAdapter {
            override val id = "native"
            override fun discover() = listOf(
                DeviceInfo("cam", "Camera", "CAMERA", id, listOf("REQUEST_CAMERA_ACCESS"), settings = mapOf("authorization" to if (granted.get()) "authorized" else "notDetermined")),
                DeviceInfo("mic", "Microphone", "AUDIO_INPUT", id, listOf("SET_MUTED"), muted = muted.get()))
            override fun execute(device: DeviceInfo, request: DeviceActionRequest) {
                if (device.id == "cam") { entered.countDown(); release.await(3, java.util.concurrent.TimeUnit.SECONDS); granted.set(true) }
                else muted.set(request.booleanValue == true)
            }
        }
        val service = DeviceService(listOf(adapter))
        val pool = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val camera = pool.submit<DeviceActionResponse> { service.execute(DeviceActionRequest("cam", "REQUEST_CAMERA_ACCESS")) }
            assertTrue(entered.await(1, java.util.concurrent.TimeUnit.SECONDS))
            val audio = pool.submit<DeviceActionResponse> { service.execute(DeviceActionRequest("mic", "SET_MUTED", true)) }
            assertTrue(audio.get(1, java.util.concurrent.TimeUnit.SECONDS).confirmed)
            release.countDown()
            assertTrue(camera.get(1, java.util.concurrent.TimeUnit.SECONDS).confirmed)
        } finally { release.countDown(); pool.shutdownNow() }
    }
}
