package com.controldesk.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class MacWindowSnapshotTest {
    @Test
    fun noWindowSnapshotCannotEmitSyntheticWindowOneEvenIfOldFieldsArePresent() {
        listOf("Xcode", "Code", "Any App").forEach { name ->
            val obj = Json.parseToJsonElement("""{
                "appName":"$name", "pid":10, "hasWindow":false, "visible":true,
                "isActive":true, "windowCount":1, "windowIndex":0, "axWindowIndex":1, "windowNumber":1
            }""").jsonObject
            val row = requireNotNull(MacWindowLister.snapshotRow(obj, 0, "obsolete-id"))
            assertEquals(0, row.windowCount)
            assertEquals(null, row.axWindowIndex)
            assertEquals(null, row.windowIndex)
            assertEquals(null, row.windowNumber)
            assertEquals(null, row.stableWindowId)
            assertFalse(row.isVisible)
            assertFalse(row.isActive)
        }
    }
}
