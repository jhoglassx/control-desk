package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign
import com.controldesk.app.ui.desk.appDropTarget
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
    onPreviewReorderApp: (String, Int) -> Unit,
    onFinishAppReorderDrag: () -> Unit,
    onReorderApps: (List<String>) -> Unit,
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
                "Segure e arraste qualquer parte de um card. O app selecionado para de vibrar.",
                color = DeskColors.Accent,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        val columnCount = columns.coerceAtLeast(1)
        val density = LocalDensity.current
        val gapPx = with(density) { DeskDesign.TileGap.dp.toPx() }
        LazyVerticalGrid(
            columns = GridCells.Fixed(columnCount),
            verticalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
            horizontalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = buttons.size + 2,
                key = { tileIndex ->
                    when (tileIndex) {
                        0 -> "system-volume"
                        1 -> "system-microphone"
                        else -> buttons[tileIndex - 2].id
                    }
                },
            ) { tileIndex ->
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
                        AppLauncherTile(
                            button = button,
                            onClick = { onAppClick(button) },
                            onLongClick = { onAppClose(button) },
                            onCommitReorder = if (isReorganizingApps) {
                                { onReorderApps(buttons.map(GridButton::id)) }
                            } else null,
                            onPreviewReorder = { draggedId, rowDelta, columnDelta ->
                                val startIndex = selectedAppDragStartIndex ?: return@AppLauncherTile
                                appDropTarget(
                                    sourceTileIndex = startIndex + 2,
                                    rowDelta = rowDelta,
                                    columnDelta = columnDelta,
                                    columns = columnCount,
                                    appCount = buttons.size,
                                )?.let { target -> onPreviewReorderApp(draggedId, target) }
                            },
                            isSelectedForReorder = selectedAppIdForReorder == button.id,
                            onSelectForReorder = { appId, cellWidth, cellHeight ->
                                onSelectAppForReorder(appId, cellWidth, cellHeight)
                            },
                            selectedDragOffsetX = if (selectedAppIdForReorder == button.id) {
                                selectedAppDragOffsetX - selectedSlotDeltaX(
                                    startIndex = selectedAppDragStartIndex,
                                    currentIndex = buttons.indexOfFirst { it.id == button.id },
                                    columns = columnCount,
                                    cellSizePx = selectedAppDragCellWidth,
                                    gapPx = gapPx,
                                )
                            } else 0f,
                            selectedDragOffsetY = if (selectedAppIdForReorder == button.id) {
                                selectedAppDragOffsetY - selectedSlotDeltaY(
                                    startIndex = selectedAppDragStartIndex,
                                    currentIndex = buttons.indexOfFirst { it.id == button.id },
                                    columns = columnCount,
                                    cellSizePx = selectedAppDragCellHeight,
                                    gapPx = gapPx,
                                )
                            } else 0f,
                            onDragBy = onMoveSelectedAppBy,
                            onFinishReorderDrag = onFinishAppReorderDrag,
                            compact = compact,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

private fun selectedSlotDeltaX(
    startIndex: Int?,
    currentIndex: Int,
    columns: Int,
    cellSizePx: Float,
    gapPx: Float,
): Float {
    if (startIndex == null || currentIndex < 0) return 0f
    val startColumn = (startIndex + 2) % columns
    val currentColumn = (currentIndex + 2) % columns
    return (currentColumn - startColumn) * (cellSizePx + gapPx)
}

private fun selectedSlotDeltaY(
    startIndex: Int?,
    currentIndex: Int,
    columns: Int,
    cellSizePx: Float,
    gapPx: Float,
): Float {
    if (startIndex == null || currentIndex < 0) return 0f
    val startRow = (startIndex + 2) / columns
    val currentRow = (currentIndex + 2) / columns
    return (currentRow - startRow) * (cellSizePx + gapPx)
}
