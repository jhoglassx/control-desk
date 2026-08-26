package com.controldesk.testing

import com.controldesk.domain.model.AudioState
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.repository.PairHostRemote
import com.controldesk.domain.repository.RunningAppIconCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeHostPreferences(
    initial: List<PairedHost> = emptyList(),
) : HostPreferences {
    private val hosts = MutableStateFlow(initial)
    override fun observeHosts(): Flow<List<PairedHost>> = hosts.asStateFlow()
    override suspend fun getHosts(): List<PairedHost> = hosts.value
    override suspend fun saveHost(host: PairedHost) {
        hosts.value = hosts.value.filterNot { it.id == host.id } + host
    }
    override suspend fun removeHost(hostId: String) {
        hosts.value = hosts.value.filterNot { it.id == hostId }
    }
}

class FakeAgentGateway(
    var apps: List<RunningApp> = emptyList(),
    var audio: AudioState = AudioState(),
    var failWith: Throwable? = null,
) : AgentGateway {
    val launched = mutableListOf<GridButton>()
    val volumes = mutableListOf<Triple<Int?, Int?, Boolean?>>()
    val mics = mutableListOf<Pair<Boolean?, Boolean>>()

    private fun <T> result(value: T): T {
        failWith?.let { throw it }
        return value
    }

    override suspend fun loadRunningApps(host: PairedHost, includeIcons: Boolean): List<RunningApp> = result(apps)
    override suspend fun fetchAudio(host: PairedHost): AudioState = result(audio)
    override suspend fun launchApp(host: PairedHost, button: GridButton) {
        result(Unit)
        launched += button
    }
    override suspend fun switchBrowserTab(host: PairedHost, button: GridButton) {
        result(Unit)
        launched += button
    }
    override suspend fun openBrowser(host: PairedHost, url: String) { result(Unit) }
    override suspend fun closeBrowser(host: PairedHost) { result(Unit) }
    override suspend fun setVolume(host: PairedHost, level: Int?, delta: Int?, mute: Boolean?) {
        result(Unit)
        volumes += Triple(level, delta, mute)
    }
    override suspend fun setMic(host: PairedHost, mute: Boolean?, toggle: Boolean) {
        result(Unit)
        mics += mute to toggle
    }
}

class FakePairHostRemote(
    var token: String = "token-1",
    var failWith: Throwable? = null,
) : PairHostRemote {
    override suspend fun pair(host: String, port: Int, pin: String): String {
        failWith?.let { throw it }
        return token
    }
}

class FakeIconCache : RunningAppIconCache {
    private val icons = mutableMapOf<String, String>()
    override suspend fun load(): Map<String, String> = icons.toMap()
    override suspend fun putIfAbsent(buttonId: String, base64: String) {
        icons.putIfAbsent(buttonId, base64)
    }
    override fun peek(buttonId: String): String? = icons[buttonId]
}
