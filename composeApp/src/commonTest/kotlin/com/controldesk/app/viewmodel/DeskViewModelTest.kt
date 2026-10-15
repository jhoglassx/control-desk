package com.controldesk.app.viewmodel

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.AppTileSize
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
    fun hostLayoutPreferenceUpdatesDoNotReloadRunningApps() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(
            apps = listOf(RunningApp(name = "Safari", appName = "Safari", isVisible = true)),
        )
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()
        val loadsBeforeLayoutUpdate = gateway.runningAppsLoadCount
        assertTrue(loadsBeforeLayoutUpdate > 0)

        preferences.saveHost(host.copy(appOrder = listOf("Safari")))
        runCurrent()

        assertEquals(loadsBeforeLayoutUpdate, gateway.runningAppsLoadCount)
        assertEquals(listOf("Safari"), viewModel.uiState.value.selectedHost?.appOrder)
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
        assertEquals(listOf("Android Studio · sample-android-app"), viewModel.uiState.value.buttons.map { it.label })
        assertEquals("/Users/test/Projects/sample-android-app", preferences.getHosts().single().pinnedApps.single().projectPath)
        viewModel.onButtonClick(viewModel.uiState.value.buttons.single())
        runCurrent()

        assertEquals("/Applications/Android Studio.app", realtime.launched.single().payload["bundlePath"])
        assertEquals("/Users/test/Projects/sample-android-app", realtime.launched.single().payload["projectPath"])
    }

    @Test
    fun resizingAppTileCyclesAndPersistsItsGridSpan() = runTest(dispatcher) {
        val bundlePath = "/Applications/Android Studio.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(
                    name = "Android Studio · sample-android-app",
                    appName = "Android Studio",
                    pid = 321,
                    bundlePath = bundlePath,
                    isVisible = true,
                ),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val buttonId = viewModel.uiState.value.buttons.single().id
        repeat(2) { viewModel.onIntent(DeskIntent.ResizeAppTile(buttonId)) }
        runCurrent()

        val savedSize = preferences.getHosts().single().appTileSizes["card:$buttonId"]
        assertEquals(2, savedSize?.columns)
        assertEquals(2, savedSize?.rows)
        assertEquals("2", viewModel.uiState.value.buttons.single().payload["spanColumns"])
        assertEquals("2", viewModel.uiState.value.buttons.single().payload["spanRows"])

        viewModel.onIntent(DeskIntent.ResizeAppTile(buttonId))
        runCurrent()
        val tallSize = preferences.getHosts().single().appTileSizes["card:$buttonId"]
        assertEquals(1, tallSize?.columns)
        assertEquals(2, tallSize?.rows)
    }

    @Test
    fun resizedDefaultShortcutKeepsSizeWhenCloseSnapshotOmitsDockAndAppReopens() = runTest(dispatcher) {
        val applications = listOf(
            "Visual Studio Code" to "Code",
            "Android Studio" to "studio",
            "Preview" to "Preview",
            "Any Editor" to "arbitrary-process",
        )
        for ((bundleName, processName) in applications) {
            val bundlePath = "/Applications/$bundleName.app"
            val host = pairedHost("10.0.2.2:8741", "Mac", 0)
            val original = RunningApp(
                name = "$bundleName · Workspace A",
                appName = processName,
                pid = 321,
                windowTitle = "Workspace A",
                windowNumber = 41,
                stableWindowId = "41",
                bundlePath = bundlePath,
                isVisible = true,
                isDockPinned = true,
            )
            val preferences = FakeHostPreferences(listOf(host))
            val gateway = FakeAgentGateway(apps = listOf(original))
            val realtime = FakeAgentRealtime(apps = listOf(original))
            val viewModel = DeskViewModel(
                hostPreferences = preferences,
                agentGateway = gateway,
                agentRealtime = realtime,
                executeGridButton = ExecuteGridButtonUseCase(gateway),
                iconCache = FakeIconCache(),
                coroutineScope = backgroundScope,
            )
            runCurrent()

            val originalButton = viewModel.uiState.value.buttons.single()
            repeat(2) { viewModel.onIntent(DeskIntent.ResizeAppTile(originalButton.id)) }
            runCurrent()
            val savedPosition = preferences.getHosts().single().appGridPositions[originalButton.id]

            // Some agent snapshots omit Dock-only apps after the last window closes.
            gateway.apps = emptyList()
            realtime.apps = emptyList()
            viewModel.onAppClose(originalButton)
            runCurrent()

            val placeholderId = originalButton.id
            assertEquals(
                AppTileSize(2, 2),
                preferences.getHosts().single().appTileSizes["card:$placeholderId"],
            )

            val reopened = original.copy(
                name = "$bundleName · Workspace B",
                pid = 777,
                windowTitle = "Workspace B",
                windowNumber = 92,
                stableWindowId = "92",
                isActive = true,
            )
            gateway.apps = listOf(reopened)
            realtime.apps = listOf(reopened)
            viewModel.refreshRunningApps()
            runCurrent()

            val reopenedButton = viewModel.uiState.value.buttons.single()
            assertEquals("2", reopenedButton.payload["spanColumns"])
            assertEquals("2", reopenedButton.payload["spanRows"])
            assertEquals(savedPosition, preferences.getHosts().single().appGridPositions[reopenedButton.id])
        }
    }

    @Test
    fun resizedDefaultShortcutKeepsItsLayoutWhenClosedAndReopenedWithNewProcessId() = runTest(dispatcher) {
        val bundlePath = "/Applications/Android Studio.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val originalWindow = RunningApp(
            name = "Android Studio · BodyTemperature",
            appName = "Android Studio",
            pid = 321,
            windowTitle = "BodyTemperature",
            windowNumber = 41,
            bundlePath = bundlePath,
            isActive = true,
            isVisible = true,
        )
        val notes = RunningApp("Notes", "Notes", pid = 654, isVisible = true)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = listOf(originalWindow, notes))
        val realtime = FakeAgentRealtime(apps = listOf(originalWindow, notes))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val studioButton = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }
        viewModel.onIntent(DeskIntent.ResizeAppTile(studioButton.id))
        viewModel.onIntent(DeskIntent.ResizeAppTile(studioButton.id))
        runCurrent()
        val savedPosition = preferences.getHosts().single().appGridPositions[studioButton.id]

        realtime.apps = listOf(notes)
        gateway.apps = listOf(notes)
        viewModel.onAppClose(viewModel.uiState.value.buttons.single { it.id == studioButton.id })
        runCurrent()
        assertFalse(viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }.isVisible)

        val reopenedWindow = originalWindow.copy(pid = 777, windowNumber = 92)
        realtime.apps = listOf(reopenedWindow, notes)
        gateway.apps = realtime.apps
        viewModel.refreshRunningApps()
        runCurrent()

        val reopenedButton = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }
        val savedHost = preferences.getHosts().single()
        assertEquals("2", reopenedButton.payload["spanColumns"])
        assertEquals("2", reopenedButton.payload["spanRows"])
        assertEquals(savedPosition, savedHost.appGridPositions[reopenedButton.id])
        assertEquals(reopenedButton.id, savedHost.appOrder[savedPosition!!])
    }

    @Test
    fun defaultTilesKeepTheirLayoutWhenRuntimeIdentityChangesAcrossApps() = runTest(dispatcher) {
        data class AppCase(
            val oldName: String,
            val currentName: String,
            val bundlePath: String?,
            val size: AppTileSize,
        )

        val cases = listOf(
            AppCase("Visual Studio Code", "Code", "/Applications/Visual Studio Code.app", AppTileSize(1, 2)),
            AppCase("Android Studio", "studio", "/Applications/Android Studio.app", AppTileSize(2, 2)),
            AppCase("Preview", "Preview", "/System/Applications/Preview.app", AppTileSize(2, 1)),
            AppCase("Notes", "Notes", null, AppTileSize(1, 2)),
        )
        val oldIds = cases.mapIndexed { index, _ -> "old-window-$index" }
        val resizedIds = cases.mapIndexed { index, _ -> "resized-window-$index" }
        val identities = cases.flatMapIndexed { index, app ->
            val appKey = app.bundlePath?.lowercase() ?: app.oldName.lowercase()
            listOf(
                oldIds[index] to "window:$appKey:title:old-title-$index",
                resizedIds[index] to "window:$appKey:title:resized-title-$index",
            )
        }.toMap()
        val positions = cases.indices
            .flatMap { listOf(oldIds[it], resizedIds[it]) }
            .mapIndexed { index, id -> id to index }
            .toMap()
        val sizes = cases.flatMapIndexed { index, app ->
            val appKey = app.bundlePath?.lowercase() ?: app.oldName.lowercase()
            listOf(
                "window:$appKey:title:old-title-$index" to AppTileSize(),
                "window:$appKey:title:resized-title-$index" to app.size,
            )
        }.toMap()
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(
            appOrder = cases.indices.flatMap { listOf(oldIds[it], resizedIds[it]) },
            appGridPositions = positions,
            appTileSizes = sizes,
            appLayoutIdentities = identities,
        )
        val currentApps = cases.mapIndexed { index, app ->
            RunningApp(
                name = "${app.currentName} · new-title-$index",
                appName = app.currentName,
                pid = 987 + index,
                windowTitle = "new-title-$index",
                bundlePath = app.bundlePath,
                isVisible = true,
            )
        }
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = currentApps)
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = currentApps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val currentButtons = viewModel.uiState.value.buttons.associateBy {
            it.payload["bundlePath"] ?: it.payload["target"]
        }
        cases.forEachIndexed { index, app ->
            val currentButton = currentButtons.getValue(app.bundlePath ?: app.currentName)
            assertEquals(app.size.columns.toString(), currentButton.payload["spanColumns"])
            assertEquals(app.size.rows.toString(), currentButton.payload["spanRows"])
            assertEquals(index * 2 + 1, preferences.getHosts().single().appGridPositions[currentButton.id])
        }
    }

    @Test
    fun tileSizeSurvivesWindowTitleAndGlobalWindowNumberChangesForAnyApp() = runTest(dispatcher) {
        val bundlePath = "/Applications/ChatGPT.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val initialWindow = RunningApp(
            name = "ChatGPT · First conversation",
            appName = "ChatGPT",
            pid = 321,
            windowTitle = "First conversation",
            windowIndex = 0,
            windowNumber = 41,
            bundlePath = bundlePath,
            isVisible = true,
        )
        val notes = RunningApp("Notes", "Notes", pid = 654, isVisible = true)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = listOf(initialWindow, notes))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val chatGpt = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }
        viewModel.onIntent(DeskIntent.ResizeAppTile(chatGpt.id))
        runCurrent()

        gateway.apps = listOf(
            initialWindow.copy(
                name = "ChatGPT · Second conversation",
                windowTitle = "Second conversation",
                windowNumber = 8,
            ),
            notes,
        )
        viewModel.refreshRunningApps()
        runCurrent()

        val refreshedChatGpt = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }
        assertEquals("2", refreshedChatGpt.payload["spanColumns"])
        assertEquals("1", refreshedChatGpt.payload["spanRows"])
        assertEquals(
            AppTileSize(2, 1),
            preferences.getHosts().single().appTileSizes[
                "card:${refreshedChatGpt.id}",
            ],
        )
    }

    @Test
    fun visualStudioShortcutKeepsSizeAndPositionWhenProjectAndWindowIndexChange() = runTest(dispatcher) {
        val bundlePath = "/Applications/Visual Studio Code.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val visualStudio = RunningApp(
            name = "Visual Studio Code · Workspace A",
            appName = "Visual Studio Code",
            pid = 321,
            windowTitle = "Workspace A",
            windowIndex = 2,
            windowNumber = 41,
            bundlePath = bundlePath,
            isVisible = true,
        )
        val notes = RunningApp("Notes", "Notes", pid = 654, isVisible = true)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = listOf(visualStudio, notes))
        val realtime = FakeAgentRealtime(apps = gateway.apps)
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val originalButton = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }
        viewModel.onIntent(DeskIntent.ResizeAppTile(originalButton.id))
        viewModel.onIntent(DeskIntent.ResizeAppTile(originalButton.id))
        runCurrent()
        val savedPosition = preferences.getHosts().single().appGridPositions[originalButton.id]

        realtime.apps = listOf(notes)
        gateway.apps = listOf(notes)
        viewModel.onAppClose(viewModel.uiState.value.buttons.single { it.id == originalButton.id })
        runCurrent()

        val reopened = visualStudio.copy(
            name = "Visual Studio Code · Workspace B",
            pid = 777,
            windowTitle = "Workspace B",
            windowIndex = 0,
            windowNumber = 3,
        )
        realtime.apps = listOf(reopened, notes)
        gateway.apps = realtime.apps
        viewModel.refreshRunningApps()
        runCurrent()

        val reopenedButton = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == bundlePath }
        val savedHost = preferences.getHosts().single()
        assertEquals("2", reopenedButton.payload["spanColumns"])
        assertEquals("2", reopenedButton.payload["spanRows"])
        assertEquals(savedPosition, savedHost.appGridPositions[reopenedButton.id])
        assertEquals(reopenedButton.id, savedHost.appOrder[savedPosition!!])
    }

    @Test
    fun singleWindowSizeSurvivesQuartzIdAndWindowTitleChanges() = runTest(dispatcher) {
        val original = RunningApp(
            name = "Visual Studio Code",
            appName = "Visual Studio Code",
            pid = 321,
            windowTitle = "Visual Studio Code",
            stableWindowId = "9535",
            bundlePath = "/Applications/Visual Studio Code.app",
            isVisible = true,
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val gateway = FakeAgentGateway(apps = listOf(original))
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val originalTile = viewModel.uiState.value.buttons.single { it.payload["stableWindowId"] == "9535" }
        viewModel.onIntent(DeskIntent.ResizeAppTile(originalTile.id))
        runCurrent()
        assertEquals("2", viewModel.uiState.value.buttons.single { it.id == originalTile.id }.payload["spanColumns"])

        gateway.apps = listOf(original.copy(
            name = "Code · Welcome",
            pid = 902,
            windowTitle = "Welcome",
            stableWindowId = "11001",
        ))
        viewModel.refreshRunningApps()
        runCurrent()

        val reopenedTile = viewModel.uiState.value.buttons.single { it.payload["stableWindowId"] == "11001" }
        assertEquals("2", reopenedTile.payload["spanColumns"])
        assertEquals("1", reopenedTile.payload["spanRows"])
    }

    @Test
    fun stableWindowTitleWinsOverReusedWindowNumberAfterAnotherAppCloses() = runTest(dispatcher) {
        val bundlePath = "/Applications/ChatGPT.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val chatGpt = RunningApp(
            name = "ChatGPT · Conversation",
            appName = "ChatGPT",
            pid = 321,
            windowTitle = "Conversation",
            windowNumber = 8,
            bundlePath = bundlePath,
            isVisible = true,
        )
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(apps = listOf(chatGpt))
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val button = viewModel.uiState.value.buttons.single()
        viewModel.onIntent(DeskIntent.ResizeAppTile(button.id))
        runCurrent()
        val savedHost = preferences.getHosts().single()
        preferences.saveHost(savedHost.copy(
            appTileSizes = savedHost.appTileSizes +
                ("window:${bundlePath.lowercase()}:number:7" to AppTileSize(1, 2)),
        ))
        runCurrent()

        gateway.apps = listOf(chatGpt.copy(windowNumber = 7))
        viewModel.refreshRunningApps()
        runCurrent()

        val refreshed = viewModel.uiState.value.buttons.single()
        assertEquals("2", refreshed.payload["spanColumns"])
        assertEquals("1", refreshed.payload["spanRows"])
    }

    @Test
    fun firstGridResizePersistsPositionsForEveryShortcut() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp("Finder", "Finder", pid = 10, isVisible = true),
                RunningApp("Notes", "Notes", pid = 20, isVisible = true),
                RunningApp("Safari", "Safari", pid = 30, isVisible = true),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val initialIds = viewModel.uiState.value.workspaceButtonsFor(host.id).map(GridButton::id)
        viewModel.onIntent(DeskIntent.ResizeAppTile(initialIds[1]))
        runCurrent()

        val savedHost = preferences.getHosts().single()
        assertEquals(initialIds.toSet(), savedHost.appGridPositions.keys)
        assertEquals(initialIds, savedHost.appOrder.take(initialIds.size))
        assertEquals(initialIds.indices.toSet(), savedHost.appGridPositions.values.toSet())
    }

    @Test
    fun droppingCardPersistsItsCompactGridOrderAfterRelease() = runTest(dispatcher) {
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val preferences = FakeHostPreferences(listOf(host))
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(name = "Finder", appName = "Finder", pid = 321, isVisible = true),
                RunningApp(name = "Notes", appName = "Notes", pid = 654, isVisible = true),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = preferences,
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val buttons = viewModel.uiState.value.workspaceButtonsFor(host.id)
        val positions = buttons.mapIndexed { index, button -> button.id to index + 10 }.toMap()
        viewModel.onIntent(DeskIntent.PreviewAppGridPositions(positions))
        viewModel.onIntent(DeskIntent.FinishAppReorderDrag)
        runCurrent()

        assertEquals(positions, preferences.getHosts().single().appGridPositions)
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
                    stableWindowId = "9848",
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

        val studioCards = viewModel.uiState.value.buttons.filter { it.isFavoriteShortcut }
        assertEquals(1, studioCards.size)
        val studio = studioCards.single()
        assertTrue(studio.isFavoriteShortcut)
        assertEquals("Android Studio · sample-android-app", studio.label)
        assertTrue(studio.isVisible)
        assertTrue(studio.isActive)
        assertEquals(2, studio.monitorIndex)
        assertEquals("321", studio.payload["pid"])
        assertEquals("9848", studio.payload["stableWindowId"])
        assertEquals(shortcut.bundlePath, studio.payload["bundlePath"])
        assertEquals(shortcut.projectPath, studio.payload["projectPath"])
    }

    @Test
    fun personalizedShortcutAlwaysShowsConfiguredProjectName() = runTest(dispatcher) {
        val shortcut = PinnedAppShortcut(
            id = "body-temperature",
            name = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/BodyTemperature",
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(shortcut))
        val runningWindow = RunningApp(
            name = "studio · Welcome to Android Studio",
            appName = "studio",
            pid = 321,
            windowTitle = "Welcome to Android Studio",
            bundlePath = shortcut.bundlePath,
            isActive = true,
            isVisible = true,
        )
        val gateway = FakeAgentGateway(apps = listOf(runningWindow))
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(apps = gateway.apps),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val projectShortcut = viewModel.uiState.value.buttons.single { it.isFavoriteShortcut }
        assertEquals("Android Studio · BodyTemperature", projectShortcut.label)
        assertTrue(projectShortcut.label.contains("BodyTemperature"))
    }

    @Test
    fun pinnedProjectShortcutDoesNotBindToAnotherOpenProjectOfSameApp() = runTest(dispatcher) {
        val shortcut = PinnedAppShortcut(
            id = "body-temperature",
            name = "Android Studio — BodyTemperature",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/BodyTemperature",
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(shortcut))
        val openProject = RunningApp(
            name = "Android Studio · control-desk",
            appName = "Android Studio",
            pid = 321,
            windowTitle = "control-desk",
            axWindowIndex = 1,
            bundlePath = shortcut.bundlePath,
            isActive = true,
            isVisible = true,
        )
        val gateway = FakeAgentGateway(apps = listOf(openProject))
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
        assertEquals(2, studioCards.size)
        val pinnedProject = studioCards.single(GridButton::isFavoriteShortcut)
        val openWindow = studioCards.single { !it.isFavoriteShortcut }
        assertEquals(shortcut.projectPath, pinnedProject.payload["projectPath"])
        assertEquals(null, pinnedProject.payload["pid"])
        assertFalse(pinnedProject.isActive)
        assertFalse(pinnedProject.isVisible)
        assertEquals("control-desk", openWindow.payload["windowTitle"])
        assertEquals("321", openWindow.payload["pid"])
    }

    @Test
    fun genericStudioShortcutDoesNotStealOrDuplicatePinnedProjectWindow() = runTest(dispatcher) {
        val bundlePath = "/Applications/Android Studio.app"
        val projectShortcut = PinnedAppShortcut(
            id = "body-temperature",
            name = "Android Studio — BodyTemperature",
            bundlePath = bundlePath,
            projectPath = "/Users/test/Projects/BodyTemperature",
            order = 1,
        )
        val genericShortcut = PinnedAppShortcut(
            id = "android-studio-default",
            name = "Android Studio",
            bundlePath = bundlePath,
            order = 0,
        )
        val projectWindow = RunningApp(
            name = "Android Studio · BodyTemperature",
            appName = "Android Studio",
            pid = 321,
            windowTitle = "BodyTemperature",
            axWindowIndex = 1,
            bundlePath = bundlePath,
            isActive = true,
            isVisible = true,
        )
        val otherProjectWindow = RunningApp(
            name = "Android Studio · control-desk",
            appName = "Android Studio",
            pid = 321,
            windowTitle = "control-desk",
            axWindowIndex = 2,
            bundlePath = bundlePath,
            isVisible = true,
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
            .copy(pinnedApps = listOf(genericShortcut, projectShortcut))
        val realtime = FakeAgentRealtime(apps = listOf(projectWindow, otherProjectWindow))
        val gateway = FakeAgentGateway(apps = realtime.apps)
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        var shortcuts = viewModel.uiState.value.buttons.filter(GridButton::isFavoriteShortcut)
        val bodyTemperature = shortcuts.single { it.payload["pinnedAppId"] == projectShortcut.id }
        val defaultStudio = shortcuts.single { it.payload["pinnedAppId"] == genericShortcut.id }
        assertEquals("BodyTemperature", bodyTemperature.payload["windowTitle"])
        assertEquals("control-desk", defaultStudio.payload["windowTitle"])
        assertEquals(2, shortcuts.mapNotNull { it.payload["windowTitle"] }.toSet().size)

        realtime.emitApps(listOf(projectWindow))
        runCurrent()

        shortcuts = viewModel.uiState.value.buttons.filter(GridButton::isFavoriteShortcut)
        assertEquals(2, shortcuts.size)
        assertTrue(shortcuts.single { it.payload["pinnedAppId"] == projectShortcut.id }.isActive)
        assertFalse(shortcuts.single { it.payload["pinnedAppId"] == genericShortcut.id }.isActive)
        assertEquals("BodyTemperature", shortcuts.single { it.payload["pinnedAppId"] == projectShortcut.id }.payload["windowTitle"])
    }

    @Test
    fun closingRunningPinnedProjectKeepsItsShortcutAvailable() = runTest(dispatcher) {
        val shortcut = PinnedAppShortcut(
            id = "body-temperature",
            name = "Android Studio — BodyTemperature",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/BodyTemperature",
        )
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(pinnedApps = listOf(shortcut))
        val runningProject = RunningApp(
            name = "Android Studio · BodyTemperature",
            appName = "Android Studio",
            pid = 321,
            windowTitle = "BodyTemperature",
            windowIndex = 0,
            axWindowIndex = 1,
            bundlePath = shortcut.bundlePath,
            isActive = true,
            isVisible = true,
        )
        val gateway = FakeAgentGateway(apps = listOf(runningProject))
        val realtime = FakeAgentRealtime(apps = listOf(runningProject))
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = realtime,
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
            coroutineScope = backgroundScope,
        )
        runCurrent()

        val openShortcut = viewModel.uiState.value.buttons.single(GridButton::isFavoriteShortcut)
        assertTrue(openShortcut.isVisible)
        assertEquals("321", openShortcut.payload["pid"])

        realtime.apps = emptyList()
        viewModel.onAppClose(openShortcut)
        runCurrent()

        val remainingShortcut = viewModel.uiState.value.buttons.single(GridButton::isFavoriteShortcut)
        assertEquals(shortcut.projectPath, remainingShortcut.payload["projectPath"])
        assertEquals(shortcut.bundlePath, remainingShortcut.payload["bundlePath"])
        assertFalse(remainingShortcut.isVisible)
        assertFalse(remainingShortcut.isActive)
        assertEquals(null, remainingShortcut.payload["pid"])
        assertEquals(1, realtime.closed.size)
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
            it.isFavoriteShortcut && it.payload["bundlePath"] == shortcut.bundlePath
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
            listOf("Terminal", "Android Studio", "Safari"),
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
            listOf("Terminal", "Android Studio", "Safari"),
            viewModel.uiState.value.buttons.map { it.label },
        )
        assertEquals(
            viewModel.uiState.value.buttons.map { it.id },
            preferences.getHosts().single().appOrder.take(3),
        )
    }

    @Test
    fun dockShortcutKeepsItsUserSlotWhenBundleOpensAsMultipleProcessNamedWindows() = runTest(dispatcher) {
        val codeBundle = "/Applications/Visual Studio Code.app"
        val legacyDockId = "app-Code-dock"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0).copy(
            appOrder = listOf(legacyDockId, "app-Safari-dock", "app-Notes-dock"),
            appGridPositions = mapOf(legacyDockId to 0, "app-Safari-dock" to 1, "app-Notes-dock" to 2),
            appLayoutIdentities = mapOf(legacyDockId to "app:$codeBundle"),
        )
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(
                    name = "Visual Studio Code",
                    appName = "Visual Studio Code",
                    isVisible = false,
                    isDockPinned = true,
                    dockIndex = 0,
                    bundlePath = codeBundle,
                ),
                RunningApp("Safari", "Safari", isVisible = false, isDockPinned = true, dockIndex = 1),
                RunningApp("Notes", "Notes", isVisible = false, isDockPinned = true, dockIndex = 2),
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
        val dockSlot = initial.indexOfFirst { it.payload["applicationShortcutId"] != null && it.payload["bundlePath"] == codeBundle }
        assertEquals(0, dockSlot)

        gateway.apps = listOf(
            RunningApp(
                name = "Code · Welcome",
                appName = "Code",
                pid = 31174,
                windowTitle = "Welcome",
                stableWindowId = "9848",
                isActive = true,
                isVisible = true,
                bundlePath = codeBundle,
            ),
            RunningApp(
                name = "Code · control-desk",
                appName = "Code",
                pid = 31174,
                windowTitle = "control-desk",
                stableWindowId = "9849",
                isVisible = true,
                bundlePath = codeBundle,
            ),
            RunningApp("Safari", "Safari", isVisible = false, isDockPinned = true, dockIndex = 1),
            RunningApp("Notes", "Notes", isVisible = false, isDockPinned = true, dockIndex = 2),
        )
        viewModel.refreshRunningApps()
        runCurrent()

        val buttons = viewModel.uiState.value.buttons
        assertEquals("Welcome", buttons.first().payload["windowTitle"])
        assertEquals(listOf("Safari", "Notes"), buttons.drop(1).take(2).map { it.label })
        assertEquals(3, buttons.size)
        assertEquals("2", buttons.first().payload["instanceCount"])
    }

    @Test
    fun gridPositionAndTileSizeSurviveAndroidStudioWindowTitleChanges() = runTest(dispatcher) {
        val studioBundle = "/Applications/Android Studio.app"
        val host = pairedHost("10.0.2.2:8741", "Mac", 0)
        val studio = RunningApp(
            name = "Android Studio · BodyTemperature",
            appName = "Android Studio",
            pid = 321,
            windowTitle = "BodyTemperature",
            windowIndex = 0,
            axWindowIndex = 1,
            bundlePath = studioBundle,
            isVisible = true,
        )
        val gateway = FakeAgentGateway(
            apps = listOf(
                studio,
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
        val studioButton = initial.single { it.payload["bundlePath"] == studioBundle }
        val chosenOrder = listOf(
            initial.single { it.label == "Terminal" }.id,
            studioButton.id,
            initial.single { it.label == "Safari" }.id,
        )
        viewModel.onIntent(DeskIntent.ReorderApps(chosenOrder))
        viewModel.onIntent(DeskIntent.ResizeAppTile(studioButton.id))
        runCurrent()
        assertEquals("2", viewModel.uiState.value.buttons.single { it.id == studioButton.id }.payload["spanColumns"])

        gateway.apps = listOf(
            studio.copy(name = "studio", appName = "studio", windowTitle = "control-desk", isActive = true),
            RunningApp("Safari", "Safari", pid = 456, isVisible = true),
            RunningApp("Terminal", "Terminal", pid = 789, isVisible = true),
        )
        viewModel.refreshRunningApps()
        runCurrent()

        val refreshedStudio = viewModel.uiState.value.buttons.single { it.payload["bundlePath"] == studioBundle }
        val savedHost = preferences.getHosts().single()
        assertEquals(chosenOrder.map { id -> if (id == studioButton.id) refreshedStudio.id else id }, savedHost.appOrder.filter { it in chosenOrder })
        assertEquals(savedHost.appOrder.indexOf(refreshedStudio.id), savedHost.appGridPositions[refreshedStudio.id])
        assertEquals("2", refreshedStudio.payload["spanColumns"])
    }

    @Test
    fun closingAppKeepsItsPersistentLauncher() = runTest(dispatcher) {
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
        realtime.apps = gateway.apps.filterNot { it.appName == "Safari" }
        viewModel.onAppClose(safari)
        assertEquals(listOf("Notes", "Safari"), viewModel.uiState.value.buttons.map { it.label })
        assertFalse(viewModel.uiState.value.buttons.single { it.id == safari.id }.isVisible)
        runCurrent()
        assertEquals("Safari", realtime.closed.single().payload["target"])
        assertEquals(listOf("Notes", "Safari"), viewModel.uiState.value.buttons.map { it.label })
        assertFalse(viewModel.uiState.value.buttons.single { it.id == safari.id }.isVisible)
    }

    @Test
    fun closeAcknowledgementRetainsSlotAndReflectsAgentSnapshot() = runTest(dispatcher) {
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
        assertEquals(listOf("Notes", "Safari"), viewModel.uiState.value.buttons.map { it.label })
        assertTrue(viewModel.uiState.value.buttons.single { it.id == safari.id }.isVisible)
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
        realtime.apps = gateway.apps.filterNot { it.appName == "Safari" }
        gateway.apps = realtime.apps
        viewModel.onAppClose(safari)
        val after = viewModel.uiState.value.buttons
        assertEquals(listOf("Safari", "Notes"), after.map { it.label })
        val placeholder = after.first { it.label == "Safari" }
        assertEquals(safari.id, placeholder.id)
        assertEquals(false, placeholder.isActive)
        assertEquals(false, placeholder.isVisible)
        assertEquals(true, placeholder.isDockPinned)
        runCurrent()
        val polled = viewModel.uiState.value.buttons.first { it.payload["target"] == "Safari" }
        assertEquals(safari.id, polled.id)
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
