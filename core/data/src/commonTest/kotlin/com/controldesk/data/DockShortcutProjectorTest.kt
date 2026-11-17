package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.AppTileSize
import com.controldesk.domain.model.ApplicationShortcut
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.RunningApp
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DockShortcutProjectorTest {
    private val host = PairedHost("host", "Mac", "localhost", 8741, OsType.MACOS, 0, "token")

    @Test
    fun missingBundleThenResolvedBundleReusesOriginalLauncherAndLayout() {
        listOf("Visual Studio Code" to "Code", "Android Studio" to "studio", "Any Editor" to "process").forEach { (name, process) ->
            val initial = DockShortcutProjector.project(host, RunningAppsGridBuilder.build(listOf(
                RunningApp(name, name, isVisible = false, isDockPinned = true))))
            val id = initial.buttons.single().id
            val saved = initial.host.copy(appTileSizes = mapOf("card:$id" to AppTileSize(1, 2)), appGridPositions = mapOf(id to 4))
            val apps = listOf(RunningApp(name, process, bundlePath = "/Applications/$name.app", pid = 10, windowCount = 1))
            val result = DockShortcutProjector.project(saved, RunningAppsGridBuilder.build(apps))
            assertEquals(listOf(id), result.buttons.map { it.id })
            assertEquals(AppTileSize(1, 2), result.host.appTileSizes["card:$id"])
            assertEquals(4, result.host.appGridPositions[id])
            assertEquals("/Applications/$name.app", result.buttons.single().payload["bundlePath"])
            val missingAgain = DockShortcutProjector.project(result.host, RunningAppsGridBuilder.build(listOf(
                RunningApp(name, process, pid = 10, windowCount = 1))))
            assertEquals(listOf(id), missingAgain.buttons.map { it.id })
        }
    }

    @Test
    fun alreadyDuplicatedCatalogIsRepairedWithoutLosingOrderOrSize() {
        val path = "/Applications/Any Editor.app"
        val first = ApplicationShortcut("original", "any editor", "Any Editor", "Any Editor")
        val duplicate = ApplicationShortcut("duplicate", path.lowercase(), "Any Editor", "process", path)
        val saved = host.copy(applicationShortcuts = listOf(first, duplicate), appOrder = listOf(first.id, duplicate.id),
            appGridPositions = mapOf(first.id to 3), appTileSizes = mapOf("card:${duplicate.id}" to AppTileSize(2, 2)))
        val result = DockShortcutProjector.project(saved, emptyList())
        assertEquals(listOf(first.id), result.buttons.map { it.id })
        assertEquals(listOf(first.id), result.host.appOrder)
        assertEquals(3, result.host.appGridPositions[first.id])
        assertEquals(AppTileSize(2, 2), result.host.appTileSizes["card:${first.id}"])
    }

    @Test
    fun ambiguousSameNameBundlesDoNotAbsorbNameOnlyShortcut() {
        val shortcuts = listOf(
            ApplicationShortcut("fallback", "editor", "Editor", "Editor"),
            ApplicationShortcut("one", "/one/editor.app", "Editor", "Editor", "/one/Editor.app"),
            ApplicationShortcut("two", "/two/editor.app", "Editor", "Editor", "/two/Editor.app"),
        )
        assertEquals(3, DockShortcutProjector.project(host.copy(applicationShortcuts = shortcuts), emptyList()).buttons.size)
    }

    @Test
    fun runningProcessWithNoWindowsIsNotASelectableWindow() {
        val result = DockShortcutProjector.project(host, RunningAppsGridBuilder.build(listOf(
            RunningApp("Any Editor", "process", bundlePath = "/Applications/Any Editor.app", pid = 10,
                windowCount = 0, axWindowIndex = 1))))
        val button = result.buttons.single()
        assertEquals("0", button.payload["instanceCount"])
        assertEquals(null, button.payload["pid"])
        assertEquals(null, button.payload["axWindowIndex"])
    }

    @Test
    fun browserTabsDoNotCreateMoreLaunchersOrReplaceNativeWindowActions() {
        val bundle = "/Applications/Any Browser.app"
        val apps = listOf(
            RunningApp("Any Browser", "Any Browser", pid = 10, stableWindowId = "window", bundlePath = bundle),
            RunningApp("Tab one", "Any Browser", isBrowserTab = true, windowTitle = "Tab one", bundlePath = bundle),
            RunningApp("Tab two", "Any Browser", isBrowserTab = true, windowTitle = "Tab two", bundlePath = bundle),
        )
        val result = DockShortcutProjector.project(host, RunningAppsGridBuilder.build(apps))
        assertEquals(1, result.buttons.size)
        assertEquals("1", result.buttons.single().payload["instanceCount"])
        assertEquals(ActionType.REMOTE_LAUNCH, result.buttons.single().actionType)
        assertEquals("window", result.buttons.single().payload["stableWindowId"])
    }

    @Test
    fun launcherIdentitySurvivesAllWindowTransitionsAndDiskRoundTripForEveryApp() {
        listOf("Visual Studio Code" to "Code", "Android Studio" to "studio",
            "Preview" to "Preview", "Any Editor" to "arbitrary-process").forEach { (name, process) ->
            val bundle = "/Applications/$name.app"
            fun window(id: String?, title: String, pid: Int) = RunningApp(title, process, pid = pid,
                windowTitle = title, stableWindowId = id, bundlePath = bundle, isVisible = true)
            val dock = RunningApp(name, name, isVisible = false, isDockPinned = true, bundlePath = bundle)
            var projection = DockShortcutProjector.project(host, RunningAppsGridBuilder.build(listOf(dock)))
            val id = projection.buttons.single().id
            var saved = projection.host.copy(appTileSizes = mapOf("card:$id" to AppTileSize(1, 2)),
                appGridPositions = mapOf(id to 7))
            val snapshots = listOf(
                listOf(window(null, "", 10)),
                listOf(window("41", "first", 10)),
                listOf(window("42", "second", 10), window("41", "first renamed", 10)),
                listOf(window("42", "second", 10)),
                emptyList(), listOf(dock), listOf(window("99", "new", 20)),
            )
            snapshots.forEach { apps ->
                saved = Json.decodeFromString<PairedHost>(Json.encodeToString(saved))
                projection = DockShortcutProjector.project(saved, RunningAppsGridBuilder.build(apps))
                assertEquals(id, projection.buttons.single().id, name)
                assertEquals(7, projection.host.appGridPositions[id], name)
                assertEquals(AppTileSize(1, 2), projection.host.appTileSizes["card:$id"], name)
                assertEquals(listOf(id), projection.host.appOrder, name)
                saved = projection.host
            }
        }
    }

    @Test
    fun identicalTitlesAndSeparateProcessesStaySelectableWithinOneLauncher() {
        val apps = listOf(1, 2).map { pid -> RunningApp("same", "Editor", pid = pid,
            windowTitle = "same", stableWindowId = "$pid", bundlePath = "/Applications/Editor.app") }
        val result = DockShortcutProjector.project(host, RunningAppsGridBuilder.build(apps))
        val tile = result.buttons.single()
        assertEquals("2", tile.payload["instanceCount"])
        assertEquals(setOf("1", "2"), result.windows.getValue(tile.id).map { it.payload["stableWindowId"] }.toSet())
    }

    @Test
    fun sameBundleNameAtDifferentPathsCreatesIndependentLaunchers() {
        val apps = listOf("/Applications/Editor.app", "/Users/test/Editor.app").map {
            RunningApp("Editor", "Editor", bundlePath = it, isVisible = true, stableWindowId = it)
        }
        val result = DockShortcutProjector.project(host, RunningAppsGridBuilder.build(apps))
        assertEquals(2, result.buttons.map { it.id }.distinct().size)
    }

    @Test
    fun migratesLegacyLayoutOnceAndNeverReappliesStaleWindowSize() {
        val key = "/applications/editor.app"
        val old = host.copy(appOrder = listOf("old"), appGridPositions = mapOf("old" to 3),
            appLayoutIdentities = mapOf("old" to "window:$key:title:project"),
            appTileSizes = mapOf("window:$key:title:project" to AppTileSize(2, 2)))
        val apps = RunningAppsGridBuilder.build(listOf(RunningApp("Editor", "Editor", bundlePath = "/Applications/Editor.app")))
        val first = DockShortcutProjector.project(old, apps)
        val id = first.buttons.single().id
        assertEquals(AppTileSize(2, 2), first.host.appTileSizes["card:$id"])
        assertEquals(3, first.host.appGridPositions[id])
        val edited = first.host.copy(appTileSizes = first.host.appTileSizes + ("card:$id" to AppTileSize(1, 2)))
        assertEquals(AppTileSize(1, 2), DockShortcutProjector.project(edited, apps).host.appTileSizes["card:$id"])
    }
}
