package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.panel.LocalPanelMode
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.PairedHost

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DeskHeader(
    uiState: DeskUiState,
    host: PairedHost?,
    isCurrentPage: Boolean,
    pageIndex: Int,
    onManageHosts: () -> Unit,
    onPairHost: () -> Unit,
    onAdminAccessRequest: () -> Unit = onManageHosts,
    modifier: Modifier = Modifier,
) {
    val panelMode = LocalPanelMode.current.enabled
    val (dot, status) = when {
        !isCurrentPage -> {
            val cached = host?.id?.let { uiState.cachedButtons[it] }.orEmpty()
            if (cached.isNotEmpty()) DeskColors.Live to "Online" else DeskColors.Idle to "—"
        }
        uiState.isLoadingApps -> DeskColors.Warning to "Sincronizando"
        !uiState.hasHosts -> DeskColors.Idle to "Offline"
        uiState.listHint == ControlDeskError.ACCESSIBILITY_OFF ->
            DeskColors.Warning to "Acessibilidade"
        uiState.listHint != null && uiState.buttons.isEmpty() ->
            DeskColors.Warning to "Re-parear"
        else -> DeskColors.Live to "Online"
    }
    Column(modifier.wrapContentWidth(Alignment.End)) {
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .combinedClickable(
                        onClick = {},
                        onLongClick = if (panelMode) onAdminAccessRequest else null,
                    ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        host?.name ?: "Control Desk",
                        color = DeskColors.TextPrimary.copy(alpha = 0.72f),
                        fontSize = 13.sp,
                    )
                }
            }
            if (!panelMode) {
                TextButton(onClick = onManageHosts) { Text("Hosts", color = DeskColors.TextPrimary, fontSize = 12.sp) }
                TextButton(onClick = onPairHost) { Text("+", color = DeskColors.Accent, fontSize = 20.sp) }
            }
        }
        Row(
            modifier = Modifier.align(Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (host != null) {
                Text(
                    "${host.host}:${host.port}",
                    color = DeskColors.TextMuted.copy(alpha = 0.72f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                )
            }
            if (status != "Online") {
                if (host != null) Spacer(Modifier.width(8.dp))
                Text(
                    text = status,
                    color = dot.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                )
            }
            if (uiState.hosts.size > 1) {
                Spacer(Modifier.width(8.dp))
                uiState.hosts.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp, vertical = 4.dp)
                            .size(if (index == pageIndex) 7.dp else 5.dp)
                            .background(
                                if (index == pageIndex) DeskColors.Accent else DeskColors.TextMuted.copy(alpha = 0.55f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}
