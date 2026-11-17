package com.controldesk.agent

import com.controldesk.protocol.RunningAppInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MacWindowTargetTest {
    @Test
    fun duplicateTitlesAreResolvedByIdAndProcessRatherThanTitleOrArrayOrder() {
        listOf("Editor", "Preview", "Any App").forEach { name ->
            val windows = listOf("101", "202").mapIndexed { index, id ->
                RunningAppInfo(name, name, pid = 10, stableWindowId = id, windowTitle = "Same title", axWindowIndex = index + 1)
            }
            assertEquals(2, MacWindowTarget.requireMatch("202", 10, windows.reversed()).axWindowIndex)
            assertFailsWith<IllegalArgumentException> { MacWindowTarget.requireMatch("missing", 10, windows) }
            assertFailsWith<IllegalArgumentException> { MacWindowTarget.requireMatch("202", 11, windows) }
            assertFailsWith<IllegalArgumentException> { MacWindowTarget.requireMatch("202", null, windows) }
        }
    }
}
