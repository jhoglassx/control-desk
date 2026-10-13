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
