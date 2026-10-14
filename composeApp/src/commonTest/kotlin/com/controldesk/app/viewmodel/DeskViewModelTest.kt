package com.controldesk.app.viewmodel

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.PinnedAppShortcut
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.testing.FakeAgentGateway
import com.controldesk.testing.FakeAgentRealtime
import com.controldesk.testing.FakeHostPreferences
import com.controldesk.testing.FakeIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DeskViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
    }

    @Test
    fun reorganizeModeIsControlledByMviIntent() = runTest(dispatcher) {
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(),
            agentGateway = FakeAgentGateway(),
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(FakeAgentGateway()),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )

        viewModel.onIntent(DeskIntent.SetReorganizingApps(true))
        assertTrue(viewModel.uiState.value.isReorganizingApps)
        viewModel.onIntent(DeskIntent.SelectAppForReorder("app-1"))
        assertEquals("app-1", viewModel.uiState.value.selectedAppIdForReorder)
        viewModel.onIntent(DeskIntent.MoveSelectedAppBy(25f, -4f))
        assertEquals(25f, viewModel.uiState.value.selectedAppDragOffsetX)
        assertEquals(-4f, viewModel.uiState.value.selectedAppDragOffsetY)
        viewModel.onIntent(DeskIntent.FinishAppReorderDrag)
        assertEquals(0f, viewModel.uiState.value.selectedAppDragOffsetX)
        assertEquals(0f, viewModel.uiState.value.selectedAppDragOffsetY)
        assertEquals(null, viewModel.uiState.value.selectedAppIdForReorder)
        assertTrue(viewModel.uiState.value.isReorganizingApps)
        viewModel.onIntent(DeskIntent.SetReorganizingApps(false))
        assertFalse(viewModel.uiState.value.isReorganizingApps)
        assertEquals(null, viewModel.uiState.value.selectedAppIdForReorder)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun buttonClickMarksTileActiveImmediately() = runTest(dispatcher) {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "tok",
        )
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(name = "Safari", appName = "Safari", isActive = false, isVisible = true),
                RunningApp(name = "Chrome", appName = "Chrome", isActive = true, isVisible = true),
            ),
        )
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        assertEquals(listOf("Chrome", "Safari"), viewModel.uiState.value.buttons.map { it.label })
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onButtonClick(safari)
        assertEquals(listOf("Safari"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })
        runCurrent()
        assertEquals("Safari", realtime.launched.last().label)
    }

    @Test
    fun manualRefreshShowsLoadingImmediatelyAndClearsItAfterAppsLoad() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway(
            apps = listOf(RunningApp(name = "Safari", appName = "Safari", isActive = true, isVisible = true)),
        )
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoadingApps)

        viewModel.refreshRunningApps()

        assertTrue(viewModel.uiState.value.isLoadingApps)
        runCurrent()
        assertFalse(viewModel.uiState.value.isLoadingApps)
    }

    @Test
    fun pinnedAppsPersistOrderAndProjectLaunchTarget() = runTest(dispatcher) {
        val shortcut = PinnedAppShortcut(
            id = "/Applications/Android Studio.app",
            name = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(shortcut))
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway()
        val realtime = FakeAgentRealtime()
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        assertEquals(listOf("Android Studio"), viewModel.uiState.value.buttons.map { it.label })
        assertEquals("/Users/test/Projects/sample-android-app", preferences.getHosts().single().pinnedApps.single().projectPath)
        viewModel.onButtonClick(viewModel.uiState.value.buttons.single())
        runCurrent()

        assertEquals("/Applications/Android Studio.app", realtime.launched.single().payload["bundlePath"])
        assertEquals("/Users/test/Projects/sample-android-app", realtime.launched.single().payload["projectPath"])
    }

    @Test
    fun installedAppsFailureRemainsVisibleForShortcutManager() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway()
        val viewModel = PinnedAppsViewModel(gateway, FakeHostPreferences(listOf(host)))
        gateway.failWith = ControlDeskError.Generic("Agente do Mac desatualizado")

        viewModel.onIntent(PinnedAppsIntent.LoadInstalledApps(host))
        runCurrent()

        assertEquals("Agente do Mac desatualizado", viewModel.uiState.value.appsError)
        assertFalse(viewModel.uiState.value.isLoadingApps)
    }

    @Test
    fun choosingRemoteFileSavesProjectPathThroughMviIntent() = runTest(dispatcher) {
        val app = PinnedAppShortcut("studio", "Android Studio", "/Applications/Android Studio.app")
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(app))
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway()
        val viewModel = PinnedAppsViewModel(gateway, preferences)

        viewModel.onIntent(PinnedAppsIntent.LoadInstalledApps(host))
        viewModel.onIntent(PinnedAppsIntent.ChoosePathFor(app.id))
        runCurrent()
        assertEquals("/Users/test", viewModel.uiState.value.fileListing?.path)

        viewModel.onIntent(PinnedAppsIntent.SelectPath("/Users/test/Projects/sample-android-app"))
        runCurrent()

        assertEquals("/Users/test/Projects/sample-android-app", preferences.getHosts().single().pinnedApps.single().projectPath)
        assertEquals(null, viewModel.uiState.value.selectedAppId)
    }

    @Test
    fun userReorderingPinnedAppsPersistsTheChosenOrder() = runTest(dispatcher) {
        val first = PinnedAppShortcut("editor", "Editor", "/Applications/Editor.app", order = 0)
        val second = PinnedAppShortcut("studio", "Studio", "/Applications/Studio.app", order = 1)
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(first, second))
        val preferences = FakeHostPreferences(listOf(host))
        val viewModel = PinnedAppsViewModel(FakeAgentGateway(), preferences)

        viewModel.onIntent(PinnedAppsIntent.LoadInstalledApps(host))
        viewModel.onIntent(PinnedAppsIntent.MoveAppTo(second.id, 0))
        runCurrent()

        assertEquals(listOf("studio", "editor"), preferences.getHosts().single().pinnedApps.sortedBy { it.order }.map { it.id })
    }

    @Test
    fun pinnedPositionsStayStableWhenRunningAppListChanges() = runTest(dispatcher) {
        val first = PinnedAppShortcut("editor", "Editor", "/Applications/Editor.app", order = 0)
        val second = PinnedAppShortcut("studio", "Studio", "/Applications/Studio.app", order = 1)
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(first, second))
        val gateway = FakeAgentGateway(apps = listOf(RunningApp("Safari", "Safari", isVisible = true)))
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val before = viewModel.uiState.value.buttons.filter { it.isFavoriteShortcut }.map { it.payload["pinnedAppId"] }

        gateway.apps = listOf(RunningApp("Calendar", "Calendar", isVisible = true), RunningApp("Terminal", "Terminal", isVisible = true))
        viewModel.refreshRunningApps()
        runCurrent()

        val after = viewModel.uiState.value.buttons.filter { it.isFavoriteShortcut }.map { it.payload["pinnedAppId"] }
        assertEquals(listOf("editor", "studio"), before)
        assertEquals(before, after)
    }

    @Test
    fun pinnedShortcutReusesItsRunningAppCardAndKeepsWindowDetails() = runTest(dispatcher) {
        val shortcut = PinnedAppShortcut(
            id = "android-studio",
            name = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(shortcut))
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(
                    name = "Android Studio · sample-android-app",
                    appName = "Android Studio",
                    pid = 321,
                    windowTitle = "sample-android-app",
                    windowIndex = 0,
                    axWindowIndex = 0,
                    windowNumber = 42,
                    isActive = true,
                    isVisible = true,
                    monitorIndex = 2,
                ),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )

        runCurrent()

        val studioCards = viewModel.uiState.value.buttons.filter { it.payload["target"] == "Android Studio" }
        assertEquals(1, studioCards.size)
        val studio = studioCards.single()
        assertTrue(studio.isFavoriteShortcut)
        assertEquals("Android Studio · sample-android-app", studio.label)
        assertTrue(studio.isVisible)
        assertTrue(studio.isActive)
        assertEquals(2, studio.monitorIndex)
        assertEquals("321", studio.payload["pid"])
        assertEquals(shortcut.bundlePath, studio.payload["bundlePath"])
        assertEquals(shortcut.projectPath, studio.payload["projectPath"])
    }

    @Test
    fun pinnedShortcutMatchesRunningAppByBundleWhenProcessNamesDiffer() = runTest(dispatcher) {
        val shortcut = PinnedAppShortcut(
            id = "android-studio",
            name = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(shortcut))
        val running = RunningApp(
            name = "studio · sample-android-app",
            appName = "studio",
            pid = 321,
            windowTitle = "sample-android-app",
            axWindowIndex = 2,
            isActive = true,
            isVisible = true,
            bundlePath = shortcut.bundlePath,
        )
        val gateway = FakeAgentGateway(apps = listOf(running))
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )

        runCurrent()

        val studioCards = viewModel.uiState.value.buttons.filter {
            it.payload["bundlePath"] == shortcut.bundlePath
        }
        assertEquals(1, studioCards.size)
        assertTrue(studioCards.single().isFavoriteShortcut)
        assertEquals("321", studioCards.single().payload["pid"])
        assertEquals("2", studioCards.single().payload["axWindowIndex"])
        assertEquals(shortcut.projectPath, studioCards.single().payload["projectPath"])
    }

    @Test
    fun reorderingWholeGridPersistsEveryAppAndRefreshKeepsTheirOrder() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp("Safari", "Safari", isVisible = true, monitorIndex = 1),
                RunningApp("Notes", "Notes", isActive = true, isVisible = true, monitorIndex = 2),
                RunningApp("Terminal", "Terminal", isVisible = true, monitorIndex = 1),
            ),
        )
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val preferences = FakeHostPreferences(listOf(host))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val initial = viewModel.uiState.value.buttons
        val chosenOrder = listOf(initial[2].id, initial[0].id, initial[1].id)

        viewModel.onIntent(DeskIntent.ReorderApps(chosenOrder))
        runCurrent()

        assertEquals(chosenOrder, viewModel.uiState.value.buttons.map { it.id })
        assertEquals(chosenOrder, preferences.getHosts().single().appOrder.take(3))

        gateway.apps = gateway.apps.reversed() + RunningApp("Calendar", "Calendar", isVisible = true)
        viewModel.refreshRunningApps()
        runCurrent()

        val refreshed = viewModel.uiState.value.buttons
        assertEquals(chosenOrder, refreshed.take(3).map { it.id })
        assertEquals("Calendar", refreshed.last().label)

        realtime.apps = gateway.apps
        realtime.emitApps(gateway.apps.reversed())
        runCurrent()

        val afterRealtime = viewModel.uiState.value.buttons
        assertEquals(chosenOrder, afterRealtime.take(3).map { it.id })
        val activeNotes = afterRealtime.single { it.label == "Notes" }
        assertTrue(activeNotes.isActive)
        assertEquals(2, activeNotes.monitorIndex)
        assertEquals(chosenOrder, preferences.getHosts().single().appOrder.take(3))
    }

    @Test
    fun previewReordersCardsImmediatelyAndOnlyCommitPersistsTheOrder() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp("Safari", "Safari", isVisible = true),
                RunningApp("Notes", "Notes", isVisible = true),
                RunningApp("Terminal", "Terminal", isVisible = true),
            ),
        )
        val preferences = FakeHostPreferences(listOf(host))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val initialOrder = viewModel.uiState.value.buttons.map { it.id }
        val movedId = initialOrder[2]
        viewModel.onIntent(DeskIntent.SetReorganizingApps(true))
        viewModel.onIntent(DeskIntent.SelectAppForReorder(movedId, cellWidth = 100f, cellHeight = 100f))

        viewModel.onIntent(DeskIntent.PreviewReorderApp(movedId, targetIndex = 0))

        val previewOrder = viewModel.uiState.value.buttons.map { it.id }
        assertEquals(listOf(initialOrder[2], initialOrder[0], initialOrder[1]), previewOrder)
        assertEquals(initialOrder, preferences.getHosts().single().appOrder.take(3))

        viewModel.onIntent(DeskIntent.ReorderApps(previewOrder))
        runCurrent()

        assertEquals(previewOrder, preferences.getHosts().single().appOrder.take(3))
    }

    @Test
    fun appOrderSurvivesDockShortcutBecomingAnOpenWindowAfterSwitchingApps() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp("Android Studio", "Android Studio", isVisible = false, isDockPinned = true, dockIndex = 0),
                RunningApp("Safari", "Safari", isVisible = true),
                RunningApp("Terminal", "Terminal", isVisible = true),
            ),
        )
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val preferences = FakeHostPreferences(listOf(host))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val initial = viewModel.uiState.value.buttons
        val chosenOrder = listOf(
            initial.single { it.label == "Terminal" }.id,
            initial.single { it.label == "Android Studio" }.id,
            initial.single { it.label == "Safari" }.id,
        )
        viewModel.onIntent(DeskIntent.ReorderApps(chosenOrder))
        runCurrent()

        gateway.apps = listOf(
            RunningApp(
                "Android Studio · sample-android-app", "Android Studio", pid = 321,
                windowTitle = "sample-android-app", axWindowIndex = 0,
                isActive = true, isVisible = true,
            ),
            RunningApp("Safari", "Safari", isVisible = true),
            RunningApp("Terminal", "Terminal", isVisible = true),
        )
        realtime.apps = gateway.apps
        viewModel.onButtonClick(viewModel.uiState.value.buttons.single { it.label == "Safari" })
        runCurrent()

        assertEquals(
            listOf("Terminal", "Android Studio · sample-android-app", "Safari"),
            viewModel.uiState.value.buttons.map { it.label },
        )
        assertEquals(
            viewModel.uiState.value.buttons.map { it.id },
            preferences.getHosts().single().appOrder.take(3),
        )
    }

    @Test
    fun appOrderSurvivesProcessNameAndPidChangesForSameBundle() = runTest(dispatcher) {
        val studioBundle = "/Applications/Android Studio.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(
                    name = "Android Studio · sample-android-app",
                    appName = "Android Studio",
                    pid = 321,
                    windowTitle = "sample-android-app",
                    axWindowIndex = 1,
                    isVisible = true,
                    bundlePath = studioBundle,
                ),
                RunningApp("Safari", "Safari", pid = 456, isVisible = true),
                RunningApp("Terminal", "Terminal", pid = 789, isVisible = true),
            ),
        )
        val preferences = FakeHostPreferences(listOf(host))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val initial = viewModel.uiState.value.buttons
        val chosenOrder = listOf(
            initial.single { it.label == "Terminal" }.id,
            initial.single { it.label.contains("Android Studio") }.id,
            initial.single { it.label == "Safari" }.id,
        )
        viewModel.onIntent(DeskIntent.ReorderApps(chosenOrder))
        runCurrent()

        gateway.apps = listOf(
            RunningApp(
                name = "studio · sample-android-app",
                appName = "studio",
                pid = 987,
                windowTitle = "sample-android-app",
                axWindowIndex = 2,
                isActive = true,
                isVisible = true,
                bundlePath = studioBundle,
            ),
            RunningApp("Safari", "Safari", pid = 456, isVisible = true),
            RunningApp("Terminal", "Terminal", pid = 789, isVisible = true),
        )
        viewModel.refreshRunningApps()
        runCurrent()

        assertEquals(
            listOf("Terminal", "studio · sample-android-app", "Safari"),
            viewModel.uiState.value.buttons.map { it.label },
        )
        assertEquals(
            viewModel.uiState.value.buttons.map { it.id },
            preferences.getHosts().single().appOrder.take(3),
        )
    }

    @Test
    fun longPressRemovesTilesAndClosesApp() = runTest(dispatcher) {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "tok",
        )
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(name = "Safari", appName = "Safari", isActive = true, isVisible = true),
                RunningApp(name = "Notes", appName = "Notes", isActive = false, isVisible = true),
            ),
        )
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onAppClose(safari)
        assertEquals(listOf("Notes"), viewModel.uiState.value.buttons.map { it.label })
        runCurrent()
        assertEquals("Safari", realtime.closed.single().payload["target"])
        assertEquals(listOf("Notes"), viewModel.uiState.value.buttons.map { it.label })
    }

    @Test
    fun closeKeepsActiveAppHiddenWhilePollStillSeesIt() = runTest(dispatcher) {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "tok",
        )
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(name = "Safari", appName = "Safari", isActive = true, isVisible = true),
                RunningApp(name = "Notes", appName = "Notes", isActive = false, isVisible = true),
            ),
            removeOnClose = false,
        )
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onAppClose(safari)
        runCurrent()
        assertEquals("Safari", realtime.closed.single().payload["target"])
        assertEquals(listOf("Notes"), viewModel.uiState.value.buttons.map { it.label })
    }

    @Test
    fun closingDockAppKeepsDimmedPlaceholderNotActive() = runTest(dispatcher) {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "tok",
        )
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(
                    name = "Safari",
                    appName = "Safari",
                    isActive = true,
                    isVisible = true,
                    isDockPinned = true,
                    dockIndex = 0,
                ),
                RunningApp(name = "Notes", appName = "Notes", isActive = false, isVisible = true, isDockPinned = true, dockIndex = 1),
            ),
            removeOnClose = false,
        )
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onAppClose(safari)
        val after = viewModel.uiState.value.buttons
        assertEquals(listOf("Safari", "Notes"), after.map { it.label })
        val placeholder = after.first { it.label == "Safari" }
        assertEquals("app-Safari-dock", placeholder.id)
        assertEquals(false, placeholder.isActive)
        assertEquals(false, placeholder.isVisible)
        assertEquals(true, placeholder.isDockPinned)
        runCurrent()
        val polled = viewModel.uiState.value.buttons.first { it.payload["target"] == "Safari" }
        assertEquals("app-Safari-dock", polled.id)
        assertEquals(false, polled.isActive)
        assertEquals(false, polled.isVisible)
    }

    @Test
    fun switchingHostHighlightsThatPcActiveWindow() = runTest(dispatcher) {
        val pc1 = pairedHost("10.0.0.1:8741", "Studio", 0)
        val pc2 = pairedHost("10.0.0.2:8741", "Laptop", 1)
        val gateway = FakeAgentGateway(
            appsByHost = mapOf(
                pc1.id to listOf(
                    RunningApp(name = "Chrome", appName = "Chrome", isActive = true, isVisible = true),
                    RunningApp(name = "Safari", appName = "Safari", isActive = false, isVisible = true),
                ),
                pc2.id to listOf(
                    RunningApp(name = "Notes", appName = "Notes", isActive = true, isVisible = true),
                    RunningApp(name = "Mail", appName = "Mail", isActive = false, isVisible = true),
                ),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(pc1, pc2)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        assertEquals(listOf("Chrome"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })

        viewModel.selectHost(1)
        runCurrent()
        assertEquals("Laptop", viewModel.uiState.value.selectedHost?.name)
        assertEquals(listOf("Notes"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })
        assertEquals(listOf("Mail", "Notes"), viewModel.uiState.value.buttons.map { it.label })

        viewModel.selectHost(0)
        runCurrent()
        assertEquals(listOf("Chrome"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })
    }

    @Test
    fun switchingHostDoesNotKeepStaleActiveWhenAgentOmitsFlag() = runTest(dispatcher) {
        val pc1 = pairedHost("10.0.0.1:8741", "Studio", 0)
        val pc2 = pairedHost("10.0.0.2:8741", "Laptop", 1)
        val gateway = FakeAgentGateway(
            appsByHost = mapOf(
                pc1.id to listOf(
                    RunningApp(name = "Chrome", appName = "Chrome", isActive = true, isVisible = true),
                ),
                pc2.id to listOf(
                    RunningApp(name = "Chrome", appName = "Chrome", isActive = false, isVisible = true),
                    RunningApp(name = "Notes", appName = "Notes", isActive = false, isVisible = true),
                ),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(pc1, pc2)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        viewModel.selectHost(1)
        runCurrent()
        assertEquals(emptyList(), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })
        assertEquals(listOf("Chrome", "Notes"), viewModel.uiState.value.buttons.map { it.label })
    }

    @Test
    fun keepsSingleActiveHighlightWhenAgentReportsMultiple() = runTest(dispatcher) {
        val host = pairedHost("10.0.0.1:8741", "Mac", 0)
        val gateway = FakeAgentGateway(
            appsByHost = mapOf(
                host.id to listOf(
                    RunningApp(name = "Chrome", appName = "Chrome", isActive = true, isVisible = true),
                    RunningApp(name = "Safari", appName = "Safari", isActive = true, isVisible = true),
                ),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        assertEquals(1, viewModel.uiState.value.buttons.count { it.isActive })
        assertEquals("Chrome", viewModel.uiState.value.buttons.first { it.isActive }.label)
    }

    private fun pairedHost(id: String, name: String, order: Int) = PairedHost(
        id = id,
        name = name,
        host = id.substringBefore(':'),
        port = 8741,
        osType = OsType.MACOS,
        sortOrder = order,
        token = "tok",
    )
}
