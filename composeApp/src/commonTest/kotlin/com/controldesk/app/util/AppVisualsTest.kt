package com.controldesk.app.util

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import kotlin.test.Test
import kotlin.test.assertEquals

class AppVisualsTest {
    @Test
    fun tileShowsWindowTitleToDistinguishInstancesAndKeepsAppNameForIconFallback() {
        val button = GridButton(
            id = "studio-project-a",
            label = "Android Studio · project-a",
            actionType = ActionType.REMOTE_LAUNCH,
        )

        assertEquals("project-a", button.tileLabel())
        assertEquals("Android Studio", button.tileAppName())
    }

    @Test
    fun tileFallsBackToApplicationNameWhenNoWindowTitleExists() {
        val button = GridButton(
            id = "studio",
            label = "Android Studio",
            actionType = ActionType.REMOTE_LAUNCH,
        )

        assertEquals("Android Studio", button.tileLabel())
    }
}
