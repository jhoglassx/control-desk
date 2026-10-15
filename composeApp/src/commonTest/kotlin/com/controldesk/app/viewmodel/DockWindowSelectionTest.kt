package com.controldesk.app.viewmodel

import com.controldesk.domain.model.AppTileSize
import com.controldesk.domain.model.ApplicationShortcut
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.model.PinnedAppShortcut
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.testing.FakeAgentGateway
import com.controldesk.testing.FakeAgentRealtime
import com.controldesk.testing.FakeHostPreferences
import com.controldesk.testing.FakeIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
class DockWindowSelectionTest {
    private val dispatcher = StandardTestDispatcher()
    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun teardown() = Dispatchers.resetMain()

    @Test
    fun projectWindowBelongsOnlyToItsCustomShortcutAcrossMultipleInstancesForAnyApp() = runTest(dispatcher) {
        listOf("Android Studio", "Any Editor", "Document App").forEach { app ->
            val bundle = "/Applications/$app.app"
            val favorite = PinnedAppShortcut("project-$app", app, bundle, projectPath = "/projects/work")
            val host = PairedHost(app, "Mac", "localhost", 8741, OsType.MACOS, 0, "token", pinnedApps = listOf(favorite))
            val project = RunningApp("work", app, pid = 10, stableWindowId = "1", windowTitle = "work", bundlePath = bundle,
                isActive = true, monitorIndex = 1)
            val gateway = FakeAgentGateway(apps = listOf(project))
            val realtime = FakeAgentRealtime(apps = gateway.apps)
            val preferences = FakeHostPreferences(listOf(host))
            val vm = DeskViewModel(preferences, gateway, realtime, ExecuteGridButtonUseCase(gateway), FakeIconCache(), backgroundScope)
            runCurrent()
            val custom = vm.uiState.value.buttons.single { it.isFavoriteShortcut }
            val standard = vm.uiState.value.buttons.single { !it.isFavoriteShortcut }
            assertEquals(1, vm.uiState.value.buttons.count { it.isActive })
            assertTrue(custom.isActive)
            assertFalse(standard.isVisible)
            assertEquals(null, standard.payload["stableWindowId"])
            vm.onIntent(DeskIntent.ResizeAppTile(custom.id))
            vm.onIntent(DeskIntent.ResizeAppTile(standard.id))
            runCurrent()
            val saved = preferences.getHosts().single()
            val other = project.copy(stableWindowId = "2", windowTitle = "other project", isActive = false, monitorIndex = 2)
            gateway.apps = listOf(project, other)
            realtime.emitApps(gateway.apps)
            runCurrent()
            val updated = vm.uiState.value.buttons.single { it.id == standard.id }
            assertEquals("2", updated.payload["stableWindowId"])
            assertEquals("1", updated.payload["instanceCount"])
            assertEquals(1, vm.uiState.value.buttons.count { it.isActive })
            vm.onButtonClick(updated)
            runCurrent()
            assertEquals(null, vm.uiState.value.windowSelection)
            assertEquals("2", realtime.launched.single().payload["stableWindowId"])
            assertEquals(saved.appOrder, preferences.getHosts().single().appOrder)
            assertEquals(saved.appTileSizes, preferences.getHosts().single().appTileSizes)
        }
    }

    @Test
    fun emptyRealtimeSnapshotClearsActivityWithoutRemovingOrResizingLauncher() = runTest(dispatcher) {
        val app = RunningApp("Editor", "Editor", pid = 10, stableWindowId = "1", bundlePath = "/Applications/Editor.app")
        val host = PairedHost("host", "Mac", "localhost", 8741, OsType.MACOS, 0, "token")
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = listOf(app))
        val realtime = FakeAgentRealtime(apps = listOf(app))
        val vm = DeskViewModel(preferences, gateway, realtime, ExecuteGridButtonUseCase(gateway), FakeIconCache(), backgroundScope)
        runCurrent()
        val id = vm.uiState.value.buttons.single().id
        vm.onIntent(DeskIntent.ResizeAppTile(id))
        runCurrent()
        realtime.emitApps(emptyList())
        runCurrent()
        val closed = vm.uiState.value.buttons.single()
        assertEquals(id, closed.id)
        assertEquals("2", closed.payload["spanColumns"])
        assertFalse(closed.isVisible)
        assertFalse(closed.isActive)
        assertEquals(null, closed.payload["stableWindowId"])
    }

    @Test
    fun restartingOfflineRestoresPersistedLaunchersWithoutRuntimeWindowState() = runTest(dispatcher) {
        val shortcut = ApplicationShortcut("persistent", "/applications/editor.app", "Editor", "Editor", "/Applications/Editor.app")
        val host = PairedHost("host", "Mac", "localhost", 8741, OsType.MACOS, 0, "token",
            applicationShortcuts = listOf(shortcut), appOrder = listOf(shortcut.id),
            appTileSizes = mapOf("card:${shortcut.id}" to AppTileSize(1, 2)))
        val gateway = FakeAgentGateway(failWith = IllegalStateException("offline"))
        val vm = DeskViewModel(FakeHostPreferences(listOf(host)), gateway, FakeAgentRealtime(),
            ExecuteGridButtonUseCase(gateway), FakeIconCache(), backgroundScope)
        runCurrent()
        assertEquals(shortcut.id, vm.uiState.value.buttons.single().id)
        assertEquals("2", vm.uiState.value.buttons.single().payload["spanRows"])
        assertFalse(vm.uiState.value.buttons.single().isVisible)
    }

    @Test
    fun selectionTargetsExactInstanceAndClosingItPreservesLauncherLayoutForAnyApp() = runTest(dispatcher) {
        listOf("Editor", "Preview", "Studio", "Unrelated App").forEach { name ->
            val bundle = "/Applications/$name.app"
            val windows = listOf("101", "202").mapIndexed { i, id ->
                RunningApp("Same title", name, pid = 10 + i, stableWindowId = id,
                    windowTitle = "Same title", bundlePath = bundle, isVisible = true, monitorIndex = i + 1)
            }
            val host = PairedHost(name, name, "localhost", 8741, OsType.MACOS, 0, "token")
            val preferences = FakeHostPreferences(listOf(host))
            val gateway = FakeAgentGateway(apps = windows)
            val realtime = FakeAgentRealtime(apps = windows)
            val vm = DeskViewModel(preferences, gateway, realtime, ExecuteGridButtonUseCase(gateway), FakeIconCache(), backgroundScope)
            runCurrent()
            val tile = vm.uiState.value.buttons.single()
            vm.onIntent(DeskIntent.ResizeAppTile(tile.id))
            runCurrent()
            val order = preferences.getHosts().single().appOrder
            vm.onButtonClick(tile)
            assertEquals(2, vm.uiState.value.windowSelection?.windows?.size)
            assertTrue(realtime.launched.isEmpty())
            vm.onIntent(DeskIntent.SelectWindow("window-quartz-202"))
            runCurrent()
            assertEquals("202", realtime.launched.single().payload["stableWindowId"])
            assertEquals("11", realtime.launched.single().payload["pid"])
            vm.onAppClose(vm.uiState.value.buttons.single())
            assertTrue(vm.uiState.value.windowSelection?.close == true)
            realtime.apps = listOf(windows.first())
            gateway.apps = realtime.apps
            vm.onIntent(DeskIntent.SelectWindow("window-quartz-202"))
            runCurrent()
            assertEquals("202", realtime.closed.single().payload["stableWindowId"])
            assertEquals("101", vm.uiState.value.buttons.single().payload["stableWindowId"])
            assertEquals(tile.id, vm.uiState.value.buttons.single().id)
            assertEquals("2", vm.uiState.value.buttons.single().payload["spanColumns"])
            assertEquals(order, preferences.getHosts().single().appOrder)
            assertEquals(null, vm.uiState.value.windowSelection)
        }
    }

    @Test
    fun removedWindowIsNotReplacedByAnotherInstanceWhenSelected() = runTest(dispatcher) {
        val windows = listOf("1", "2").map { id -> RunningApp("Editor", "Editor", pid = 10,
            stableWindowId = id, windowTitle = "same", bundlePath = "/Applications/Editor.app") }
        val host = PairedHost("host", "Mac", "localhost", 8741, OsType.MACOS, 0, "token")
        val gateway = FakeAgentGateway(apps = windows)
        val realtime = FakeAgentRealtime(apps = windows)
        val vm = DeskViewModel(FakeHostPreferences(listOf(host)), gateway, realtime,
            ExecuteGridButtonUseCase(gateway), FakeIconCache(), backgroundScope)
        runCurrent()
        vm.onButtonClick(vm.uiState.value.buttons.single())
        gateway.apps = listOf(windows.first())
        vm.refreshRunningApps()
        runCurrent()
        vm.onIntent(DeskIntent.SelectWindow("window-quartz-2"))
        runCurrent()
        assertTrue(realtime.launched.isEmpty())
        assertTrue(vm.uiState.value.feedback.orEmpty().contains("não está mais disponível"))
    }
}
