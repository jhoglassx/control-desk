package com.controldesk.agent

import com.controldesk.protocol.AppLaunchRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacFocusAppTest {
    @Test
    fun existingWindowTakesPrecedenceOverConfiguredProjectPath() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            windowTitle = "sample-android-app",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
            pid = 120,
            axWindowIndex = 2,
        )

        assertTrue(MacFocusApp.shouldFocusExistingWindow(request))
    }

    @Test
    fun configuredProjectOpensWhenNoRunningWindowIsBound() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
        )

        assertFalse(MacFocusApp.shouldFocusExistingWindow(request))
    }
}
