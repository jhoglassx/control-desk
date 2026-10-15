package com.controldesk.agent

import com.controldesk.protocol.AppLaunchRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertContains

class MacFocusAppTest {
    @Test
    fun processWithoutWindowUsesApplicationLaunchInsteadOfInvalidWindowIndex() {
        listOf("Xcode", "Code", "Any App").forEach { app ->
            assertFalse(MacFocusApp.shouldFocusExistingProcess(AppLaunchRequest(target = app, pid = 120)))
        }
        assertContains(MacFocusApp.focusWindowScript(120, 1, null), "return \"no_windows\"")
    }

    @Test
    fun focusingResolvedWindowVerifiesItsCurrentTitleAndAcknowledgesAction() {
        val script = MacFocusApp.focusWindowScript(10, 2, "Same title")
        assertContains(script, "window 2 of targetProcess")
        assertContains(script, "name of targetWindow is not \"Same title\"")
        assertContains(script, "return \"focused\"")
        assertFalse(script.contains("try"))
    }

    @Test
    fun configuredProjectPathTakesPrecedenceOverBoundRunningWindow() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            windowTitle = "sample-android-app",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
            pid = 120,
        )

        assertFalse(MacFocusApp.shouldFocusExistingProcess(request))
    }

    @Test
    fun boundRunningWindowIsFocusedWhenNoProjectPathIsConfigured() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            pid = 120,
            axWindowIndex = 1,
        )

        assertTrue(MacFocusApp.shouldFocusExistingProcess(request))
    }

    @Test
    fun appWithoutProjectPathStillOpensWhenNoRunningWindowIsBound() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
        )

        assertFalse(MacFocusApp.shouldFocusExistingProcess(request))
    }
}
