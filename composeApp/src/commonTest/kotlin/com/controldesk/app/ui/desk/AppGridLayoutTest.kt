package com.controldesk.app.ui.desk

import kotlin.test.Test
import kotlin.test.assertEquals

class AppGridLayoutTest {
    @Test
    fun choosesEvenColumnsForAvailableWidth() {
        assertEquals(4, appGridColumnCount(398f))
        assertEquals(3, appGridColumnCount(320f))
        assertEquals(1, appGridColumnCount(80f))
    }

    @Test
    fun rowsKeepAllTilesInEqualWidthTracksAndPadOnlyTheLastRow() {
        assertEquals(4, appGridRowCount(appCount = 14, columns = 4))
        assertEquals(4, appGridRowCount(appCount = 14, columns = 4, systemTileCount = 0))
        assertEquals(1, appGridRowCount(appCount = 0, columns = 4))
        assertEquals(16, appGridRowCount(appCount = 14, columns = 0))
    }
}
