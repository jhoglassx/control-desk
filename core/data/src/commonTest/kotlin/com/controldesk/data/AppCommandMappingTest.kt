package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.protocol.AppCloseRequest
import com.controldesk.protocol.AppLaunchRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppCommandMappingTest {
    @Test
    fun bothTransportsKeepSelectedWindowIdentityAcrossWireSerialization() {
        listOf("Editor", "Preview", "Any App").forEach { name ->
            val button = GridButton("persistent-shortcut", name, ActionType.REMOTE_LAUNCH, payload = mapOf(
                "target" to name, "pid" to "10", "stableWindowId" to "202", "axWindowIndex" to "2",
                "windowTitle" to "Same title", "windowCount" to "2", "projectPath" to "/projects/work"))
            val launch = Json.decodeFromString<AppLaunchRequest>(Json.encodeToString(button.toLaunchRequest()))
            val close = Json.decodeFromString<AppCloseRequest>(Json.encodeToString(button.toCloseRequest()))
            assertEquals("202", launch.stableWindowId)
            assertEquals("202", close.stableWindowId)
            assertEquals(10, launch.pid)
            assertEquals(10, close.pid)
            assertEquals(2, close.axWindowIndex)
            assertEquals("/projects/work", launch.projectPath)
        }
    }
}
