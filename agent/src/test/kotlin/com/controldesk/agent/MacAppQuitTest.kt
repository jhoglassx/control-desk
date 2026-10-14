package com.controldesk.agent

import com.controldesk.protocol.AppCloseRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacAppQuitTest {
    @Test
    fun closesOnlySelectedWindowWhenProcessHasMultipleWindows() {
        assertTrue(
            MacAppQuit.shouldCloseWindowOnly(
                AppCloseRequest(target = "Android Studio", pid = 120, axWindowIndex = 2, windowCount = 2),
            ),
        )
        assertFalse(
            MacAppQuit.shouldCloseWindowOnly(
                AppCloseRequest(target = "Android Studio", pid = 120, axWindowIndex = 2, windowCount = 1),
            ),
        )
        assertFalse(
            MacAppQuit.shouldCloseWindowOnly(
                AppCloseRequest(target = "Android Studio", windowCount = 2),
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
