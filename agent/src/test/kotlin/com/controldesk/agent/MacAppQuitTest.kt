package com.controldesk.agent

import com.controldesk.protocol.AppCloseRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertContains

class MacAppQuitTest {
    @Test
    fun closesOnlySelectedWindowWhenProcessHasMultipleWindows() {
        assertEquals(
            MacAppQuit.CloseAction.CLOSE_WINDOW,
            MacAppQuit.closeAction(
                AppCloseRequest(target = "Android Studio", pid = 120, axWindowIndex = 2, windowCount = 2),
                matchingProcessCount = 1,
            ),
        )
        assertEquals(
            MacAppQuit.CloseAction.CLOSE_WINDOW,
            MacAppQuit.closeAction(
                AppCloseRequest(target = "Android Studio", pid = 120, axWindowIndex = 2, windowCount = 1),
                matchingProcessCount = 1,
            ),
        )
    }

    @Test
    fun doesNotCloseEveryProcessWhenRequestHasNoInstanceIdentity() {
        assertEquals(
            MacAppQuit.CloseAction.IGNORE,
            MacAppQuit.closeAction(
                AppCloseRequest(target = "Android Studio"),
                matchingProcessCount = 2,
            ),
        )
        assertEquals(
            MacAppQuit.CloseAction.IGNORE,
            MacAppQuit.closeAction(
                AppCloseRequest(target = "Android Studio", pid = 120, windowCount = 2),
                matchingProcessCount = 1,
            ),
        )
        assertEquals(
            MacAppQuit.CloseAction.IGNORE,
            MacAppQuit.closeAction(
                AppCloseRequest(target = "Android Studio", pid = 120),
                matchingProcessCount = 1,
            ),
        )
        assertEquals(
            MacAppQuit.CloseAction.IGNORE,
            MacAppQuit.closeAction(
                AppCloseRequest(target = "Android Studio", pid = 0, windowCount = 1),
                matchingProcessCount = 2,
            ),
        )
    }

    @Test
    fun closeWindowUsesStableTitleBeforePotentiallyStaleAccessibilityIndex() {
        val script = MacAppQuit.closeWindowScript(
            pid = 120,
            index = 2,
            title = "project \"blue\"",
        )

        assertContains(script, "whose name is \"project \\\"blue\\\"\"")
        assertContains(script, "value of attribute \"AXCloseButton\" of targetWindow")
        assertContains(script, "perform action \"AXPress\" of closeControl")
        assertContains(script, "if closeControl is missing value then")
        assertContains(script, "(count of matchingWindows) is not 1")
        assertFalse(script.contains("set targetWindow to window 2 of targetProcess"))
    }

    @Test
    fun closeWindowFallsBackToAccessibilityIndexWhenTitleIsUnavailable() {
        val script = MacAppQuit.closeWindowScript(pid = 120, index = 2, title = null)

        assertContains(script, "set targetWindow to window 2 of targetProcess")
    }

    @Test
    fun matchesMainBinaryAndSkipsHelpers() {
        val bundle = "/Applications/Google Chrome.app"
        assertTrue(
            MacAppQuit.matchesMainApp(
                command = "$bundle/Contents/MacOS/Google Chrome",
                app = "Google Chrome",
                bundle = bundle,
                exe = "Google Chrome",
            ),
        )
        assertFalse(
            MacAppQuit.matchesMainApp(
                command = "$bundle/Contents/Frameworks/Google Chrome Helper.app/Contents/MacOS/Google Chrome Helper",
                app = "Google Chrome",
                bundle = bundle,
                exe = "Google Chrome",
            ),
        )
        assertTrue(
            MacAppQuit.matchesMainApp(
                command = "/Applications/Cursor.app/Contents/MacOS/Cursor",
                app = "Cursor",
                bundle = "/Applications/Cursor.app",
                exe = "Cursor",
            ),
        )
    }
}
