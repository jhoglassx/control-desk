package com.controldesk.data

import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.data.storage.FileStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JsonHostPreferences(
    private val fileStore: FileStore,
    private val fileName: String = HOSTS_FILE,
) : HostPreferences {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private val hosts = MutableStateFlow(readFromDisk())

    override fun observeHosts(): Flow<List<PairedHost>> = hosts.asStateFlow()

    override suspend fun getHosts(): List<PairedHost> = mutex.withLock { hosts.value }

    override suspend fun saveHost(host: PairedHost) {
        mutex.withLock {
            val next = hosts.value
                .filterNot { it.id == host.id }
                .plus(host)
                .sortedBy { it.sortOrder }
            persist(next)
        }
    }

    override suspend fun removeHost(hostId: String) {
        mutex.withLock {
            persist(hosts.value.filterNot { it.id == hostId })
        }
    }

    private fun persist(next: List<PairedHost>) {
        fileStore.writeText(fileName, json.encodeToString(HostsFile(next)))
        hosts.value = next
    }

    private fun readFromDisk(): List<PairedHost> {
        val raw = fileStore.readText(fileName) ?: return emptyList()
        return runCatching { json.decodeFromString<HostsFile>(raw).hosts }
            .getOrElse { emptyList() }
            .sortedBy { it.sortOrder }
    }

    companion object {
        const val HOSTS_FILE = "control_desk_hosts.json"
    }
}

@Serializable
private data class HostsFile(
    val hosts: List<PairedHost> = emptyList(),
)
