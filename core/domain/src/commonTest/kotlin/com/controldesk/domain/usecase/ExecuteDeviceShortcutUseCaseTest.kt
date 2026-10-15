package com.controldesk.domain.usecase

import com.controldesk.domain.model.*
import com.controldesk.testing.FakeAgentGateway
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class ExecuteDeviceShortcutUseCaseTest {
    private val host = PairedHost("pc", "PC", "localhost", 8741, OsType.MACOS, 0, "test")
    private fun camera() = ConnectedDevice("physical-2", "Camera", DeviceKind.CAMERA, "test", listOf(DeviceCapability.SET_ENABLED), enabled = true)
    private fun shortcut() = ControlShortcut("shortcut", "My camera", ControlShortcutKind.CAMERA, ShortcutDestination.DEVICE,
        deviceId = "physical-2", deviceOperation = "TOGGLE_ENABLED").toButton()
    @Test fun toggleReadsAgentStateAndSendsOneExplicitTargetedCommand() = runTest {
        val gateway = FakeAgentGateway().apply { devices = DeviceCatalog(listOf(camera().copy(id = "physical-1"), camera())) }
        val useCase = ExecuteDeviceShortcutUseCase(gateway)
        useCase(shortcut(), host)
        assertEquals(DeviceCommand("physical-2", DeviceCapability.SET_ENABLED, false), gateway.deviceCommands.single())
        assertTrue(gateway.devices.devices.first().enabled!!)
        useCase(shortcut(), host)
        assertEquals(true, gateway.deviceCommands.last().booleanValue)
    }
    @Test fun missingOrUnsupportedDeviceDoesNotUseAnotherCamera() = runTest {
        val gateway = FakeAgentGateway().apply { devices = DeviceCatalog(listOf(camera().copy(id = "physical-1"))) }
        assertFails { ExecuteDeviceShortcutUseCase(gateway)(shortcut(), host) }
        gateway.devices = DeviceCatalog(listOf(camera().copy(capabilities = emptyList())))
        assertFails { ExecuteDeviceShortcutUseCase(gateway)(shortcut(), host) }
        gateway.devices = DeviceCatalog(listOf(camera().copy(enabled = null)))
        assertFails { ExecuteDeviceShortcutUseCase(gateway)(shortcut(), host) }
        assertTrue(gateway.deviceCommands.isEmpty())
    }
    @Test fun nativeIdentityAndOperationSurvivePersistence() {
        val configured = ControlShortcut("s", "Mic", ControlShortcutKind.MICROPHONE, ShortcutDestination.DEVICE,
            deviceId = "input:uid", deviceOperation = "TOGGLE_MUTED")
        val json = kotlinx.serialization.json.Json
        val restored = json.decodeFromString<ControlShortcut>(json.encodeToString(configured))
        assertEquals(configured, restored)
        assertNull(restored.validationError())
        assertEquals(ActionType.REMOTE_DEVICE, restored.toButton().actionType)
        assertEquals("input:uid", restored.toButton().payload["deviceId"])
    }
}
