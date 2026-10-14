package com.controldesk.agent

import com.controldesk.protocol.AppLaunchRequest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MacFocusAppTest {
    @Test
    fun existingProcessTakesPrecedenceOverConfiguredProjectPathEvenWithoutAxIndex() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            windowTitle = "sample-android-app",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
            pid = 120,
        )

        assertTrue(MacFocusApp.shouldFocusExistingProcess(request))
    }

    @Test
    fun configuredProjectOpensWhenNoRunningWindowIsBound() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
        )

        assertFalse(MacFocusApp.shouldFocusExistingProcess(request))
    }
}
