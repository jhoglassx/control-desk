package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.animateIntOffsetAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign
import com.controldesk.app.ui.desk.placeTilesInGrid
import com.controldesk.app.ui.desk.reorderGridIdsAtCell
import com.controldesk.app.ui.desk.TileSpan
import com.controldesk.app.ui.desk.TilePosition
import com.controldesk.domain.model.GridButton

@Composable
fun WorkspacePanel(
    buttons: List<GridButton>,
    listHint: String?,
    onAppClick: (GridButton) -> Unit,
    onAppClose: (GridButton) -> Unit,
    isReorganizingApps: Boolean,
    selectedAppIdForReorder: String?,
    selectedAppDragStartIndex: Int?,
    selectedAppDragOffsetX: Float,
    selectedAppDragOffsetY: Float,
    selectedAppDragCellWidth: Float,
    selectedAppDragCellHeight: Float,
    onSelectAppForReorder: (String, Float, Float) -> Unit,
    onMoveSelectedAppBy: (Float, Float) -> Unit,
    onPreviewAppGridPositions: (Map<String, Int>) -> Unit,
    appGridPositions: Map<String, Int>,
    onFinishAppReorderDrag: () -> Unit,
    onResizeAppTile: (String) -> Unit,
    volumeLevel: Int,
    volumeMuted: Boolean,
    micLevel: Int,
    micMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onMuteClick: () -> Unit,
    onMicChange: (Int) -> Unit,
    onMicChangeFinished: () -> Unit,
    onMicClick: () -> Unit,
    compact: Boolean,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val focused = buttons.count { it.isActive }
    val open = buttons.count { it.isVisible && !it.isActive }
    val hidden = buttons.count { !it.isVisible && !it.isDockPinned }
    val docked = buttons.count { it.isDockPinned && !it.isVisible && !it.isFavoriteShortcut }
    val monitors = buttons.filter { it.monitorIndex > 0 }.groupBy { it.monitorIndex }
        .entries
        .sortedBy { it.key }
        .joinToString(" · ") { entry -> "M${entry.key}: ${entry.value.size}" }
    Column(modifier.fillMaxSize()) {
        if (!compact) {
            Text("Apps no Mac", color = DeskColors.TextPrimary, fontSize = 16.sp)
        }
        Text(
            when {
                buttons.isEmpty() -> listHint ?: "Nenhuma janela detectada no PC"
                listHint != null -> listHint
                else -> buildString {
                    append("${buttons.size} apps")
                    if (open > 0) append(" · $open abertos")
                    append(" · $focused em uso")
                    if (monitors.isNotBlank()) append(" · $monitors")
                    if (docked > 0) append(" · $docked na Dock")
                    if (hidden > 0) append(" · $hidden ocultos")
                }
            },
            color = DeskColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = if (compact) 0.dp else 4.dp, bottom = 8.dp),
        )
        if (isReorganizingApps) {
            Text(
                "Segure e arraste para reorganizar; toque no tamanho para alternar entre 1×1, 2×1, 2×2 e 1×2.",
                color = DeskColors.Accent,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val columnCount = columns.coerceAtLeast(1)
        val density = LocalDensity.current
        val gapPx = with(density) { DeskDesign.TileGap.dp.toPx() }
        val tileSpans = listOf(TileSpan(), TileSpan()) + buttons.map { button ->
            TileSpan(button.tileSpanColumns(), button.tileSpanRows())
        }
        val savedCells = buttons.mapIndexedNotNull { index, button ->
            appGridPositions[button.id]?.let { (index + 2) to it }
        }.toMap()
        val tilePositions = placeTilesInGrid(tileSpans, columnCount, savedCells)
        var dragStartTilePositions by remember { mutableStateOf<List<TilePosition>?>(null) }
        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
        ) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cellPx = with(density) {
                    val availableWidthPx = maxWidth.roundToPx()
                    val gapPx = DeskDesign.TileGap.dp.roundToPx()
                    (availableWidthPx - gapPx * (columnCount - 1)) / columnCount
                }
                val gapCellPx = with(density) { DeskDesign.TileGap.dp.roundToPx() }
                Layout(
                    content = {
                        tilePositions.forEachIndexed { tileIndex, position ->
                            val itemKey = when (tileIndex) {
                                0 -> "system-volume"
                                1 -> "system-microphone"
                                else -> buttons.getOrNull(tileIndex - 2)?.id ?: "missing-tile-$tileIndex"
                            }
                            val offset = IntOffset(
                                position.column * (cellPx + gapCellPx),
                                position.row * (cellPx + gapCellPx),
                            )
                            key(itemKey) {
                                GridPositionedItem(
                                    offset = offset,
                                    isDragged = tileIndex >= 2 && selectedAppIdForReorder == buttons.getOrNull(tileIndex - 2)?.id,
                                ) {
                                    RenderTile(
                                        tileIndex = tileIndex,
                                        buttons = buttons,
                                        columnCount = columnCount,
                                        compact = compact,
                                        selectedAppIdForReorder = selectedAppIdForReorder,
                                        selectedAppDragStartIndex = selectedAppDragStartIndex,
                                        selectedAppDragOffsetX = selectedAppDragOffsetX,
                                        selectedAppDragOffsetY = selectedAppDragOffsetY,
                                        selectedAppDragCellWidth = selectedAppDragCellWidth,
                                        selectedAppDragCellHeight = selectedAppDragCellHeight,
                                        dragStartTilePositions = dragStartTilePositions,
                                        onDragStarted = { dragStartTilePositions = tilePositions },
                                        onDragFinished = {
                                            dragStartTilePositions = null
                                            onFinishAppReorderDrag()
                                        },
                                        tilePositions = tilePositions,
                                        gapPx = gapPx,
                                        isReorganizingApps = isReorganizingApps,
                                        onAppClick = onAppClick,
                                        onAppClose = onAppClose,
                                        onSelectAppForReorder = onSelectAppForReorder,
                                        onMoveSelectedAppBy = onMoveSelectedAppBy,
                                        onPreviewAppGridPositions = onPreviewAppGridPositions,
                                        onResizeAppTile = onResizeAppTile,
                                        volumeLevel = volumeLevel,
                                        volumeMuted = volumeMuted,
                                        micLevel = micLevel,
                                        micMuted = micMuted,
                                        onVolumeChange = onVolumeChange,
                                        onVolumeChangeFinished = onVolumeChangeFinished,
                                        onMuteClick = onMuteClick,
                                        onMicChange = onMicChange,
                                        onMicChangeFinished = onMicChangeFinished,
                                        onMicClick = onMicClick,
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { measurables, constraints ->
                    val gap = gapCellPx
                    val cellWidth = (constraints.maxWidth - gap * (columnCount - 1)) / columnCount
                    val placeables = measurables.mapIndexed { index, measurable ->
                        val span = tilePositions[index].span
                        val width = cellWidth * span.columns + gap * (span.columns - 1)
                        val height = cellWidth * span.rows + gap * (span.rows - 1)
                        measurable.measure(Constraints.fixed(width, height))
                    }
                    val rowCount = tilePositions.maxOfOrNull { it.row + it.span.rows } ?: 0
                    val layoutHeight = rowCount * cellWidth + (rowCount - 1).coerceAtLeast(0) * gap
                    layout(constraints.maxWidth, layoutHeight) {
                        placeables.forEach { it.placeRelative(0, 0) }
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun GridPositionedItem(
    offset: IntOffset,
    isDragged: Boolean,
    content: @Composable () -> Unit,
) {
    val animatedOffset by animateIntOffsetAsState(
        targetValue = offset,
        animationSpec = tween(180),
        label = "grid-item-placement",
    )
    val displayedOffset = if (isDragged) offset else animatedOffset
    Box(Modifier.offset { displayedOffset }.zIndex(if (isDragged) 100f else 0f)) { content() }
}

@Composable
private fun RenderTile(
    tileIndex: Int,
    buttons: List<GridButton>,
    columnCount: Int,
    compact: Boolean,
    selectedAppIdForReorder: String?,
    selectedAppDragStartIndex: Int?,
    selectedAppDragOffsetX: Float,
    selectedAppDragOffsetY: Float,
    selectedAppDragCellWidth: Float,
    selectedAppDragCellHeight: Float,
    dragStartTilePositions: List<TilePosition>?,
    onDragStarted: () -> Unit,
    onDragFinished: () -> Unit,
    tilePositions: List<TilePosition>,
    gapPx: Float,
    isReorganizingApps: Boolean,
    onAppClick: (GridButton) -> Unit,
    onAppClose: (GridButton) -> Unit,
    onSelectAppForReorder: (String, Float, Float) -> Unit,
    onMoveSelectedAppBy: (Float, Float) -> Unit,
    onPreviewAppGridPositions: (Map<String, Int>) -> Unit,
    onResizeAppTile: (String) -> Unit,
    volumeLevel: Int,
    volumeMuted: Boolean,
    micLevel: Int,
    micMuted: Boolean,
    onVolumeChange: (Int) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onMuteClick: () -> Unit,
    onMicChange: (Int) -> Unit,
    onMicChangeFinished: () -> Unit,
    onMicClick: () -> Unit,
) {
    when (tileIndex) {
                        0 -> VolumeAppTile(
                            volumeLevel = volumeLevel,
                            muted = volumeMuted,
                            onLevelChange = onVolumeChange,
                            onAdjustFinished = onVolumeChangeFinished,
                            onMuteClick = onMuteClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        1 -> MicAppTile(
                            micLevel = micLevel,
                            muted = micMuted,
                            onLevelChange = onMicChange,
                            onAdjustFinished = onMicChangeFinished,
                            onMuteClick = onMicClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        else -> {
                            val button = buttons[tileIndex - 2]
                            val buttonIndex = tileIndex - 2
                            val spanColumns = button.tileSpanColumns().coerceAtMost(columnCount)
                            val startPositions = dragStartTilePositions ?: tilePositions
                            val currentPosition = tilePositions[buttonIndex + 2]
                            AppLauncherTile(
                                button = button,
                                onClick = { onAppClick(button) },
                                onLongClick = { onAppClose(button) },
                                onPreviewReorder = { draggedId, rowDelta, columnDelta ->
                                    val startIndex = selectedAppDragStartIndex ?: return@AppLauncherTile
                                    val origin = startPositions.getOrNull(startIndex + 2) ?: return@AppLauncherTile
                                    val targetRow = (origin.row + rowDelta).coerceAtLeast(0)
                                    val targetColumn = (origin.column + columnDelta)
                                        .coerceIn(0, columnCount - button.tileSpanColumns().coerceAtMost(columnCount))
                                    if (targetRow == origin.row && targetColumn == origin.column) return@AppLauncherTile
                                    val movingSpan = button.tileSpanColumns().coerceAtMost(columnCount)
                                    if (startPositions.take(2).any { systemTile ->
                                            systemTile.row == targetRow &&
                                                (targetColumn until targetColumn + movingSpan).contains(systemTile.column)
                                        }
                                    ) {
                                        return@AppLauncherTile
                                    }
                                    val orderedIds = reorderGridIdsAtCell(
                                        appIds = buttons.map(GridButton::id),
                                        positions = startPositions,
                                        draggedId = draggedId,
                                        targetRow = targetRow,
                                        targetColumn = targetColumn,
                                    )
                                    onPreviewAppGridPositions(
                                        orderedIds.mapIndexed { index, id -> id to index }.toMap(),
                                    )
                                },
                                isSelectedForReorder = selectedAppIdForReorder == button.id,
                                onSelectForReorder = { appId, cellWidth, cellHeight ->
                                    onDragStarted()
                                    onSelectAppForReorder(appId, cellWidth, cellHeight)
                                },
                                selectedDragOffsetX = if (selectedAppIdForReorder == button.id) {
                                    val start = dragStartTilePositions?.getOrNull(selectedAppDragStartIndex?.plus(2) ?: -1)
                                    val columnDelta = start?.let { currentPosition.column - it.column } ?: 0
                                    selectedAppDragOffsetX - columnDelta * (selectedAppDragCellWidth + gapPx)
                                } else 0f,
                                selectedDragOffsetY = if (selectedAppIdForReorder == button.id) {
                                    val start = dragStartTilePositions?.getOrNull(selectedAppDragStartIndex?.plus(2) ?: -1)
                                    val rowDelta = start?.let { currentPosition.row - it.row } ?: 0
                                    selectedAppDragOffsetY - rowDelta * (selectedAppDragCellHeight + gapPx)
                                } else 0f,
                                onDragBy = onMoveSelectedAppBy,
                                onFinishReorderDrag = onDragFinished,
                                compact = compact,
                                spanColumns = spanColumns,
                                spanRows = button.tileSpanRows(),
                                onResize = if (isReorganizingApps) ({ onResizeAppTile(button.id) }) else null,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
    }
}

private fun GridButton.tileSpanColumns(): Int =
    payload["spanColumns"]?.toIntOrNull()?.coerceIn(1, 2) ?: 1

private fun GridButton.tileSpanRows(): Int =
    payload["spanRows"]?.toIntOrNull()?.coerceIn(1, 2) ?: 1
