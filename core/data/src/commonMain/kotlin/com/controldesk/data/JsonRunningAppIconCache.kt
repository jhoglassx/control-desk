package com.controldesk.data

import com.controldesk.domain.repository.RunningAppIconCache
import com.controldesk.data.storage.FileStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class JsonRunningAppIconCache(
    private val fileStore: FileStore,
    private val fileName: String = ICON_CACHE_FILE,
) : RunningAppIconCache {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mutex = Mutex()
    private var memory: MutableMap<String, String> = readFromDisk().toMutableMap()

    override suspend fun load(): Map<String, String> = mutex.withLock { memory.toMap() }

    override suspend fun putIfAbsent(buttonId: String, base64: String) {
        if (base64.isBlank()) return
        mutex.withLock {
            if (memory.containsKey(buttonId)) return
            memory[buttonId] = base64
            fileStore.writeText(fileName, json.encodeToString(IconCacheFile(memory)))
        }
    }

    override fun peek(buttonId: String): String? = memory[buttonId]

    fun hydrate(snapshot: Map<String, String>) {
        memory = snapshot.toMutableMap()
    }

    private fun readFromDisk(): Map<String, String> {
        val raw = fileStore.readText(fileName) ?: return emptyMap()
        return runCatching { json.decodeFromString<IconCacheFile>(raw).icons }
            .getOrElse { emptyMap() }
    }

    companion object {
        const val ICON_CACHE_FILE = "control_desk_icon_cache.json"
    }
}

@Serializable
private data class IconCacheFile(
    val icons: Map<String, String> = emptyMap(),
)
