package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
    modifier: Modifier = Modifier,
) {
    val focused = buttons.count { it.isActive }
    val hidden = buttons.count { !it.isVisible }
    val monitors = buttons.groupBy { it.monitorIndex.coerceAtLeast(1) }
        .toSortedMap()
        .entries
        .joinToString(" · ") { (monitor, apps) -> "M$monitor: ${apps.size}" }
    Column(modifier.fillMaxSize()) {
        Text("Apps no Mac", color = DeskColors.TextPrimary, fontSize = 16.sp)
        if (buttons.isNotEmpty()) {
            Text(
                "${buttons.size} apps · $focused em uso · $monitors · $hidden ocultos",
                color = DeskColors.TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 10.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = DeskDesign.TileMinSize.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(buttons, key = { it.id }) { button ->
                    AppLauncherTile(button = button, onClick = { onAppClick(button) })
                }
            }
        } else {
            EmptyWorkspaceCard(listHint)
        }
    }
}

@Composable
fun EmptyWorkspaceCard(listHint: String?) {
    Text(
        listHint ?: "Nenhuma janela detectada no PC",
        color = DeskColors.TextMuted,
        fontSize = 14.sp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .background(DeskColors.Surface, RoundedCornerShape(16.dp))
            .padding(18.dp),
    )
}
