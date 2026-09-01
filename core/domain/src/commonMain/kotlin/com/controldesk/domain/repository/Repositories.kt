package com.controldesk.domain.repository

import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import kotlinx.coroutines.flow.Flow

interface HostPreferences {
    fun observeHosts(): Flow<List<PairedHost>>
    suspend fun getHosts(): List<PairedHost>
    suspend fun saveHost(host: PairedHost)
    suspend fun removeHost(hostId: String)
}

interface AgentGateway {
    suspend fun fetchHealth(host: PairedHost): Boolean?
    suspend fun loadRunningApps(host: PairedHost, includeIcons: Boolean): List<RunningApp>
    suspend fun fetchAudio(host: PairedHost): AudioState
    suspend fun launchApp(host: PairedHost, button: GridButton)
    suspend fun closeApp(host: PairedHost, button: GridButton)
    suspend fun switchBrowserTab(host: PairedHost, button: GridButton)
    suspend fun openBrowser(host: PairedHost, url: String)
    suspend fun closeBrowser(host: PairedHost)
    suspend fun setVolume(host: PairedHost, level: Int? = null, delta: Int? = null, mute: Boolean? = null)
    suspend fun setMic(host: PairedHost, mute: Boolean? = null, toggle: Boolean = false, level: Int? = null)
}

interface PairHostRemote {
    suspend fun pair(host: String, port: Int, pin: String): String
}

interface RunningAppIconCache {
    suspend fun load(): Map<String, String>
    suspend fun putIfAbsent(buttonId: String, base64: String)
    fun peek(buttonId: String): String?
}
