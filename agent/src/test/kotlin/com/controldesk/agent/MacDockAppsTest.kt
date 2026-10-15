package com.controldesk.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.controldesk.protocol.RunningAppInfo

class MacDockAppsTest {
    @Test
    fun associatesProcessAliasesByBundleWithoutDependingOnIconCache() {
        val bundle = "/test/Review Editor.app"
        val pin = RunningAppInfo(name = "Review Editor", appName = "Review Editor", bundlePath = bundle,
            isDockPinned = true, isVisible = false, dockIndex = 3)
        val windows = listOf("one", "two").map { title ->
            RunningAppInfo(name = title, appName = "review-process", bundlePath = bundle, windowTitle = title)
        }
        val merged = MacDockApps.merge(windows, listOf(pin))
        assertEquals(2, merged.size)
        assertTrue(merged.all { it.isDockPinned && it.dockIndex == 3 })
        assertEquals(listOf(pin), MacDockApps.merge(emptyList(), listOf(pin)))
    }

    @Test
    fun equalNamesWithDifferentBundlesDoNotHideDockShortcut() {
        val pin = RunningAppInfo(name = "Editor", appName = "Editor", bundlePath = "/one/Editor.app",
            isDockPinned = true, isVisible = false)
        val window = RunningAppInfo(name = "Editor", appName = "Editor", bundlePath = "/two/Editor.app")
        assertEquals(listOf(window, pin), MacDockApps.merge(listOf(window), listOf(pin)))
    }

    @Test
    fun parseXmlReadsFileLabelAndUrl() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <array>
              <dict>
                <key>GUID</key>
                <integer>1</integer>
                <key>tile-data</key>
                <dict>
                  <key>file-data</key>
                  <dict>
                    <key>_CFURLString</key>
                    <string>file:///Applications/Cursor.app/</string>
                  </dict>
                  <key>file-label</key>
                  <string>Cursor</string>
                </dict>
              </dict>
              <dict>
                <key>GUID</key>
                <integer>2</integer>
                <key>tile-data</key>
                <dict>
                  <key>file-data</key>
                  <dict>
                    <key>_CFURLString</key>
                    <string>file:///Applications/Google%20Chrome.app/</string>
                  </dict>
                  <key>file-label</key>
                  <string>Google Chrome</string>
                </dict>
              </dict>
            </array>
        """.trimIndent()
        val apps = MacDockApps.parseXml(xml)
        assertEquals(listOf("Cursor", "Google Chrome"), apps.map { it.appName })
        assertEquals(listOf(0, 1), apps.map { it.dockIndex })
        assertEquals(listOf("/Applications/Cursor.app", "/Applications/Google Chrome.app"), apps.map { it.bundlePath })
        assertTrue(apps.all { it.isDockPinned && !it.isVisible })
    }
}
