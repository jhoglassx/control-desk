package com.controldesk.app.viewmodel

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.testing.FakeAgentGateway
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
}
