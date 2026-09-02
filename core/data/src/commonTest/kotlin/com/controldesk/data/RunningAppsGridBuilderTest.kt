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
    fun preferMergeKeepsEmptyPollAndPreviousIcons() {
        val current = listOf(
            GridButton(
                id = "app-Safari-wn-1",
                label = "Safari",
                actionType = ActionType.REMOTE_LAUNCH,
                iconBase64 = "abc",
            ),
        )
        assertEquals(current, RunningAppsGridBuilder.merge(current, emptyList(), preferMerge = true))
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
            listOf("app-Android Studio-pid101-ax1-t${"project-a".hashCode()}", "app-Android Studio-pid101-ax2-t${"project-b".hashCode()}"),
            buttons.filter { it.appNameFromPayload() == "Android Studio" }.map { it.id },
        )
    }

    private fun GridButton.appNameFromPayload(): String = payload.getValue("target")
}
