package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.PairedHost

@Composable
fun DeskHeader(
    uiState: DeskUiState,
    host: PairedHost?,
    isCurrentPage: Boolean,
    pageIndex: Int,
    onRefresh: () -> Unit,
    onManageHosts: () -> Unit,
    onPairHost: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (dot, status) = when {
        !isCurrentPage -> {
            val cached = host?.id?.let { uiState.cachedButtons[it] }.orEmpty()
            if (cached.isNotEmpty()) DeskColors.Live to "Online" else DeskColors.Idle to "—"
        }
        uiState.isLoadingApps -> DeskColors.Warning to "Sincronizando"
        !uiState.hasHosts -> DeskColors.Idle to "Offline"
        uiState.listHint != null && uiState.buttons.isEmpty() ->
            DeskColors.Warning to "Re-parear"
        else -> DeskColors.Live to "Online"
    }
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(DeskColors.Surface, RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        host?.name ?: "Control Desk",
                        color = DeskColors.TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(status, color = DeskColors.TextMuted, fontSize = 11.sp, maxLines = 1)
                }
                if (host != null) {
                    Text(
                        "${host.host}:${host.port}",
                        color = DeskColors.TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onRefresh, enabled = !uiState.isLoadingApps && isCurrentPage) {
                Text("↻", color = if (uiState.isLoadingApps) DeskColors.TextMuted else DeskColors.TextPrimary, fontSize = 18.sp)
            }
            TextButton(onClick = onManageHosts) { Text("Hosts", color = DeskColors.TextPrimary, fontSize = 13.sp) }
            TextButton(onClick = onPairHost) { Text("+", color = DeskColors.Accent, fontSize = 20.sp) }
        }
        if (uiState.hosts.size > 1) {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                uiState.hosts.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (index == pageIndex) 8.dp else 6.dp)
                            .background(
                                if (index == pageIndex) DeskColors.Accent else DeskColors.TextMuted.copy(alpha = 0.45f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
