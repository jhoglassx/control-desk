package com.controldesk.agent

import com.controldesk.protocol.AppCloseRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
            MacAppQuit.CloseAction.TERMINATE_PROCESS,
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
