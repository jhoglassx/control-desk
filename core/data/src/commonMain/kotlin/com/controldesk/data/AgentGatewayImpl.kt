package com.controldesk.data

import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.logging.ControlDeskLog
import com.controldesk.protocol.AgentApiClient
import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.BrowserCloseRequest
import com.controldesk.protocol.BrowserOpenRequest
import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.VolumeRequest
import com.controldesk.protocol.agentBaseUrl
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException

class AgentGatewayImpl(
    private val api: AgentApiClient,
) : AgentGateway {

    override suspend fun fetchHealth(host: PairedHost): Boolean? = mapErrors {
        api.health(host.baseUrl()).accessibilityEnabled
    }

    override suspend fun loadRunningApps(host: PairedHost, includeIcons: Boolean): List<RunningApp> = mapErrors {
        requireToken(host)
        mapRunningApps(api.runningApps(host.baseUrl(), host.token, includeIcons).apps)
    }

    private fun mapRunningApps(apps: List<com.controldesk.protocol.RunningAppInfo>): List<RunningApp> =
        apps.map { info ->
            RunningApp(
                name = info.name,
                appName = info.appName,
                windowTitle = info.windowTitle,
                windowIndex = info.windowIndex,
                axWindowIndex = info.axWindowIndex,
                windowNumber = info.windowNumber,
                iconBase64 = info.iconBase64,
                isActive = info.isActive,
                isVisible = info.isVisible,
                browserWindowIndex = info.browserWindowIndex,
                isBrowserTab = info.isBrowserTab,
                monitorIndex = info.monitorIndex,
                isDockPinned = info.isDockPinned,
                dockIndex = info.dockIndex,
            )
        }

    override suspend fun fetchAudio(host: PairedHost): AudioState = mapErrors {
        requireToken(host)
        val audio = api.audio(host.baseUrl(), host.token)
        AudioState(
            volumeLevel = audio.volumeLevel.coerceIn(0, 100),
            volumeMuted = audio.volumeMuted,
            micMuted = audio.micMuted,
            micLevel = audio.micLevel.coerceIn(0, 100),
        )
    }

    override suspend fun launchApp(host: PairedHost, button: GridButton): List<RunningApp> = mapErrors {
        requireToken(host)
        mapRunningApps(
            api.launchApp(
                host.baseUrl(),
                host.token,
                AppLaunchRequest(
                    target = button.payload.getValue("target"),
                    targetType = button.payload["targetType"] ?: "appName",
                    windowTitle = button.payload["windowTitle"],
                    windowIndex = button.payload["windowIndex"]?.toIntOrNull(),
                    windowNumber = button.payload["windowNumber"]?.toIntOrNull(),
                ),
            ).apps,
        )
    }

    override suspend fun closeApp(host: PairedHost, button: GridButton): List<RunningApp> = mapErrors {
        requireToken(host)
        mapRunningApps(
            api.closeApp(
                host.baseUrl(),
                host.token,
                AppCloseRequest(
                    target = button.payload.getValue("target"),
                    targetType = button.payload["targetType"] ?: "appName",
                ),
            ).apps,
        )
    }

    override suspend fun switchBrowserTab(host: PairedHost, button: GridButton): List<RunningApp> = mapErrors {
        mapRunningApps(
            api.switchBrowserTab(
                host.baseUrl(),
                host.token,
                BrowserTabSwitchRequest(
                    browser = button.payload["browser"] ?: "chrome",
                    action = button.payload["action"] ?: "select",
                    value = button.payload["value"],
                    windowIndex = button.payload["windowIndex"]?.toIntOrNull(),
                ),
            ).apps,
        )
    }

    override suspend fun openBrowser(host: PairedHost, url: String) = mapErrors {
        api.openBrowser(host.baseUrl(), host.token, BrowserOpenRequest(url = url))
    }

    override suspend fun closeBrowser(host: PairedHost) = mapErrors {
        api.closeBrowser(host.baseUrl(), host.token, BrowserCloseRequest())
    }

    override suspend fun setVolume(host: PairedHost, level: Int?, delta: Int?, mute: Boolean?) = mapErrors {
        api.setVolume(host.baseUrl(), host.token, VolumeRequest(level = level, delta = delta, mute = mute))
    }

    override suspend fun setMic(host: PairedHost, mute: Boolean?, toggle: Boolean, level: Int?) = mapErrors {
        api.setMic(host.baseUrl(), host.token, MicRequest(mute = mute, toggle = toggle, level = level))
    }

    private fun PairedHost.baseUrl(): String = agentBaseUrl(host, port)

    private fun requireToken(host: PairedHost) {
        if (host.token.isBlank()) throw ControlDeskError.Unauthorized()
    }

    private inline fun <T> mapErrors(block: () -> T): T {
        try {
            return block()
        } catch (error: ControlDeskError) {
            throw error
        } catch (error: ResponseException) {
            if (error.response.status == HttpStatusCode.Unauthorized) {
                throw ControlDeskError.Unauthorized()
            }
            throw ControlDeskError.Generic("Erro do agente (${error.response.status.value})")
        } catch (error: Throwable) {
            if (error !is ControlDeskError) {
                ControlDeskLog.gateway.e(error) { "agent request failed: ${error.message}" }
            }
            if (isOffline(error) || isUnauthorizedBody(error)) {
                if (isUnauthorizedBody(error)) throw ControlDeskError.Unauthorized()
                throw ControlDeskError.Offline()
            }
            throw ControlDeskError.Generic(error.message ?: "Erro inesperado")
        }
    }

    private fun isUnauthorizedBody(error: Throwable): Boolean {
        val text = error.message.orEmpty()
        return text.contains("401") && (
            text.contains("Unauthorized", ignoreCase = true) ||
                text.contains("NoTransformationFound", ignoreCase = true)
            )
    }

    private fun isOffline(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is HttpRequestTimeoutException ||
                current is UnresolvedAddressException ||
                current::class.simpleName?.contains("Connect", ignoreCase = true) == true ||
                current::class.simpleName?.contains("IOException", ignoreCase = true) == true
            ) {
                return true
            }
            current = current.cause
        }
        return error.message?.contains("Failed to connect", ignoreCase = true) == true ||
            error.message?.contains("Connection refused", ignoreCase = true) == true
    }
}
