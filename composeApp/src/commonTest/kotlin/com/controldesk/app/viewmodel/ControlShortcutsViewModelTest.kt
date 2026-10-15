package com.controldesk.app.viewmodel

import com.controldesk.domain.model.*
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.testing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class ControlShortcutsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun teardown() = Dispatchers.resetMain()
    private fun host() = PairedHost("mac", "Mac", "localhost", 8741, OsType.MACOS, 0, "test")

    @Test fun editingControlsPreservesLatestHostLayoutAndPairing() = runTest(dispatcher) {
        val host = host()
        val prefs = FakeHostPreferences(listOf(host))
        val vm = PinnedAppsViewModel(FakeAgentGateway(), prefs)
        vm.onIntent(PinnedAppsIntent.LoadInstalledApps(host))
        runCurrent()
        val camera = ControlShortcut("cam", "Frente", ControlShortcutKind.CAMERA, ShortcutDestination.URL, "http://192.168.1.2")
        vm.onIntent(PinnedAppsIntent.EditControl(camera))
        val latest = host.copy(appOrder = listOf("other"), appTileSizes = mapOf("card:other" to AppTileSize(2, 2)))
        prefs.saveHost(latest)
        vm.onIntent(PinnedAppsIntent.SaveControl)
        runCurrent()
        val saved = prefs.getHosts().single()
        assertEquals(latest.token, saved.token)
        assertEquals(latest.appOrder, saved.appOrder)
        assertEquals(latest.appTileSizes, saved.appTileSizes)
        assertEquals(camera, saved.controlShortcuts.last())
        assertNull(vm.uiState.value.editingControl)
        vm.onIntent(PinnedAppsIntent.RemoveControl(camera.id))
        runCurrent()
        assertEquals(ControlShortcut.defaults(), prefs.getHosts().single().controlShortcuts)
    }

    @Test fun invalidDraftNeverSavesAndCancelDiscardsChanges() = runTest(dispatcher) {
        val prefs = FakeHostPreferences(listOf(host()))
        val vm = PinnedAppsViewModel(FakeAgentGateway(), prefs)
        vm.onIntent(PinnedAppsIntent.LoadInstalledApps(host()))
        runCurrent()
        vm.onIntent(PinnedAppsIntent.EditControl(ControlShortcut("bad", "Camera", ControlShortcutKind.CAMERA, ShortcutDestination.URL, "file:///tmp")))
        vm.onIntent(PinnedAppsIntent.SaveControl)
        runCurrent()
        assertNotNull(vm.uiState.value.controlError)
        assertEquals(ControlShortcut.defaults(), prefs.getHosts().single().controlShortcuts)
        vm.onIntent(PinnedAppsIntent.CancelControl)
        assertNull(vm.uiState.value.editingControl)
    }

    @Test fun audioCardsCanMoveResizeAndKeepLayoutAfterEmptyRuntimeSnapshot() = runTest(dispatcher) {
        val host = host()
        val prefs = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = listOf(RunningApp("Editor", "Editor")))
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val vm = DeskViewModel(prefs, gateway, realtime, ExecuteGridButtonUseCase(gateway), FakeIconCache(), coroutineScope = backgroundScope)
        runCurrent()
        vm.onIntent(DeskIntent.ResizeAppTile("system-microphone"))
        vm.onIntent(DeskIntent.SetReorganizingApps(true))
        vm.onIntent(DeskIntent.SelectAppForReorder("system-microphone", 100f, 100f))
        assertNotNull(vm.uiState.value.selectedAppDragStartIndex)
        val ids = vm.uiState.value.workspaceButtonsFor(host.id).map { it.id }.reversed()
        vm.onIntent(DeskIntent.PreviewAppGridPositions(ids.mapIndexed { index, id -> id to index }.toMap()))
        vm.onIntent(DeskIntent.FinishAppReorderDrag)
        runCurrent()
        assertNull(vm.uiState.value.selectedAppIdForReorder)
        assertEquals(ids, prefs.getHosts().single().appOrder.take(ids.size))
        realtime.emitApps(emptyList())
        runCurrent()
        val mic = vm.uiState.value.workspaceButtonsFor(host.id).single { it.id == "system-microphone" }
        assertEquals("2", mic.payload["spanColumns"])
        assertEquals(ids, vm.uiState.value.workspaceButtonsFor(host.id).map { it.id })
    }
    @Test fun deviceUrlAndApplicationActionsExecuteThroughAgentWithoutChangingActiveApp() = runTest(dispatcher) {
        val host = host()
        val gateway = FakeAgentGateway(apps = listOf(RunningApp("Editor", "Editor", isActive = true)))
        val vm = DeskViewModel(FakeHostPreferences(listOf(host)), gateway, FakeAgentRealtime(apps = gateway.apps),
            ExecuteGridButtonUseCase(gateway), FakeIconCache(), coroutineScope = backgroundScope)
        runCurrent()
        val initial = vm.uiState.value.buttons
        val camera = ControlShortcut("cam", "Câmera", ControlShortcutKind.CAMERA, ShortcutDestination.URL, "http://192.168.1.2")
        vm.onButtonClick(camera.toButton())
        runCurrent()
        assertEquals(listOf(camera.target), gateway.openedUrls)
        assertEquals(initial, vm.uiState.value.buttons)
        val controller = camera.copy(id = "controller", destination = ShortcutDestination.APPLICATION, target = "/Applications/Camera Hub.app")
        vm.onButtonClick(controller.toButton())
        vm.onAppClose(controller.toButton())
        runCurrent()
        assertEquals(controller.target, gateway.launched.single().payload["bundlePath"])
        assertTrue(gateway.closed.isEmpty())
        assertEquals(initial, vm.uiState.value.buttons)
    }
    @Test fun editingAnExistingControlKeepsItsOriginalCatalogPosition() = runTest(dispatcher) {
        val host = host()
        val prefs = FakeHostPreferences(listOf(host))
        val vm = PinnedAppsViewModel(FakeAgentGateway(), prefs)
        vm.onIntent(PinnedAppsIntent.LoadInstalledApps(host))
        runCurrent()
        vm.onIntent(PinnedAppsIntent.EditControl(host.controlShortcuts.first().copy(name = "Fone")))
        vm.onIntent(PinnedAppsIntent.SaveControl)
        runCurrent()
        assertEquals(host.controlShortcuts.map { it.id }, prefs.getHosts().single().controlShortcuts.map { it.id })
        assertEquals("Fone", prefs.getHosts().single().controlShortcuts.first().name)
    }
    @Test fun nativeEditorOffersOnlyCapabilitiesReportedByAgent() = runTest(dispatcher) {
        val device = ConnectedDevice("cam", "USB Camera", DeviceKind.CAMERA, "native")
        val gateway = FakeAgentGateway().apply { devices = DeviceCatalog(listOf(device)) }
        val prefs = FakeHostPreferences(listOf(host()))
        val vm = PinnedAppsViewModel(gateway, prefs)
        vm.onIntent(PinnedAppsIntent.LoadInstalledApps(host()))
        val draft = ControlShortcut("camera", "Camera", ControlShortcutKind.CAMERA, ShortcutDestination.DEVICE,
            deviceId = device.id, deviceOperation = "TOGGLE_ENABLED")
        vm.onIntent(PinnedAppsIntent.EditControl(draft))
        runCurrent()
        assertEquals(device, vm.uiState.value.deviceCatalog.devices.single())
        vm.onIntent(PinnedAppsIntent.SaveControl)
        runCurrent()
        assertNotNull(vm.uiState.value.controlError)
        assertEquals(ControlShortcut.defaults(), prefs.getHosts().single().controlShortcuts)
        gateway.devices = DeviceCatalog(listOf(device.copy(capabilities = listOf(DeviceCapability.SET_ENABLED))))
        vm.onIntent(PinnedAppsIntent.LoadDevices)
        runCurrent()
        vm.onIntent(PinnedAppsIntent.SaveControl)
        runCurrent()
        assertEquals(draft, prefs.getHosts().single().controlShortcuts.last())
    }

    @Test fun nativeCommandsGuardDoubleTapAndDisconnectedCardsKeepSizeAndPosition() = runTest(dispatcher) {
        val camera = ConnectedDevice("real-camera", "Camera", DeviceKind.CAMERA, "native", listOf(DeviceCapability.SET_ENABLED), enabled = true)
        val shortcut = ControlShortcut("camera-slot", "Camera", ControlShortcutKind.CAMERA, ShortcutDestination.DEVICE,
            deviceId = camera.id, deviceOperation = "TOGGLE_ENABLED")
        val configuredHost = host().copy(controlShortcuts = listOf(shortcut), appOrder = listOf(shortcut.id),
            appTileSizes = mapOf("card:${shortcut.id}" to AppTileSize(2, 2)))
        val gateway = FakeAgentGateway().apply {
            devices = DeviceCatalog(listOf(camera))
            deviceCommandGate = kotlinx.coroutines.CompletableDeferred()
        }
        val vm = DeskViewModel(FakeHostPreferences(listOf(configuredHost)), gateway, FakeAgentRealtime(),
            ExecuteGridButtonUseCase(gateway), FakeIconCache(), coroutineScope = backgroundScope)
        runCurrent()
        val button = vm.uiState.value.workspaceButtonsFor(configuredHost.id).single()
        vm.onButtonClick(button)
        vm.onButtonClick(button)
        runCurrent()
        assertEquals(1, gateway.deviceCommands.size)
        assertEquals("Executando…", vm.uiState.value.workspaceButtonsFor(configuredHost.id).single().payload["deviceStatus"])
        gateway.deviceCommandGate!!.complete(Unit)
        runCurrent()
        assertEquals("Câmera bloqueada", vm.uiState.value.workspaceButtonsFor(configuredHost.id).single().payload["deviceStatus"])
        gateway.devices = DeviceCatalog()
        advanceTimeBy(5_001)
        runCurrent()
        val disconnected = vm.uiState.value.workspaceButtonsFor(configuredHost.id).single()
        assertEquals(shortcut.id, disconnected.id)
        assertEquals("2", disconnected.payload["spanRows"])
        assertEquals("Desconectado / indisponível", disconnected.payload["deviceStatus"])
        assertFalse(disconnected.isActive)
        vm.onButtonClick(disconnected)
        runCurrent()
        assertEquals(1, gateway.deviceCommands.size)
        assertTrue(vm.uiState.value.feedback.orEmpty().contains("indisponível"))
    }
}
