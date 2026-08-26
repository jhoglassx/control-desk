package com.controldesk.domain.usecase

import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.testing.FakeAgentGateway
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ExecuteGridButtonUseCaseTest {
    private val host = PairedHost(
        id = "10.0.2.2:8741",
        name = "Mac",
        host = "10.0.2.2",
        port = 8741,
        osType = OsType.MACOS,
        sortOrder = 0,
        token = "abc",
    )

    @Test
    fun launchRequiresTarget() = runTest {
        val gateway = FakeAgentGateway()
        val useCase = ExecuteGridButtonUseCase(gateway)
        val button = GridButton(id = "1", label = "Safari", actionType = ActionType.REMOTE_LAUNCH)
        assertFailsWith<ControlDeskError.Generic> { useCase(button, host) }
    }

    @Test
    fun launchCallsGateway() = runTest {
        val gateway = FakeAgentGateway()
        val useCase = ExecuteGridButtonUseCase(gateway)
        val button = GridButton(
            id = "1",
            label = "Safari",
            actionType = ActionType.REMOTE_LAUNCH,
            payload = mapOf("target" to "Safari"),
        )
        useCase(button, host)
        assertEquals(listOf(button), gateway.launched)
    }

    @Test
    fun missingTokenIsUnauthorized() = runTest {
        val useCase = ExecuteGridButtonUseCase(FakeAgentGateway())
        val button = GridButton(
            id = "1",
            label = "Safari",
            actionType = ActionType.REMOTE_LAUNCH,
            payload = mapOf("target" to "Safari"),
        )
        assertFailsWith<ControlDeskError.Unauthorized> {
            useCase(button, host.copy(token = ""))
        }
    }

    @Test
    fun openUrlIsLocalOnly() = runTest {
        val useCase = ExecuteGridButtonUseCase(FakeAgentGateway())
        val button = GridButton(id = "1", label = "Web", actionType = ActionType.OPEN_URL)
        val error = assertFailsWith<ControlDeskError.Generic> { useCase(button, host) }
        assertTrue(error.message!!.contains("local only"))
    }
}
