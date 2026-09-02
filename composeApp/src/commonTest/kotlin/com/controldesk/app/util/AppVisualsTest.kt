package com.controldesk.app.util

import kotlin.test.Test
import kotlin.test.assertEquals

class AppVisualsTest {
    @Test
    fun monogramUsesFirstLetterOfAppName() {
        assertEquals("S", monogramForLabel("Safari"))
        assertEquals("G", monogramForLabel("Google Chrome · GitHub"))
    }

    @Test
    fun monogramFallsBackWhenEmpty() {
        assertEquals("?", monogramForLabel("   "))
    }
}
