package com.controldesk.data

import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ShortcutWindowBinderTest {
    @Test
    fun overlappingFavoritesAndDefaultLauncherNeverOwnTheSameWindow() {
        listOf("Editor", "Any App", "IDE").forEach { app ->
            val bundle = "/Applications/$app.app"
            fun shortcut(id: String, project: String? = null) = GridButton(id, id, ActionType.REMOTE_LAUNCH,
                payload = mapOf("target" to app, "bundlePath" to bundle) +
                    (project?.let { mapOf("projectPath" to "/projects/$it") } ?: emptyMap()), isFavoriteShortcut = true)
            fun window(id: String, project: String, active: Boolean) = GridButton(id, project, ActionType.REMOTE_LAUNCH,
                payload = mapOf("target" to app, "bundlePath" to bundle, "windowTitle" to project, "stableWindowId" to id),
                isActive = active, isVisible = true)
            val project = shortcut("project", "work")
            val duplicateProject = shortcut("duplicate-project", "work")
            val generic = shortcut("generic")
            val launcher = shortcut("default").copy(isFavoriteShortcut = false)
            val windows = listOf(window("1", "work", true), window("2", "work", false), window("3", "other", false))
            val result = ShortcutWindowBinder.bind(listOf(generic, project, duplicateProject), listOf(launcher), windows,
                mapOf(launcher.id to windows))
            assertEquals(listOf("1", "2"), result.windows.getValue(project.id).map { it.id })
            assertEquals(listOf("3"), result.windows.getValue(generic.id).map { it.id })
            assertEquals(emptyList(), result.windows.getValue(duplicateProject.id))
            assertEquals(3, result.windows.values.flatten().map { it.id }.distinct().size)
            assertEquals(3, result.windows.values.sumOf { it.size })
            assertEquals(1, result.buttons.count { it.isActive })
            assertFalse(result.buttons.any { it.id == launcher.id })
        }
    }
}
