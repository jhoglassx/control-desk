package com.controldesk.app.ui.desk

import com.controldesk.app.theme.DeskDesign
import kotlin.math.floor

internal fun appGridColumnCount(
    availableWidthDp: Float,
    tileSizeDp: Float = DeskDesign.TileSize.toFloat(),
    gapDp: Float = DeskDesign.TileGap.toFloat(),
): Int =
    floor((availableWidthDp + gapDp) / (tileSizeDp + gapDp)).toInt().coerceAtLeast(1)

internal fun appGridRowCount(
    appCount: Int,
    columns: Int,
    systemTileCount: Int = 2,
): Int {
    val safeColumns = columns.coerceAtLeast(1)
    val totalTiles = appCount.coerceAtLeast(0) + systemTileCount.coerceAtLeast(0)
    return (totalTiles + safeColumns - 1) / safeColumns
}

internal fun appDropTarget(
    sourceTileIndex: Int,
    rowDelta: Int,
    columnDelta: Int,
    columns: Int,
    appCount: Int,
    systemTileCount: Int = 2,
    tileSpans: List<TileSpan> = emptyList(),
    sourceTileSpans: List<TileSpan>? = null,
): Int? {
    if (appCount < 2) return null
    val safeColumns = columns.coerceAtLeast(1)
    if (tileSpans.all { it.columns == 1 && it.rows == 1 } &&
        sourceTileSpans.orEmpty().all { it.columns == 1 && it.rows == 1 }
    ) {
        val sourceRow = sourceTileIndex / safeColumns
        val sourceColumn = sourceTileIndex % safeColumns
        val targetRow = (sourceRow + rowDelta).coerceAtLeast(0)
        val targetColumn = (sourceColumn + columnDelta).coerceIn(0, safeColumns - 1)
        val targetIndex = (targetRow * safeColumns + targetColumn - systemTileCount).coerceIn(0, appCount - 1)
        return targetIndex.takeIf { it != sourceTileIndex - systemTileCount }
    }
    val count = appCount + systemTileCount
    val spans = List(count) { index ->
        tileSpans.getOrNull(index)?.let { span ->
            TileSpan(span.columns.coerceIn(1, safeColumns), span.rows.coerceIn(1, 2))
        } ?: TileSpan()
    }
    val positions = placeTiles(spans, safeColumns)
    val sourceSpans = sourceTileSpans?.takeIf { it.isNotEmpty() } ?: spans
    val source = placeTiles(sourceSpans, safeColumns).getOrNull(sourceTileIndex) ?: return null
    val desiredX = source.column + source.span.columns / 2f + columnDelta
    val desiredY = source.row + source.span.rows / 2f + rowDelta
    val targetIndex = (systemTileCount until count).minByOrNull { index ->
        val target = positions[index]
        val dx = target.column + target.span.columns / 2f - desiredX
        val dy = target.row + target.span.rows / 2f - desiredY
        dx * dx + dy * dy
    }?.minus(systemTileCount) ?: return null
    return targetIndex.takeIf { it != sourceTileIndex - systemTileCount }
}

internal data class TileSpan(val columns: Int = 1, val rows: Int = 1)

internal data class TilePosition(val row: Int, val column: Int, val span: TileSpan)

/** Returns the compact app order produced by dropping a card at a grid cell. */
internal fun reorderGridIdsAtCell(
    appIds: List<String>,
    positions: List<TilePosition>,
    draggedId: String,
    targetRow: Int,
    targetColumn: Int,
    systemTileCount: Int = 2,
): List<String> {
    if (appIds.size < 2 || draggedId !in appIds) return appIds
    val ordered = appIds.withIndex().sortedWith(
        compareBy<IndexedValue<String>> { positions.getOrNull(it.index + systemTileCount)?.row ?: Int.MAX_VALUE }
            .thenBy { positions.getOrNull(it.index + systemTileCount)?.column ?: Int.MAX_VALUE },
    ).map { it.value }
    val moving = ordered.firstOrNull { it == draggedId } ?: return ordered
    val remaining = ordered.filterNot { it == draggedId }
    val insertAt = remaining.indexOfFirst { id ->
        val originalIndex = appIds.indexOf(id)
        val position = positions.getOrNull(originalIndex + systemTileCount) ?: return@indexOfFirst false
        val centerRow = position.row + (position.span.rows - 1) / 2f
        val centerColumn = position.column + (position.span.columns - 1) / 2f
        targetRow < centerRow || (targetRow == centerRow.toInt() && targetColumn <= centerColumn)
    }.let { if (it < 0) remaining.size else it }
    return remaining.toMutableList().apply { add(insertAt, moving) }
}

/** Uses saved cells as ordering hints, then packs every card without leaving holes. */
internal fun placeTilesInGrid(
    spans: List<TileSpan>,
    columns: Int,
    preferredCells: Map<Int, Int> = emptyMap(),
    fixedTileCount: Int = 2,
): List<TilePosition> {
    val safeColumns = columns.coerceAtLeast(1)
    val orderedIndices = buildList {
        addAll(spans.indices.take(fixedTileCount))
        addAll(
            spans.indices.drop(fixedTileCount).sortedWith(
                compareBy<Int> { preferredCells[it] ?: Int.MAX_VALUE }
                    .thenBy { it },
            ),
        )
    }
    val positions = MutableList<TilePosition?>(spans.size) { null }
    val occupied = mutableMapOf<Int, MutableSet<Int>>()

    fun normalizedSpan(index: Int) = TileSpan(
        columns = spans[index].columns.coerceIn(1, safeColumns),
        rows = spans[index].rows.coerceIn(1, 2),
    )

    fun canPlace(row: Int, column: Int, span: TileSpan): Boolean =
        column + span.columns <= safeColumns &&
            (row until row + span.rows).all { currentRow ->
                (column until column + span.columns).none { it in occupied[currentRow].orEmpty() }
            }

    orderedIndices.forEach { tileIndex ->
        val span = normalizedSpan(tileIndex)
        var row = 0
        var column = 0
        while (!canPlace(row, column, span)) {
            column++
            if (column >= safeColumns) {
                row++
                column = 0
            }
        }
        positions[tileIndex] = TilePosition(row, column, span)
        (row until row + span.rows).forEach { currentRow ->
            (column until column + span.columns).forEach { currentColumn ->
                occupied.getOrPut(currentRow) { mutableSetOf() }.add(currentColumn)
            }
        }
    }
    return positions.map { requireNotNull(it) }
}

internal fun placeTiles(spans: List<TileSpan>, columns: Int): List<TilePosition> {
    val safeColumns = columns.coerceAtLeast(1)
    var cursorRow = 0
    var cursorColumn = 0
    var lineHeight = 1
    return spans.map { requestedSpan ->
        val span = TileSpan(
            columns = requestedSpan.columns.coerceIn(1, safeColumns),
            rows = requestedSpan.rows.coerceIn(1, 2),
        )
        if (cursorColumn + span.columns > safeColumns) {
            cursorRow += lineHeight
            cursorColumn = 0
            lineHeight = 1
        }
        val position = TilePosition(cursorRow, cursorColumn, span)
        cursorColumn += span.columns
        lineHeight = maxOf(lineHeight, span.rows)
        position
    }
}
