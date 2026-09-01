package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign
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
    tileSize: Dp,
    modifier: Modifier = Modifier,
) {
    val focused = buttons.count { it.isActive }
    val hidden = buttons.count { !it.isVisible && !it.isDockPinned }
    val docked = buttons.count { it.isDockPinned && !it.isVisible }
    val monitors = buttons.filter { it.monitorIndex > 0 }.groupBy { it.monitorIndex }
        .toSortedMap()
        .entries
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
                    append("${buttons.size} apps · $focused em uso")
                    if (monitors.isNotBlank()) append(" · $monitors")
                    if (docked > 0) append(" · $docked na Dock")
                    if (hidden > 0) append(" · $hidden ocultos")
                }
            },
            color = DeskColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = if (compact) 0.dp else 4.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.FixedSize(tileSize),
            horizontalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
            verticalArrangement = Arrangement.spacedBy(DeskDesign.TileGap.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "sys-volume", contentType = "system") {
                VolumeAppTile(
                    volumeLevel = volumeLevel,
                    muted = volumeMuted,
                    onLevelChange = onVolumeChange,
                    onAdjustFinished = onVolumeChangeFinished,
                    onMuteClick = onMuteClick,
                )
            }
            item(key = "sys-mic", contentType = "system") {
                MicAppTile(
                    micLevel = micLevel,
                    muted = micMuted,
                    onLevelChange = onMicChange,
                    onAdjustFinished = onMicChangeFinished,
                    onMuteClick = onMicClick,
                )
            }
            items(buttons, key = { it.id }) { button ->
                AppLauncherTile(
                    button = button,
                    onClick = { onAppClick(button) },
                    onLongClick = { onAppClose(button) },
                    compact = compact,
                )
            }
        }
    }
}
