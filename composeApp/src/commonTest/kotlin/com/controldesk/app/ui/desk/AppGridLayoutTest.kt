package com.controldesk.app.ui.desk

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun resizedTilesReserveTheirWidthAndHeightBeforePlacingFollowingRows() {
        val positions = placeTiles(
            listOf(
                TileSpan(),
                TileSpan(),
                TileSpan(columns = 2, rows = 2),
                TileSpan(),
            ),
            columns = 4,
        )

        assertEquals(2, positions[2].column)
        assertEquals(0, positions[2].row)
        assertEquals(2, positions[3].row)
        assertEquals(0, positions[3].column)
    }

    @Test
    fun reorderTargetUsesOccupiedCellsForResizedShortcuts() {
        val spans = listOf(
            TileSpan(), TileSpan(),
            TileSpan(columns = 2), TileSpan(), TileSpan(), TileSpan(),
        )

        assertEquals(
            2,
            appDropTarget(
                sourceTileIndex = 5,
                rowDelta = 0,
                columnDelta = -1,
                columns = 4,
                appCount = 4,
                tileSpans = spans,
            ),
        )
    }

    @Test
    fun gridUsesSavedPositionsAsOrderAndAlwaysPacksCards() {
        val spans = listOf(TileSpan(), TileSpan(), TileSpan(), TileSpan())
        val positions = placeTilesInGrid(
            spans = spans,
            columns = 4,
            preferredCells = mapOf(2 to 2, 3 to 8),
        )

        assertEquals(0, positions[2].row)
        assertEquals(2, positions[2].column)
        assertEquals(0, positions[3].row)
        assertEquals(3, positions[3].column)
        assertEquals(listOf(0, 1, 2, 3), positions.map { it.row * 4 + it.column })
    }

    @Test
    fun reorderedAndResizedCardsReflowWithoutEmptyCells() {
        val spans = listOf(
            TileSpan(), TileSpan(), TileSpan(columns = 2, rows = 2), TileSpan(), TileSpan(),
        )
        val positions = placeTilesInGrid(
            spans = spans,
            columns = 4,
            preferredCells = mapOf(2 to 20, 3 to 2, 4 to 30),
        )

        assertEquals(0, positions[3].row)
        assertEquals(2, positions[3].column)
        assertEquals(1, positions[2].row)
        assertEquals(0, positions[2].column)
        assertEquals(0, positions[4].row)
        assertEquals(3, positions[4].column)
    }

    @Test
    fun droppingCardBetweenTwoItemsProducesAStableCompactOrder() {
        val ids = listOf("a", "b", "c")
        val positions = placeTilesInGrid(
            spans = listOf(TileSpan(), TileSpan(), TileSpan(), TileSpan(), TileSpan()),
            columns = 4,
        )

        assertEquals(
            listOf("a", "c", "b"),
            reorderGridIdsAtCell(ids, positions, draggedId = "c", targetRow = 0, targetColumn = 2),
        )
        assertEquals(
            listOf("b", "c", "a"),
            reorderGridIdsAtCell(ids, positions, draggedId = "a", targetRow = 1, targetColumn = 1),
        )
    }

    @Test
    fun tallAndWideTilesCanBeMixedAndReflowToAvailableCells() {
        val positions = placeTilesInGrid(
            spans = listOf(
                TileSpan(), TileSpan(), TileSpan(columns = 1, rows = 2), TileSpan(columns = 2), TileSpan(),
            ),
            columns = 4,
        )

        assertEquals(0, positions[2].row)
        assertEquals(2, positions[2].column)
        assertEquals(1, positions[3].row)
        assertEquals(0, positions[3].column)
        assertEquals(0, positions[4].row)
        assertEquals(3, positions[4].column)
    }

    @Test
    fun mixedGridSpansNeverOverlap() {
        val positions = placeTilesInGrid(
            spans = listOf(
                TileSpan(), TileSpan(), TileSpan(columns = 1, rows = 2),
                TileSpan(columns = 2), TileSpan(columns = 2, rows = 2),
                TileSpan(), TileSpan(columns = 1, rows = 2), TileSpan(),
            ),
            columns = 4,
            preferredCells = mapOf(2 to 3, 3 to 0, 4 to 1, 5 to 4, 6 to 2, 7 to 5),
        )
        val occupiedCells = mutableSetOf<Pair<Int, Int>>()

        positions.forEach { position ->
            for (row in position.row until position.row + position.span.rows) {
                for (column in position.column until position.column + position.span.columns) {
                    assertTrue(occupiedCells.add(row to column), "Grid cells must have at most one card")
                }
            }
        }
    }
}
