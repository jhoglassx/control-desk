package com.controldesk.data

import com.controldesk.data.network.installControlDeskPlugins
import com.controldesk.domain.model.*
import com.controldesk.protocol.AgentApiClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class DeviceGatewayTest {
    private val host = PairedHost("pc", "PC", "localhost", 8741, OsType.MACOS, 0, "test-token")
    @Test fun authenticatedCatalogMapsCapabilitiesAndDiagnostics() = runTest {
        val client = HttpClient(MockEngine { request ->
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            assertEquals("/devices", request.url.encodedPath)
            respond("""{"devices":[{"id":"native:1","name":"Mic","kind":"AUDIO_INPUT","adapter":"native","capabilities":["SET_MUTED","FUTURE_ACTION"],"muted":false}],"diagnostics":{"other":"Unavailable"}}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }) { installControlDeskPlugins() }
        try {
            val catalog = AgentGatewayImpl(AgentApiClient(client)).loadDevices(host)
            assertEquals(listOf(DeviceCapability.SET_MUTED), catalog.devices.single().capabilities)
            assertEquals("Unavailable", catalog.diagnostics["other"])
        } finally { client.close() }
    }
    @Test fun nativeDeviceFailuresPreserveAgentExplanationAndDoNotRetry() = runTest {
        var requests = 0
        val client = HttpClient(MockEngine {
            requests++
            respond("""{"ok":false,"message":"Permission denied; run agent as administrator"}""", HttpStatusCode.InternalServerError,
                headersOf(HttpHeaders.ContentType, "application/json"))
        }) { installControlDeskPlugins() }
        try {
            val error = runCatching { AgentGatewayImpl(AgentApiClient(client)).executeDeviceCommand(host,
                DeviceCommand("camera", DeviceCapability.SET_ENABLED, false)) }.exceptionOrNull()
            assertTrue(error?.message.orEmpty().contains("administrator"))
            assertEquals(1, requests)
        } finally { client.close() }
    }
    @Test fun wrongDeviceOrUnconfirmedResponseNeverBecomesSuccess() = runTest {
        for (body in listOf(
            """{"device":{"id":"wrong","name":"Camera","kind":"CAMERA","adapter":"native"},"confirmed":true}""",
            """{"device":{"id":"camera","name":"Camera","kind":"CAMERA","adapter":"native"},"confirmed":false}""")) {
            val client = HttpClient(MockEngine { respond(body, headers = headersOf(HttpHeaders.ContentType, "application/json")) }) { installControlDeskPlugins() }
            try {
                assertFails { AgentGatewayImpl(AgentApiClient(client)).executeDeviceCommand(host, DeviceCommand("camera", DeviceCapability.SET_ENABLED, false)) }
            } finally { client.close() }
        }
    }
}
