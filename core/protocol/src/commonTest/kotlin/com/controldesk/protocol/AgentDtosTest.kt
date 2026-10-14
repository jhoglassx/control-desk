package com.controldesk.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class AgentDtosTest {
    @Test
    fun launchRequestDefaultsToAppName() {
        val request = AppLaunchRequest(target = "Safari")
        assertEquals("appName", request.targetType)
    }

    @Test
    fun launchRequestCarriesSpecificAppAndProjectPath() {
        val request = AppLaunchRequest(
            target = "Android Studio",
            bundlePath = "/Applications/Android Studio.app",
            projectPath = "/Users/test/Projects/sample-android-app",
        )
        assertEquals("/Applications/Android Studio.app", request.bundlePath)
        assertEquals("/Users/test/Projects/sample-android-app", request.projectPath)
    }

    @Test
    fun tabSwitchDefaults() {
        val request = BrowserTabSwitchRequest(value = "Gmail")
        assertEquals("chrome", request.browser)
        assertEquals("select", request.action)
        assertEquals(true, request.focusFirst)
    }
}
