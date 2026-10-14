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

    @Test
    fun movingAnyAppUsesItsGridPositionAndKeepsAllAppsInRange() {
        assertEquals(1, appDropTarget(sourceTileIndex = 2, rowDelta = 0, columnDelta = 1, columns = 4, appCount = 4))
        assertEquals(3, appDropTarget(sourceTileIndex = 2, rowDelta = 1, columnDelta = 0, columns = 4, appCount = 4))
        assertEquals(0, appDropTarget(sourceTileIndex = 3, rowDelta = 0, columnDelta = -1, columns = 4, appCount = 4))
        assertEquals(2, appDropTarget(sourceTileIndex = 5, rowDelta = 0, columnDelta = -1, columns = 4, appCount = 4))
        assertEquals(null, appDropTarget(sourceTileIndex = 2, rowDelta = 0, columnDelta = 0, columns = 4, appCount = 1))
    }
}
