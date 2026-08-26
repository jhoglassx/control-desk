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
    volumeLevel: Int,
    volumeMuted: Boolean,
    micMuted: Boolean,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onMuteClick: () -> Unit,
    onMicClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val focused = buttons.count { it.isActive }
    val hidden = buttons.count { !it.isVisible }
    val monitors = buttons.groupBy { it.monitorIndex.coerceAtLeast(1) }
        .toSortedMap()
        .entries
        .joinToString(" · ") { (monitor, apps) -> "M$monitor: ${apps.size}" }
    val minSize = if (compact) DeskDesign.TileMinSizeLandscape.dp else DeskDesign.TileMinSize.dp
    Column(modifier.fillMaxSize()) {
        if (!compact) {
            Text("Apps no Mac", color = DeskColors.TextPrimary, fontSize = 16.sp)
        }
        Text(
            if (buttons.isEmpty()) {
                listHint ?: "Nenhuma janela detectada no PC"
            } else {
                "${buttons.size} apps · $focused em uso · $monitors · $hidden ocultos"
            },
            color = DeskColors.TextMuted,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = if (compact) 0.dp else 4.dp, bottom = 8.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minSize),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "sys-volume", contentType = "system") {
                VolumeAppTile(
                    volumeLevel = volumeLevel,
                    muted = volumeMuted,
                    onMuteClick = onMuteClick,
                    onVolumeDown = onVolumeDown,
                    onVolumeUp = onVolumeUp,
                )
            }
            item(key = "sys-mic", contentType = "system") {
                MicAppTile(muted = micMuted, onClick = onMicClick)
            }
            items(buttons, key = { it.id }) { button ->
                AppLauncherTile(button = button, onClick = { onAppClick(button) }, compact = compact)
            }
        }
    }
}
