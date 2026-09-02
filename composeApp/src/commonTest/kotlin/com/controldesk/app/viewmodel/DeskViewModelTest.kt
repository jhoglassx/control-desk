package com.controldesk.app.viewmodel

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.testing.FakeAgentGateway
import com.controldesk.testing.FakeAgentRealtime
import com.controldesk.testing.FakeHostPreferences
import com.controldesk.testing.FakeIconCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class DeskViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setup() {
        Dispatchers.setMain(dispatcher)
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
        val safari = GridButton(
            id = "app-Safari",
            label = "Safari",
            actionType = ActionType.REMOTE_LAUNCH,
            payload = mapOf("target" to "Safari"),
            isActive = false,
            isVisible = true,
        )
        val chrome = safari.copy(id = "app-Chrome", label = "Chrome", payload = mapOf("target" to "Chrome"), isActive = true)
        val gateway = FakeAgentGateway(
            apps = listOf(
                RunningApp(name = "Safari", appName = "Safari", isActive = false, isVisible = true),
                RunningApp(name = "Chrome", appName = "Chrome", isActive = true, isVisible = true),
            ),
        )
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
        )
        advanceUntilIdle()
        viewModel.onButtonClick(safari)
        assertEquals(listOf("Safari"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })
        advanceUntilIdle()
        assertEquals("Safari", gateway.launched.last().label)
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
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
        )
        advanceUntilIdle()
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onAppClose(safari)
        assertEquals(listOf("Notes"), viewModel.uiState.value.buttons.map { it.label })
        advanceUntilIdle()
        assertEquals("Safari", gateway.closed.single().payload["target"])
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
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
        )
        advanceUntilIdle()
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onAppClose(safari)
        advanceUntilIdle()
        assertEquals("Safari", gateway.closed.single().payload["target"])
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
        val viewModel = DeskViewModel(
            hostPreferences = FakeHostPreferences(listOf(host)),
            agentGateway = gateway,
            agentRealtime = FakeAgentRealtime(),
            executeGridButton = ExecuteGridButtonUseCase(gateway),
            iconCache = FakeIconCache(),
        )
        advanceUntilIdle()
        val safari = viewModel.uiState.value.buttons.first { it.label == "Safari" }
        viewModel.onAppClose(safari)
        val after = viewModel.uiState.value.buttons
        assertEquals(listOf("Safari", "Notes"), after.map { it.label })
        val placeholder = after.first { it.label == "Safari" }
        assertEquals("app-Safari-dock", placeholder.id)
        assertEquals(false, placeholder.isActive)
        assertEquals(false, placeholder.isVisible)
        assertEquals(true, placeholder.isDockPinned)
        advanceUntilIdle()
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
        )
        advanceUntilIdle()
        assertEquals(listOf("Chrome"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })

        viewModel.selectHost(1)
        advanceUntilIdle()
        assertEquals("Laptop", viewModel.uiState.value.selectedHost?.name)
        assertEquals(listOf("Notes"), viewModel.uiState.value.buttons.filter { it.isActive }.map { it.label })
        assertEquals(listOf("Mail", "Notes"), viewModel.uiState.value.buttons.map { it.label })

        viewModel.selectHost(0)
        advanceUntilIdle()
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
        )
        advanceUntilIdle()
        viewModel.selectHost(1)
        advanceUntilIdle()
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
        )
        advanceUntilIdle()
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
