package com.controldesk.domain.model

import kotlinx.serialization.json.Json
import kotlin.test.*

class ControlShortcutTest {
    @Test fun defaultsMigrateAndExplicitRemovalSurvivesSerialization() {
        val old = """{"id":"mac","name":"Mac","host":"localhost","port":8741,"osType":"MACOS","sortOrder":0,"token":"test"}"""
        val host = Json.decodeFromString<PairedHost>(old)
        assertEquals(listOf("system-volume", "system-microphone"), host.controlShortcuts.map { it.id })
        val removed = host.copy(controlShortcuts = emptyList())
        assertEquals(emptyList(), Json.decodeFromString<PairedHost>(Json.encodeToString(removed)).controlShortcuts)
    }
    @Test fun cameraDestinationsAreExplicitAndNeverWindows() {
        val camera = ControlShortcut("camera-front", "Frente", ControlShortcutKind.CAMERA, ShortcutDestination.URL, "http://192.168.1.2")
        assertNull(camera.validationError())
        assertEquals(ActionType.REMOTE_BROWSER_OPEN_SITE, camera.toButton().actionType)
        assertEquals(camera.target, camera.toButton().payload["url"])
        val app = camera.copy(id = "camera-app", destination = ShortcutDestination.APPLICATION, target = "/Applications/Camera Hub.app")
        assertEquals(ActionType.REMOTE_LAUNCH, app.toButton().actionType)
        assertEquals(app.target, app.toButton().payload["bundlePath"])
        assertFalse(app.toButton().isActive)
    }
    @Test fun invalidUrlsAreRejected() {
        listOf("", "file:///etc/passwd", "javascript:alert(1)", "http://", "https://some host", "http://user:pass@host").forEach {
            assertNotNull(ControlShortcut("1", "Câmera", ControlShortcutKind.CAMERA, ShortcutDestination.URL, it).validationError())
        }
    }
    @Test fun controlsShareLayoutAndKeepAppRuntimeUntouched() {
        val host = PairedHost("1", "Mac", "localhost", 8741, OsType.MACOS, 0, "test",
            appOrder = listOf("app", "system-microphone", "system-volume"),
            appTileSizes = mapOf("card:system-volume" to AppTileSize(2, 1)))
        val app = GridButton("app", "Editor", ActionType.REMOTE_LAUNCH, isActive = true)
        val buttons = host.workspaceButtons(listOf(app))
        assertEquals(host.appOrder, buttons.map { it.id })
        assertEquals("2", buttons.last().payload["spanColumns"])
        assertEquals(app, buttons.first())
        assertFalse(buttons.last().isActive)
    }
}
