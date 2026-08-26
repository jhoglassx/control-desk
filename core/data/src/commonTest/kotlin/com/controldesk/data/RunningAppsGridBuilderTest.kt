package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.RunningApp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RunningAppsGridBuilderTest {
    @Test
    fun sortsActiveThenVisibleThenLabel() {
        val apps = listOf(
            RunningApp(name = "Zed", appName = "Zed", isActive = false, isVisible = false),
            RunningApp(name = "Safari", appName = "Safari", isActive = false, isVisible = true),
            RunningApp(name = "Chrome", appName = "Chrome", isActive = true, isVisible = true),
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
}
