package com.controldesk.agent

import com.controldesk.protocol.AppLaunchRequest
import com.controldesk.protocol.AudioResponse
import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.MicRequest
import com.controldesk.protocol.RunningAppInfo
import com.controldesk.protocol.VolumeRequest

interface PlatformExecutor {
    val osName: String
    fun listRunningApps(includeIcons: Boolean): List<RunningAppInfo>
    fun launchApp(request: AppLaunchRequest)
    fun openBrowser(url: String, browser: String?)
    fun closeBrowser(browser: String?)
    fun switchBrowserTab(request: BrowserTabSwitchRequest)
    fun audio(): AudioResponse
    fun setVolume(request: VolumeRequest)
    fun setMic(request: MicRequest)
}

class LoggingPlatformExecutor : PlatformExecutor {
    @Volatile private var volume = 50
    @Volatile private var volumeMuted = false
    @Volatile private var micMuted = false
    @Volatile private var micLevel = 75
    private val apps = mutableListOf(
        RunningAppInfo(name = "Safari", appName = "Safari", windowTitle = "GitHub", isActive = true, isVisible = true, windowNumber = 1, monitorIndex = 1),
        RunningAppInfo(name = "Notes", appName = "Notes", isActive = false, isVisible = true, windowNumber = 2, monitorIndex = 1),
    )

    override val osName: String = System.getProperty("os.name") ?: "unknown"

    override fun listRunningApps(includeIcons: Boolean): List<RunningAppInfo> = apps.toList()

    override fun launchApp(request: AppLaunchRequest) {
        apps.replaceAll { if (it.appName.equals(request.target, true) || it.name.equals(request.target, true)) it.copy(isActive = true) else it.copy(isActive = false) }
    }

    override fun openBrowser(url: String, browser: String?) = Unit
    override fun closeBrowser(browser: String?) = Unit
    override fun switchBrowserTab(request: BrowserTabSwitchRequest) = Unit
    override fun audio(): AudioResponse = AudioResponse(volume, volumeMuted, micMuted, if (micMuted) 0 else micLevel)
    override fun setVolume(request: VolumeRequest) {
        request.mute?.let { volumeMuted = it }
        request.level?.let { volume = it.coerceIn(0, 100); volumeMuted = false }
        request.delta?.let { volume = (volume + it).coerceIn(0, 100); volumeMuted = false }
    }
    override fun setMic(request: MicRequest) {
        when {
            request.level != null -> {
                micLevel = request.level!!.coerceIn(0, 100)
                micMuted = micLevel <= 1
            }
            request.inputDelta != null -> {
                micLevel = (micLevel + request.inputDelta!!).coerceIn(0, 100)
                micMuted = micLevel <= 1
            }
            request.mute != null -> micMuted = request.mute == true
            request.toggle -> micMuted = !micMuted
        }
    }
}

class NativePlatformExecutor : PlatformExecutor {
    private val os = System.getProperty("os.name").lowercase()
    private val isMac = os.contains("mac")
    private val fallback = LoggingPlatformExecutor()

    override val osName: String = when {
        isMac -> "macos"
        os.contains("win") -> "windows"
        else -> "linux"
    }

    override fun listRunningApps(includeIcons: Boolean): List<RunningAppInfo> =
        if (isMac) MacWindowLister.list(includeIcons) else fallback.listRunningApps(includeIcons)

    override fun launchApp(request: AppLaunchRequest) {
        if (isMac) MacFocusApp.focus(request) else fallback.launchApp(request)
    }

    override fun openBrowser(url: String, browser: String?) {
        if (isMac) MacBrowser.open(url, browser) else fallback.openBrowser(url, browser)
    }

    override fun closeBrowser(browser: String?) {
        if (isMac) MacBrowser.close(browser) else fallback.closeBrowser(browser)
    }

    override fun switchBrowserTab(request: BrowserTabSwitchRequest) {
        if (isMac) MacChromeTabs.switchTab(request) else fallback.switchBrowserTab(request)
    }

    override fun audio(): AudioResponse =
        if (isMac) MacAudioControl.read() else fallback.audio()

    override fun setVolume(request: VolumeRequest) {
        if (isMac) MacAudioControl.setVolume(request) else fallback.setVolume(request)
    }

    override fun setMic(request: MicRequest) {
        if (isMac) MacAudioControl.setMic(request) else fallback.setMic(request)
    }
}
