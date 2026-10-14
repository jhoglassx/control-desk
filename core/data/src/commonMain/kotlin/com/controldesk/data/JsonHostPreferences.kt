package com.controldesk.data

import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.data.storage.FileStore
import com.controldesk.logging.ControlDeskLog
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
        val previous = fileStore.readText(fileName)
        if (previous != null && decodeHosts(previous) != null) {
            fileStore.writeText(backupFileName(), previous)
        }
        fileStore.writeText(fileName, json.encodeToString(HostsFile(next)))
        hosts.value = next
    }

    private fun readFromDisk(): List<PairedHost> {
        val raw = fileStore.readText(fileName)
        if (raw != null) {
            decodeHosts(raw)?.let { return it.sortedBy { host -> host.sortOrder } }
        }

        val backup = fileStore.readText(backupFileName())
        val recovered = backup?.let(::decodeHosts)
        if (recovered != null) {
            ControlDeskLog.gateway.w { "Hosts JSON was missing or invalid; restored paired devices from backup." }
            runCatching { fileStore.writeText(fileName, backup) }
            return recovered.sortedBy { it.sortOrder }
        }

        if (raw == null && backup == null) return emptyList()

        ControlDeskLog.gateway.e { "Hosts JSON is invalid and no valid backup exists; preserving the file for recovery." }
        return emptyList()
    }

    private fun backupFileName() = "$fileName.bak"

    private fun decodeHosts(raw: String): List<PairedHost>? =
        runCatching { json.decodeFromString<HostsFile>(raw).hosts }.getOrNull()

    companion object {
        const val HOSTS_FILE = "control_desk_hosts.json"
    }
}

@Serializable
private data class HostsFile(
    val hosts: List<PairedHost> = emptyList(),
)
