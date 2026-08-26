package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.osShortLabel

@Composable
fun DeskHeader(
    uiState: DeskUiState,
    onRefresh: () -> Unit,
    onManageHosts: () -> Unit,
    onPairHost: () -> Unit,
    onSelectHost: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val host = uiState.selectedHost
    val (dot, status) = when {
        uiState.isLoadingApps -> DeskColors.Warning to "Sincronizando…"
        !uiState.hasHosts -> DeskColors.Idle to "Aguardando conexão"
        uiState.listHint != null && uiState.buttons.isEmpty() ->
            DeskColors.Warning to "Sem sessão · pareie de novo"
        else -> DeskColors.Live to "Conectado · atualiza automaticamente"
    }
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Control Desk", color = DeskColors.TextPrimary, fontSize = 22.sp)
                Text("Painel remoto", color = DeskColors.TextMuted, fontSize = 13.sp)
            }
            TextButton(onClick = onRefresh, enabled = !uiState.isLoadingApps) {
                Text("↻", color = if (uiState.isLoadingApps) DeskColors.TextMuted else DeskColors.TextPrimary, fontSize = 20.sp)
            }
            TextButton(onClick = onManageHosts) { Text("Hosts", color = DeskColors.TextPrimary) }
            TextButton(onClick = onPairHost) { Text("+", color = DeskColors.Accent, fontSize = 22.sp) }
        }
        Spacer(Modifier.height(10.dp))
        if (host != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(DeskColors.Surface, RoundedCornerShape(16.dp))
                    .border(1.dp, DeskColors.Stroke, RoundedCornerShape(16.dp))
                    .padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(host.name, color = DeskColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.size(8.dp).background(dot, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(status, color = DeskColors.TextMuted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text("${host.host}:${host.port}", color = DeskColors.TextMuted, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
            }
        }
        if (uiState.hosts.size > 1) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                uiState.hosts.forEachIndexed { index, item ->
                    val selected = index == uiState.selectedHostIndex
                    Text(
                        "${item.name} · ${item.osType.osShortLabel()}",
                        color = if (selected) DeskColors.TextPrimary else DeskColors.TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .background(
                                if (selected) DeskColors.AccentSoft else DeskColors.SurfaceRaised,
                                RoundedCornerShape(20.dp),
                            )
                            .clickable { onSelectHost(index) }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }
        }
    }
}
