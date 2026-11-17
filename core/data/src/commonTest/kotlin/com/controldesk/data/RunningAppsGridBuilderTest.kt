package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.RunningApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunningAppsGridBuilderTest {
    @Test
    fun doesNotMoveActiveAppToFront() {
        val apps = listOf(
            RunningApp(name = "Zed", appName = "Zed", isActive = true, isVisible = true),
            RunningApp(name = "Safari", appName = "Safari", isActive = false, isVisible = true),
            RunningApp(name = "Chrome", appName = "Chrome", isActive = false, isVisible = true),
        )
        val buttons = RunningAppsGridBuilder.build(apps)
        assertEquals(listOf("Chrome", "Safari", "Zed"), buttons.map { it.label })
        assertEquals(0, buttons[0].row)
        assertEquals(0, buttons[0].col)
        assertEquals(1, buttons[1].col)
    }

    @Test
    fun chromeTabsUseBrowserAction() {
        val apps = listOf(
            RunningApp(
                name = "Chrome · Gmail",
                appName = "Google Chrome",
                windowTitle = "Gmail",
                isBrowserTab = true,
                browserWindowIndex = 0,
            ),
        )
        val button = RunningAppsGridBuilder.build(apps).single()
        assertEquals(ActionType.REMOTE_BROWSER_TAB, button.actionType)
        assertEquals("select", button.payload["action"])
        assertEquals("Gmail", button.payload["value"])
        assertEquals("Google Chrome", button.payload["target"])
    }

    @Test
    fun dockOrderStaysEvenWhenAnotherAppIsActive() {
        val apps = listOf(
            RunningApp(
                name = "Safari",
                appName = "Safari",
                isActive = true,
                isVisible = true,
                monitorIndex = 1,
                isDockPinned = true,
                dockIndex = 1,
            ),
            RunningApp(
                name = "Zed",
                appName = "Zed",
                isActive = false,
                isVisible = false,
                monitorIndex = 0,
                isDockPinned = true,
                dockIndex = 0,
            ),
        )
        val buttons = RunningAppsGridBuilder.build(apps)
        assertEquals(listOf("Zed", "Safari"), buttons.map { it.label })
        assertEquals("app-Zed-dock", buttons.first().id)
        assertTrue(buttons.first().isDockPinned)
        assertEquals(0, buttons.first().monitorIndex)
    }

    @Test
    fun dockLabelAndRunningProcessNameShareCanonicalBundleIdentity() {
        val bundle = "/Applications/Visual Studio Code.app"
        val dockShortcut = RunningAppsGridBuilder.build(
            listOf(RunningApp(
                name = "Visual Studio Code",
                appName = "Visual Studio Code",
                isVisible = false,
                isDockPinned = true,
                bundlePath = bundle,
            )),
        ).single()
        val runningWindow = RunningAppsGridBuilder.build(
            listOf(RunningApp(
                name = "Code · Welcome",
                appName = "Code",
                pid = 31174,
                windowTitle = "Welcome",
                bundlePath = bundle,
                isVisible = true,
            )),
        ).single()

        assertEquals("dock:/applications/visual studio code.app", dockShortcut.id)
        assertTrue(runningWindow.id.startsWith("app-Visual Studio Code-pid31174-"))
        assertEquals(bundle, dockShortcut.payload["bundlePath"])
        assertEquals(bundle, runningWindow.payload["bundlePath"])
    }

    @Test
    fun mergeClearsEmptyRuntimeSnapshotButKeepsPreviousIconsForPresentWindows() {
        val current = listOf(
            GridButton(
                id = "app-Safari-wn-1",
                label = "Safari",
                actionType = ActionType.REMOTE_LAUNCH,
                iconBase64 = "abc",
            ),
        )
        assertTrue(RunningAppsGridBuilder.merge(current, emptyList(), preferMerge = true).isEmpty())
        val incoming = listOf(
            GridButton(
                id = "app-Safari-wn-1",
                label = "Safari",
                actionType = ActionType.REMOTE_LAUNCH,
                iconBase64 = null,
            ),
        )
        val merged = RunningAppsGridBuilder.merge(current, incoming, preferMerge = true)
        assertEquals("abc", merged.single().iconBase64)
        assertTrue(RunningAppsGridBuilder.merge(current, emptyList(), preferMerge = false).isEmpty())
    }

    @Test
    fun keepsMonitorIndexOnTiles() {
        val apps = listOf(
            RunningApp(name = "Safari", appName = "Safari", isActive = true, isVisible = true, monitorIndex = 1),
            RunningApp(name = "Studio", appName = "Android Studio", isActive = false, isVisible = true, monitorIndex = 2),
        )
        val buttons = RunningAppsGridBuilder.build(apps)
        assertEquals(1, buttons.first { it.label == "Safari" }.monitorIndex)
        assertEquals(2, buttons.first { it.label == "Studio" }.monitorIndex)
    }

    @Test
    fun multipleInstancesOfSameAppGetDistinctIds() {
        val apps = listOf(
            RunningApp(
                name = "Android Studio · project-a",
                appName = "Android Studio",
                pid = 101,
                windowTitle = "project-a",
                axWindowIndex = 1,
                isActive = true,
                isVisible = true,
            ),
            RunningApp(
                name = "Android Studio · project-b",
                appName = "Android Studio",
                pid = 101,
                windowTitle = "project-b",
                axWindowIndex = 2,
                isActive = false,
                isVisible = true,
            ),
            RunningApp(
                name = "Google Chrome",
                appName = "Google Chrome",
                pid = 202,
                axWindowIndex = 1,
                isActive = false,
                isVisible = true,
            ),
            RunningApp(
                name = "Google Chrome · Docs",
                appName = "Google Chrome",
                pid = 202,
                windowTitle = "Docs",
                axWindowIndex = 2,
                isActive = false,
                isVisible = true,
            ),
        )
        val buttons = RunningAppsGridBuilder.build(apps)
        assertEquals(4, buttons.size)
        assertEquals(4, buttons.map { it.id }.toSet().size)
        assertEquals(
            listOf("Android Studio · project-a", "Android Studio · project-b"),
            buttons.filter { it.appNameFromPayload() == "Android Studio" }.map { it.label },
        )
        assertEquals(
            listOf("project-a", "project-b").map { title ->
                "app-Android Studio-pid101-ax${if (title == "project-a") 1 else 2}-t${title.hashCode()}-${title.reversed().hashCode()}-${title.length}"
            },
            buttons.filter { it.appNameFromPayload() == "Android Studio" }.map { it.id },
        )
    }

    @Test
    fun distinctWindowTitlesWithSameHashKeepSeparateTileIds() {
        val buttons = RunningAppsGridBuilder.build(
            listOf(
                RunningApp("App · Aa", "App", pid = 101, windowTitle = "Aa", axWindowIndex = 1),
                RunningApp("App · BB", "App", pid = 101, windowTitle = "BB", axWindowIndex = 1),
            ),
        )

        assertEquals(2, buttons.size)
        assertEquals(2, buttons.map { it.id }.distinct().size)
    }

    @Test
    fun quartzWindowIdentitySurvivesTitleAndAccessibilityIndexChanges() {
        val before = RunningAppsGridBuilder.build(
            listOf(
                RunningApp(
                    name = "Visual Studio Code · workspace-a",
                    appName = "Visual Studio Code",
                    pid = 501,
                    windowTitle = "workspace-a",
                    axWindowIndex = 1,
                    stableWindowId = "9841",
                ),
            ),
        ).single()
        val after = RunningAppsGridBuilder.build(
            listOf(
                RunningApp(
                    name = "Code · workspace-a [Modified]",
                    appName = "Visual Studio Code",
                    pid = 501,
                    windowTitle = "workspace-a [Modified]",
                    axWindowIndex = 2,
                    stableWindowId = "9841",
                ),
            ),
        ).single()

        assertEquals(before.id, after.id)
        assertEquals("9841", after.payload["stableWindowId"])
    }

    @Test
    fun simultaneousWindowsOfAnyAppUseIndependentQuartzIdentities() {
        val buttons = RunningAppsGridBuilder.build(
            listOf(
                RunningApp("Code · repo-a", "Visual Studio Code", pid = 501, stableWindowId = "9841"),
                RunningApp("Code · repo-b", "Visual Studio Code", pid = 501, stableWindowId = "9842"),
                RunningApp("Preview · a.pdf", "Preview", pid = 602, stableWindowId = "1288"),
                RunningApp("Preview · b.pdf", "Preview", pid = 602, stableWindowId = "1289"),
            ),
        )

        assertEquals(4, buttons.size)
        assertEquals(4, buttons.map { it.id }.distinct().size)
        assertEquals(
            setOf("9841", "9842", "1288", "1289"),
            buttons.map { it.payload.getValue("stableWindowId") }.toSet(),
        )
    }

    @Test
    fun closePayloadPreservesProcessAndWindowIdentity() {
        val button = RunningAppsGridBuilder.build(
            listOf(
                RunningApp(
                    name = "Android Studio · project-a",
                    appName = "Android Studio",
                    pid = 101,
                    windowTitle = "project-a",
                    windowIndex = 0,
                    axWindowIndex = 1,
                    windowCount = 2,
                    bundlePath = "/Applications/Android Studio.app",
                    isVisible = true,
                ),
            ),
        ).single()

        assertEquals("101", button.payload["pid"])
        assertEquals("project-a", button.payload["windowTitle"])
        assertEquals("1", button.payload["axWindowIndex"])
        assertEquals("2", button.payload["windowCount"])
        assertEquals("/Applications/Android Studio.app", button.payload["bundlePath"])
    }

    @Test
    fun duplicateReportsOfTheSameWindowProduceOneCard() {
        val window = RunningApp(
            name = "Android Studio · sample-android-app",
            appName = "Android Studio",
            pid = 101,
            windowTitle = "sample-android-app",
            axWindowIndex = 1,
            isVisible = true,
        )

        val buttons = RunningAppsGridBuilder.build(listOf(window, window.copy(isActive = true)))

        assertEquals(1, buttons.size)
        assertEquals("Android Studio · sample-android-app", buttons.single().label)
    }

    private fun GridButton.appNameFromPayload(): String = payload.getValue("target")
}
