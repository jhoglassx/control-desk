package com.controldesk.agent.devices

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacDeviceInventoryTest {
    /** Read-only smoke check; never acquires a capture session or changes device properties. */
    @Test fun nativeMacInventoryCanBeReadWithoutChangingHardware() {
        if (System.getenv("CONTROL_DESK_TEST_NATIVE_DEVICES") != "1") return
        val catalog = DeviceService(listOf(MacCameraAdapter(), MacAudioDeviceAdapter())).catalog()
        assertTrue(catalog.diagnostics.isEmpty(), catalog.diagnostics.toString())
        assertTrue(catalog.devices.isNotEmpty())
        assertEquals(catalog.devices.size, catalog.devices.map { it.id }.distinct().size)
        assertTrue(catalog.devices.filter { it.kind == "CAMERA" }.all { it.capabilities.isEmpty() && it.enabled == null })
    }
}
