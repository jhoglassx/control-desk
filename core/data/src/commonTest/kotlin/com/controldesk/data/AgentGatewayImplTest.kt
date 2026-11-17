package com.controldesk.data

import com.controldesk.data.network.installControlDeskPlugins
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.protocol.AgentApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentGatewayImplTest {
    @Test
    fun cancellationIsPropagatedWithoutBeingMappedToAgentError() = runTest {
        val cancellation = CancellationException("Host selection changed")
        val client = HttpClient(MockEngine { throw cancellation }) {
            installControlDeskPlugins()
        }
        val gateway = AgentGatewayImpl(AgentApiClient(client))

        try {
            val thrown = runCatching {
                gateway.setVolume(
                    host = PairedHost(
                        id = "host",
                        name = "Mac",
                        host = "localhost",
                        port = 8741,
                        osType = OsType.MACOS,
                        sortOrder = 0,
                        token = "token",
                    ),
                    level = 50,
                    delta = null,
                    mute = null,
                )
            }.exceptionOrNull()

            assertTrue(thrown is CancellationException)
            assertEquals(cancellation.message, thrown?.message)
        } finally {
            client.close()
        }
    }
}
