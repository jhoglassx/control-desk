package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign
import com.controldesk.app.ui.desk.appGridRowCount
import com.controldesk.domain.model.GridButton

@Composable
fun WorkspacePanel(
    buttons: List<GridButton>,
    listHint: String?,
    onAppClick: (GridButton) -> Unit,
    onAppClose: (GridButton) -> Unit,
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
    val docked = buttons.count { it.isDockPinned && !it.isVisible }
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
        val columnCount = columns.coerceAtLeast(1)
        val rowCount = appGridRowCount(buttons.size, columnCount)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(rowCount, key = { "app-grid-row-$it" }) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
                ) {
                    repeat(columnCount) { columnIndex ->
                        val tileIndex = rowIndex * columnCount + columnIndex
                        when {
                            tileIndex == 0 -> VolumeAppTile(
                                volumeLevel = volumeLevel,
                                muted = volumeMuted,
                                onLevelChange = onVolumeChange,
                                onAdjustFinished = onVolumeChangeFinished,
                                onMuteClick = onMuteClick,
                                modifier = Modifier.weight(1f),
                            )
                            tileIndex == 1 -> MicAppTile(
                                micLevel = micLevel,
                                muted = micMuted,
                                onLevelChange = onMicChange,
                                onAdjustFinished = onMicChangeFinished,
                                onMuteClick = onMicClick,
                                modifier = Modifier.weight(1f),
                            )
                            tileIndex - 2 < buttons.size -> {
                                val button = buttons[tileIndex - 2]
                                AppLauncherTile(
                                    button = button,
                                    onClick = { onAppClick(button) },
                                    onLongClick = { onAppClose(button) },
                                    compact = compact,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            else -> Spacer(Modifier.weight(1f).aspectRatio(1f))
                        }
                    }
                }
            }
        }
    }
}
