package com.controldesk.data

import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.model.PinnedAppShortcut
import com.controldesk.domain.model.MacFileEntry
import com.controldesk.domain.model.MacFileListing
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.logging.ControlDeskLog
import com.controldesk.protocol.AgentApiClient
import com.controldesk.protocol.BrowseFilesRequest
import com.controldesk.protocol.BrowserCloseRequest
import com.controldesk.protocol.BrowserOpenRequest
import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.VolumeRequest
import com.controldesk.protocol.agentBaseUrl
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException
import kotlinx.coroutines.CancellationException

class AgentGatewayImpl(
    private val api: AgentApiClient,
) : AgentGateway {

    override suspend fun loadDevices(host: PairedHost): com.controldesk.domain.model.DeviceCatalog = try { mapErrors {
        requireToken(host)
        val response = api.devices(host.baseUrl(), host.token)
        com.controldesk.domain.model.DeviceCatalog(response.devices.mapNotNull(::mapDevice), response.diagnostics)
    } } catch (error: ControlDeskError.Generic) {
        if (error.message?.contains("(404)") == true) throw ControlDeskError.Generic("Agente desatualizado. Instale o Control Desk Agent 1.1.13 ou superior para listar dispositivos.")
        throw error
    }

    override suspend fun executeDeviceCommand(host: PairedHost, command: com.controldesk.domain.model.DeviceCommand): com.controldesk.domain.model.ConnectedDevice = mapErrors {
        requireToken(host)
        val response = try {
            api.deviceAction(host.baseUrl(), host.token,
                com.controldesk.protocol.DeviceActionRequest(command.deviceId, command.action.name, command.booleanValue, command.level))
        } catch (error: ResponseException) {
            if (error.response.status == HttpStatusCode.Unauthorized) throw error
            val message = runCatching {
                com.controldesk.protocol.AgentJson.parseToJsonElement(error.response.bodyAsText()).jsonObject["message"]?.jsonPrimitive?.content
            }.getOrNull()
            throw ControlDeskError.Generic(message ?: "O agente recusou o comando de dispositivo (${error.response.status.value}).")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) {
            if (isOffline(error)) throw ControlDeskError.Generic("Não foi possível confirmar a ação no dispositivo. Atualize o estado antes de tentar novamente.")
            throw error
        }
        check(response.device.id == command.deviceId) { "O agente retornou outro dispositivo; resultado não confirmado." }
        check(response.confirmed) { "O agente não confirmou o estado do dispositivo. Atualize antes de tentar novamente." }
        mapDevice(response.device) ?: error("Resposta de dispositivo incompatível")
    }

    private fun mapDevice(info: com.controldesk.protocol.DeviceInfo): com.controldesk.domain.model.ConnectedDevice? {
        val kind = com.controldesk.domain.model.DeviceKind.entries.singleOrNull { it.name == info.kind } ?: return null
        return com.controldesk.domain.model.ConnectedDevice(info.id, info.name, kind, info.adapter,
            info.capabilities.mapNotNull { value -> com.controldesk.domain.model.DeviceCapability.entries.singleOrNull { it.name == value } },
            info.enabled, info.muted, info.level, info.isDefault, info.notice)
    }

    override suspend fun fetchHealth(host: PairedHost): Boolean? = mapErrors {
        api.health(host.baseUrl()).accessibilityEnabled
    }

    override suspend fun loadRunningApps(host: PairedHost, includeIcons: Boolean): List<RunningApp> = mapErrors {
        requireToken(host)
        mapRunningApps(api.runningApps(host.baseUrl(), host.token, includeIcons).apps)
    }

    override suspend fun loadInstalledApps(host: PairedHost): List<PinnedAppShortcut> = try {
        mapErrors {
            requireToken(host)
            api.installedApps(host.baseUrl(), host.token).apps.map { app ->
                PinnedAppShortcut(
                    id = app.bundlePath,
                    name = app.name,
                    bundlePath = app.bundlePath,
                    iconBase64 = app.iconBase64,
                )
            }
        }
    } catch (error: ControlDeskError.Generic) {
        if (error.message?.contains("(404)") == true) {
            throw ControlDeskError.Generic("Agente do Mac desatualizado. Atualize o Control Desk Agent no Mac para carregar os apps fixos.")
        }
        throw error
    }

    override suspend fun browseMacFiles(host: PairedHost, path: String?): MacFileListing = mapErrors {
        requireToken(host)
        val result = api.browseFiles(host.baseUrl(), host.token, BrowseFilesRequest(path))
        MacFileListing(
            path = result.path,
            parentPath = result.parentPath,
            entries = result.entries.map { MacFileEntry(it.name, it.path, it.isDirectory) },
        )
    }

    private fun mapRunningApps(apps: List<com.controldesk.protocol.RunningAppInfo>): List<RunningApp> =
        apps.map { info ->
            RunningApp(
                name = info.name,
                appName = info.appName,
                pid = info.pid,
                windowTitle = info.windowTitle,
                windowIndex = info.windowIndex,
                axWindowIndex = info.axWindowIndex,
                windowNumber = info.windowNumber,
                stableWindowId = info.stableWindowId,
                windowCount = info.windowCount,
                iconBase64 = info.iconBase64,
                isActive = info.isActive,
                isVisible = info.isVisible,
                browserWindowIndex = info.browserWindowIndex,
                isBrowserTab = info.isBrowserTab,
                monitorIndex = info.monitorIndex,
                isDockPinned = info.isDockPinned,
                dockIndex = info.dockIndex,
                bundlePath = info.bundlePath,
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
                button.toLaunchRequest(),
            ).apps,
        )
    }

    override suspend fun closeApp(host: PairedHost, button: GridButton): List<RunningApp> = mapErrors {
        requireToken(host)
        mapRunningApps(
            api.closeApp(
                host.baseUrl(),
                host.token,
                button.toCloseRequest(),
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
        } catch (error: CancellationException) {
            // Host changes and screen refreshes intentionally cancel stale requests.
            // Preserve structured concurrency and avoid reporting them as agent failures.
            throw error
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
