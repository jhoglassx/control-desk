package com.controldesk.agent

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacAppQuitTest {
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
