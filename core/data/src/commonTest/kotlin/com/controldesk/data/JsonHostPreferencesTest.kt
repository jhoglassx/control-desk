package com.controldesk.data

import com.controldesk.data.storage.FileStore
import com.controldesk.domain.model.ApplicationShortcut
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.AppTileSize
import com.controldesk.domain.model.PairedHost
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class JsonHostPreferencesTest {
    @Test
    fun recoversPreviousHostsWhenWriteWasInterrupted() = runTest {
        val store = InterruptingFileStore()
        val preferences = JsonHostPreferences(store)
        val first = pairedHost("10.0.0.1:8741", 0)
        preferences.saveHost(first)
        store.interruptNextHostsWrite = true

        assertFails { preferences.saveHost(pairedHost("10.0.0.2:8741", 1)) }

        val restarted = JsonHostPreferences(store)
        assertEquals(listOf(first.id), restarted.getHosts().map { it.id })
        assertEquals(store.files[JsonHostPreferences.HOSTS_FILE + ".bak"], store.files[JsonHostPreferences.HOSTS_FILE])
    }

    @Test
    fun appOrderSurvivesPersistenceAndReload() = runTest {
        val store = InterruptingFileStore()
        val preferences = JsonHostPreferences(store)
        val host = pairedHost("10.0.0.1:8741", 0).copy(appOrder = listOf("terminal", "studio", "browser"))

        preferences.saveHost(host)

        assertEquals(host.appOrder, JsonHostPreferences(store).getHosts().single().appOrder)
    }

    @Test
    fun pinnedShortcutSizeSurvivesPersistenceAndReload() = runTest {
        val store = InterruptingFileStore()
        val preferences = JsonHostPreferences(store)
        val tileSizes = mapOf("/applications/android studio.app" to AppTileSize(columns = 2, rows = 2))
        val gridPositions = mapOf("app-android-studio-pid321-ax0-t0" to 11)
        val layoutIdentities = mapOf("app-android-studio-pid321-ax0-t0" to "window:/applications/android studio.app:title:bodytemperature")
        val host = pairedHost("10.0.0.1:8741", 0).copy(
            appTileSizes = tileSizes,
            appGridPositions = gridPositions,
            appLayoutIdentities = layoutIdentities,
            applicationShortcuts = listOf(ApplicationShortcut("persistent-id", "/applications/editor.app", "Editor", "Editor")),
        )

        preferences.saveHost(host)

        assertEquals(tileSizes, JsonHostPreferences(store).getHosts().single().appTileSizes)
        assertEquals(gridPositions, JsonHostPreferences(store).getHosts().single().appGridPositions)
        assertEquals(layoutIdentities, JsonHostPreferences(store).getHosts().single().appLayoutIdentities)
        assertEquals(host, JsonHostPreferences(store).getHosts().single())
    }

    private fun pairedHost(id: String, order: Int) = PairedHost(
        id = id,
        name = "Mac $order",
        host = id.substringBefore(':'),
        port = 8741,
        osType = OsType.MACOS,
        sortOrder = order,
        token = "token-$order",
    )

    private class InterruptingFileStore : FileStore {
        val files = mutableMapOf<String, String>()
        var interruptNextHostsWrite = false

        override fun readText(name: String): String? = files[name]

        override fun writeText(name: String, content: String) {
            if (name == JsonHostPreferences.HOSTS_FILE && interruptNextHostsWrite) {
                interruptNextHostsWrite = false
                files[name] = "{ truncated"
                error("simulated process interruption")
            }
            files[name] = content
        }
    }
}
