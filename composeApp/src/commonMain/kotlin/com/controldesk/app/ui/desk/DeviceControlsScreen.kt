package com.controldesk.app.ui.desk

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.panel.PanelBackHandler
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.DeskIntent
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.ConnectedDevice
import com.controldesk.domain.model.DeviceCapability
import com.controldesk.domain.model.DeviceKind
import com.controldesk.domain.model.DeviceOperation

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceControlsScreen(state: DeskUiState, onIntent: (DeskIntent) -> Unit, onClearFeedback: () -> Unit) {
    val panel = state.devicePanel ?: return
    val catalog = state.deviceCatalogs[panel.hostId]
    val device = catalog?.devices?.singleOrNull { it.id == panel.deviceId }
    val busy = "${panel.hostId}:${panel.deviceId}" in state.executingDeviceIds
    PanelBackHandler(true) { onIntent(DeskIntent.DismissDevicePanel) }
    Scaffold(
        containerColor = DeskColors.Background,
        topBar = { TopAppBar(title = { Text(panel.name) }, navigationIcon = {
            TextButton(onClick = { onIntent(DeskIntent.DismissDevicePanel) }) { Text("Voltar") }
        }) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(device?.name ?: panel.name, fontSize = 20.sp, color = DeskColors.TextPrimary)
                Text(state.hosts.firstOrNull { it.id == panel.hostId }?.name.orEmpty(), color = DeskColors.TextMuted)
                if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Aguardando confirmação do agente…", color = DeskColors.TextMuted) }
                state.deviceErrors[panel.hostId]?.let { Text(it, color = DeskColors.Warning) }
                state.feedback?.let { message ->
                    Text(message, color = DeskColors.Warning)
                    TextButton(onClick = onClearFeedback) { Text("Dispensar") }
                }
                device?.notice?.let { Text(it, color = DeskColors.TextMuted) }
                if (device == null) Text(if (catalog == null) "Consultando o agente…" else "Dispositivo desconectado ou indisponível. O atalho e sua organização foram preservados.", color = DeskColors.TextMuted)
                OutlinedButton(enabled = !busy, onClick = { onIntent(DeskIntent.RefreshDevicePanel) }) { Text("Verificar controles") }
            }
            if (device != null) {
                if (DeviceCapability.REQUEST_CAMERA_ACCESS in device.capabilities) item {
                    ControlSection("Acesso à câmera") {
                        Text("Confirme o diálogo no Mac para consultar e usar os controles nativos. Não gravamos nem transmitimos vídeo.", color = DeskColors.TextMuted)
                        Button(enabled = !busy, onClick = { onIntent(DeskIntent.AuthorizePanelCamera) }) { Text("Autorizar câmera no Mac") }
                    }
                }
                if (device.kind == DeviceKind.CAMERA) {
                    item { NativeModeSection("Foco", "focusMode", "FOCUS_", device, busy, onIntent) }
                    item { NativeModeSection("Exposição", "exposureMode", "EXPOSURE_", device, busy, onIntent) }
                    item { NativeModeSection("Balanço de branco", "whiteBalanceMode", "WHITE_BALANCE_", device, busy, onIntent) }
                    if (DeviceCapability.SET_ENABLED in device.capabilities) item {
                        ControlSection("Disponibilidade da câmera") {
                            Text(if (device.enabled == false) "Bloqueada / desabilitada" else "Disponível; este estado não indica captura em andamento.", color = DeskColors.TextMuted)
                            Button(enabled = !busy && device.enabled != null, onClick = { onIntent(DeskIntent.ExecuteDevicePanelAction(DeviceOperation.TOGGLE_ENABLED)) }) {
                                Text(if (device.enabled == false) "Habilitar / liberar" else "Desabilitar / bloquear")
                            }
                        }
                    }
                }
                if (DeviceCapability.SET_MUTED in device.capabilities) item {
                    ControlSection("Mute") {
                        Text(if (device.muted == true) "Mudo" else "Áudio liberado", color = DeskColors.TextMuted)
                        Button(enabled = !busy && device.muted != null, onClick = { onIntent(DeskIntent.ExecuteDevicePanelAction(DeviceOperation.TOGGLE_MUTED)) }) {
                            Text(if (device.muted == true) "Desmutar" else "Mutar")
                        }
                    }
                }
                if (DeviceCapability.SET_LEVEL in device.capabilities) item {
                    ControlSection("Nível de áudio") {
                        Text("Atual: ${device.level ?: "—"}% · Ajuste: ${panel.level}%", color = DeskColors.TextMuted)
                        Slider(value = panel.level.toFloat(), valueRange = 0f..100f, enabled = !busy,
                            onValueChange = { onIntent(DeskIntent.UpdateDevicePanelLevel(it.toInt())) },
                            onValueChangeFinished = { onIntent(DeskIntent.ExecuteDevicePanelAction(DeviceOperation.SET_LEVEL)) })
                    }
                }
                if (DeviceCapability.SET_DEFAULT in device.capabilities) item {
                    ControlSection("Dispositivo padrão") {
                        Text(if (device.isDefault == true) "Já é o dispositivo padrão" else "Usar esta entrada/saída como padrão do sistema", color = DeskColors.TextMuted)
                        Button(enabled = !busy && device.isDefault != true, onClick = { onIntent(DeskIntent.ExecuteDevicePanelAction(DeviceOperation.SET_DEFAULT)) }) { Text("Definir como padrão") }
                    }
                }
            }
        }
    }
}

@Composable
private fun NativeModeSection(title: String, stateKey: String, prefix: String, device: ConnectedDevice, busy: Boolean, onIntent: (DeskIntent) -> Unit) {
    val operations = DeviceOperation.entries.filter { it.name.startsWith(prefix) && it.capability in device.capabilities }
    ControlSection(title) {
        if (operations.isEmpty()) {
            Text(if (device.settings["authorization"] in setOf("notDetermined", "denied")) "Autorize a câmera para verificar esta seção."
                else "O dispositivo/driver não oferece opções nativas configuráveis nesta seção.", color = DeskColors.TextMuted)
        } else {
            Text("Estado confirmado: ${device.settings[stateKey]?.let(::modeLabel) ?: "desconhecido"}", color = DeskColors.TextMuted)
            operations.forEach { operation ->
                FilterChip(selected = device.settings[stateKey] == operation.name.substringAfterLast('_').lowercase(), enabled = !busy,
                    onClick = { onIntent(DeskIntent.ExecuteDevicePanelAction(operation)) }, label = { Text(operation.displayName()) })
            }
        }
    }
}
private fun modeLabel(value: String): String = when (value) {
    "locked" -> "travado"
    "auto" -> "automático"
    "continuous" -> "automático contínuo"
    else -> value
}
@Composable
private fun ControlSection(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = DeskColors.SurfaceRaised)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontSize = 18.sp, color = DeskColors.TextPrimary)
            content()
        }
    }
}
