package com.controldesk.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MacDockAppsTest {
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
        assertTrue(apps.all { it.isDockPinned && !it.isVisible })
    }
}
