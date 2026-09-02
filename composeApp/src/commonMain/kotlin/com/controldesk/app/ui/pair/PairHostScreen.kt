package com.controldesk.app.ui.pair

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.PairHostUiState
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.osShortLabel

@Composable
fun PairHostScreen(
    uiState: PairHostUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPinChange: (String) -> Unit,
    onOsTypeChange: (OsType) -> Unit,
    onPair: () -> Unit,
    onRemoveHost: (String) -> Unit,
    showLauncherHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(DeskColors.Background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onBack) { Text("← Voltar", color = DeskColors.TextPrimary) }
        Text("Adicionar PC", color = DeskColors.TextPrimary, fontSize = 24.sp)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(uiState.name, onNameChange, label = { Text("Nome") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(uiState.host, onHostChange, label = { Text("IP / hostname") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            uiState.port,
            onPortChange,
            label = { Text("Porta") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            uiState.pin,
            onPinChange,
            label = { Text("PIN") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OsType.entries.forEach { os ->
                FilterChip(
                    selected = uiState.osType == os,
                    onClick = { onOsTypeChange(os) },
                    label = { Text(os.osShortLabel()) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .background(DeskColors.Surface, RoundedCornerShape(14.dp))
                .padding(12.dp),
        ) {
            Text("Como conectar", color = DeskColors.TextPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text("Emulador Android no Mac → 10.0.2.2", color = DeskColors.TextMuted, fontSize = 13.sp)
            Text("Celular físico → IP LAN do Mac (janela do agente)", color = DeskColors.TextMuted, fontSize = 13.sp)
            Text("Não use 127.0.0.1 no emulador/celular", color = DeskColors.Warning, fontSize = 13.sp)
        }
        if (showLauncherHint) {
            Spacer(Modifier.height(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(DeskColors.Surface, RoundedCornerShape(14.dp))
                    .padding(12.dp),
            ) {
                Text("Modo painel dedicado", color = DeskColors.TextPrimary, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Para abrir direto ao ligar o aparelho, defina Control Desk como app inicial em Configurações → Apps padrão → App inicial.",
                    color = DeskColors.TextMuted,
                    fontSize = 13.sp,
                )
                Text(
                    "PIN administrativo do painel: 8741",
                    color = DeskColors.TextMuted,
                    fontSize = 13.sp,
                )
            }
        }
        if (uiState.errorMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(uiState.errorMessage, color = DeskColors.Danger)
        }
        if (uiState.successMessage != null) {
            Spacer(Modifier.height(10.dp))
            Text(uiState.successMessage, color = DeskColors.Mint)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPair,
            enabled = !uiState.isPairing,
            colors = ButtonDefaults.buttonColors(containerColor = DeskColors.Accent),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (uiState.isPairing) "Pareando…" else "Parear")
        }
        Spacer(Modifier.height(24.dp))
        Text("PCs pareados", color = DeskColors.TextPrimary, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        uiState.hosts.forEach { host ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .background(DeskColors.Surface, RoundedCornerShape(12.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${host.name} · ${host.osType.osShortLabel()}", color = DeskColors.TextPrimary)
                    Text("${host.host}:${host.port}", color = DeskColors.TextMuted, fontSize = 12.sp)
                }
                TextButton(onClick = { onRemoveHost(host.id) }) {
                    Text("🗑", fontSize = 18.sp)
                }
            }
        }
    }
}
