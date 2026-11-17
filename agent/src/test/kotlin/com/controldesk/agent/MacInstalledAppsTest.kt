package com.controldesk.agent

import com.controldesk.protocol.AppLaunchRequest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MacInstalledAppsTest {
    @Test
    fun catalogFindsAppsInUserApplications() {
        val home = Files.createTempDirectory("control-desk-app-catalog-").toFile()
        try {
            val app = File(home, "Applications/Example IDE.app")
            assertEquals(true, app.mkdirs())
            File(home, "Applications/readme.txt").writeText("ignored")

            val catalog = MacInstalledApps.list(home.absolutePath) { name, path -> "icon:$name:$path" }

            val discovered = catalog.first { it.name == "Example IDE" }
            assertEquals(app.absolutePath, discovered.bundlePath)
            assertEquals("icon:Example IDE:${app.absolutePath}", discovered.iconBase64)
        } finally {
            home.deleteRecursively()
        }
    }

    @Test
    fun projectLaunchRejectsMissingPathBeforeLaunching() {
        val home = Files.createTempDirectory("control-desk-app-launch-").toFile()
        try {
            val bundle = File(home, "Example IDE.app")
            assertEquals(true, bundle.mkdirs())
            assertFailsWith<IllegalArgumentException> {
                MacFocusApp.focus(
                    AppLaunchRequest(
                        target = "Example IDE",
                        bundlePath = bundle.absolutePath,
                        projectPath = File(home, "missing-project").absolutePath,
                    ),
                )
            }
        } finally {
            home.deleteRecursively()
        }
    }
}
