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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.PairHostUiState
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.osShortLabel
import com.controldesk.domain.repository.DiscoveredAgent

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
    onDiscover: () -> Unit,
    onSelectDiscovered: (DiscoveredAgent) -> Unit,
    onRemoveHost: (String) -> Unit,
    showLauncherHint: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val showAdvanced = remember(uiState.discoveredAgents, uiState.host) {
        uiState.discoveredAgents.isEmpty() || uiState.host != "10.0.2.2"
    }
    Column(
        modifier.fillMaxSize()
            .background(DeskColors.ScreenGradient)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(onClick = onBack, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
            Text("←  Voltar", color = DeskColors.TextMuted)
        }
        Text("Conecte seu Mac", color = DeskColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Instale o agente no computador. Depois, encontre-o na rede e informe o PIN que aparece no Mac.",
            color = DeskColors.TextMuted,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(Modifier.height(20.dp))

        PairStepCard(number = "1", title = "Agente instalado no Mac?") {
            Text(
                "Abra Control Desk Agent. Ele fica ativo em segundo plano e mostra um PIN temporário para conectar este celular.",
                color = DeskColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(
                onClick = onDiscover,
                enabled = !uiState.isDiscovering,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 0.dp, top = 8.dp, end = 8.dp, bottom = 0.dp),
            ) {
                if (uiState.isDiscovering) {
                    CircularProgressIndicator(Modifier.padding(end = 10.dp), color = DeskColors.Accent, strokeWidth = 2.dp)
                    Text("Procurando na rede…", color = DeskColors.Accent)
                } else {
                    Text("⌕  Buscar Mac na mesma rede", color = DeskColors.Accent, fontWeight = FontWeight.Medium)
                }
            }
            if (uiState.discoveredAgents.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                uiState.discoveredAgents.forEach { agent ->
                    Surface(
                        onClick = { onSelectDiscovered(agent) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        color = DeskColors.SurfaceRaised,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                            Text(agent.name, color = DeskColors.TextPrimary, fontWeight = FontWeight.Medium)
                            Text("Encontrado na rede · ${agent.host}", color = DeskColors.TextMuted, fontSize = 12.sp)
                        }
                    }
                }
            } else if (uiState.isDiscovering) {
                Text("Mantenha o celular e o Mac na mesma rede Wi‑Fi.", color = DeskColors.TextMuted, fontSize = 12.sp)
            } else {
                Text("Celular e Mac precisam estar na mesma rede Wi‑Fi.", color = DeskColors.TextMuted, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(12.dp))
        PairStepCard(number = "2", title = "Digite o PIN do Mac") {
            Text("Encontre o PIN de 6 dígitos na janela do agente e digite-o aqui.", color = DeskColors.TextMuted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = uiState.pin,
                onValueChange = { value -> onPinChange(value.filter(Char::isDigit).take(6)) },
                label = { Text("PIN de 6 dígitos") },
                placeholder = { Text("••••••") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = uiState.errorMessage != null,
            )
        }

        if (showAdvanced) {
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDiscover) {
                Text("Não encontrou? Buscar novamente ou configurar manualmente", color = DeskColors.TextMuted, fontSize = 12.sp)
            }
            OutlinedTextField(
                value = uiState.name,
                onValueChange = onNameChange,
                label = { Text("Nome do computador") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.host,
                onValueChange = onHostChange,
                label = { Text("Endereço do Mac") },
                supportingText = { Text("Ex.: IP exibido pelo agente. Porta padrão: 8741.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uiState.port,
                onValueChange = onPortChange,
                label = { Text("Porta") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            Spacer(Modifier.height(10.dp))
            Text("Sistema operacional", color = DeskColors.TextMuted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OsType.entries.forEach { os ->
                    FilterChip(selected = uiState.osType == os, onClick = { onOsTypeChange(os) }, label = { Text(os.osShortLabel()) })
                }
            }
        }

        if (showLauncherHint) {
            Spacer(Modifier.height(12.dp))
            PairStepCard(number = "i", title = "Modo painel") {
                Text("Para abrir o Control Desk ao ligar o aparelho, defina-o como app inicial nas configurações do Android.", color = DeskColors.TextMuted, fontSize = 13.sp)
            }
        }

        uiState.errorMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = DeskColors.Danger, fontSize = 13.sp)
        }
        uiState.successMessage?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = DeskColors.Mint, fontSize = 13.sp)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onPair,
            enabled = !uiState.isPairing && uiState.pin.length == 6 && uiState.host.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = DeskColors.Accent),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text(if (uiState.isPairing) "Conectando…" else "Conectar ao Mac", fontWeight = FontWeight.SemiBold)
        }

        if (uiState.hosts.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("Computadores conectados", color = DeskColors.TextPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            uiState.hosts.forEach { host ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        .background(DeskColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(host.name, color = DeskColors.TextPrimary, fontWeight = FontWeight.Medium)
                        Text("${host.host}:${host.port} · ${host.osType.osShortLabel()}", color = DeskColors.TextMuted, fontSize = 12.sp)
                    }
                    TextButton(onClick = { onRemoveHost(host.id) }) {
                        Text("Remover", color = DeskColors.Danger, fontSize = 12.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PairStepCard(number: String, title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(DeskColors.Surface, RoundedCornerShape(18.dp)).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = DeskColors.AccentSoft, shape = RoundedCornerShape(9.dp)) {
                Text(number, Modifier.padding(horizontal = 10.dp, vertical = 5.dp), color = DeskColors.Accent, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.padding(horizontal = 5.dp))
            Text(title, color = DeskColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        content()
    }
}
