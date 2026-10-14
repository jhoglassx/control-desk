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
): Int? {
    if (appCount < 2) return null
    val safeColumns = columns.coerceAtLeast(1)
    val sourceRow = sourceTileIndex / safeColumns
    val sourceColumn = sourceTileIndex % safeColumns
    val targetRow = (sourceRow + rowDelta).coerceAtLeast(0)
    val targetColumn = (sourceColumn + columnDelta).coerceIn(0, safeColumns - 1)
    val targetIndex = (targetRow * safeColumns + targetColumn - systemTileCount).coerceIn(0, appCount - 1)
    return targetIndex.takeIf { it != sourceTileIndex - systemTileCount }
}
