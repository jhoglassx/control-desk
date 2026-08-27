package com.controldesk.protocol

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

class AgentApiClient(
    private val httpClient: HttpClient,
) {
    suspend fun health(baseUrl: String): HealthResponse =
        httpClient.get("$baseUrl/health").body()

    suspend fun pair(baseUrl: String, pin: String): PairResponse =
        httpClient.post("$baseUrl/pair") {
            contentType(ContentType.Application.Json)
            setBody(PairRequest(pin))
        }.body()

    suspend fun runningApps(baseUrl: String, token: String, includeIcons: Boolean): RunningAppsResponse =
        httpClient.get("$baseUrl/apps/running") {
            bearerAuth(token)
            parameter("icons", if (includeIcons) 1 else 0)
        }.body()

    suspend fun launchApp(baseUrl: String, token: String, request: AppLaunchRequest) {
        httpClient.post("$baseUrl/apps/launch") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun closeApp(baseUrl: String, token: String, request: AppCloseRequest) {
        httpClient.post("$baseUrl/apps/close") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun openBrowser(baseUrl: String, token: String, request: BrowserOpenRequest) {
        httpClient.post("$baseUrl/browser/open") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun closeBrowser(baseUrl: String, token: String, request: BrowserCloseRequest = BrowserCloseRequest()) {
        httpClient.post("$baseUrl/browser/close") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun switchBrowserTab(baseUrl: String, token: String, request: BrowserTabSwitchRequest) {
        httpClient.post("$baseUrl/browser/tabs/switch") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun audio(baseUrl: String, token: String): AudioResponse =
        httpClient.get("$baseUrl/system/audio") {
            timeout { requestTimeoutMillis = 800 }
            bearerAuth(token)
        }.body()

    suspend fun setVolume(baseUrl: String, token: String, request: VolumeRequest) {
        httpClient.post("$baseUrl/system/volume") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun setMic(baseUrl: String, token: String, request: MicRequest) {
        httpClient.post("$baseUrl/system/mic") {
            timeout { requestTimeoutMillis = COMMAND_TIMEOUT_MS }
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}

private const val COMMAND_TIMEOUT_MS = 2_500L

fun agentBaseUrl(host: String, port: Int): String = "http://$host:$port"
