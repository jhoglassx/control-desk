package com.controldesk.app.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.viewmodel.PinnedAppsIntent
import com.controldesk.app.viewmodel.PinnedAppsUiState
import com.controldesk.domain.model.*

internal fun ControlShortcutKind.displayName(): String = when (this) {
    ControlShortcutKind.VOLUME -> "Saída de áudio"
    ControlShortcutKind.MICROPHONE -> "Microfone"
    ControlShortcutKind.CAMERA -> "Câmera"
    ControlShortcutKind.DEVICE -> "Outro dispositivo"
    ControlShortcutKind.WEBSITE -> "Site ou painel web"
}
internal fun DeviceOperation.displayName(): String = when (this) {
    DeviceOperation.ENABLE -> "Habilitar câmera / liberar privacidade"
    DeviceOperation.DISABLE -> "Desabilitar câmera / bloquear privacidade"
    DeviceOperation.TOGGLE_ENABLED -> "Alternar disponibilidade da câmera"
    DeviceOperation.MUTE -> "Mutar"
    DeviceOperation.UNMUTE -> "Desmutar"
    DeviceOperation.TOGGLE_MUTED -> "Alternar mute"
    DeviceOperation.SET_LEVEL -> "Definir nível"
    DeviceOperation.SET_DEFAULT -> "Usar como dispositivo padrão"
}

@Composable
internal fun ControlShortcutEditor(state: PinnedAppsUiState, onIntent: (PinnedAppsIntent) -> Unit) {
    val draft = state.editingControl ?: return
    val audio = draft.kind == ControlShortcutKind.VOLUME || draft.kind == ControlShortcutKind.MICROPHONE
    val native = draft.destination == ShortcutDestination.DEVICE
    val saving = state.isSavingControl
    val device = state.deviceCatalog.devices.singleOrNull { it.id == draft.deviceId }
    LazyColumn(
        Modifier.fillMaxSize().background(DeskColors.ScreenGradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(enabled = !saving, onClick = { onIntent(PinnedAppsIntent.CancelControl) }) { Text("Voltar") }
            Text("Configurar atalho", fontSize = 24.sp, color = DeskColors.TextPrimary)
            Text("Escolha a ação. O agente executa o controle no computador.", color = DeskColors.TextMuted)
        }
        item {
            ControlShortcutKind.entries.forEach { kind ->
                FilterChip(selected = draft.kind == kind, enabled = !saving,
                    onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(
                        kind = kind, name = draft.name.ifBlank { kind.displayName() }, deviceId = null, deviceOperation = null,
                        destination = if (kind == ControlShortcutKind.WEBSITE) ShortcutDestination.URL else ShortcutDestination.DEVICE,
                    ))) }, label = { Text(kind.displayName()) })
            }
            OutlinedTextField(value = draft.name,
                onValueChange = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(name = it))) },
                label = { Text("Nome do atalho") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
        }
        item {
            if (draft.kind != ControlShortcutKind.WEBSITE) {
                FilterChip(selected = native, enabled = !saving,
                    onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(destination = ShortcutDestination.DEVICE))) },
                    label = { Text("Dispositivo detectado pelo agente") })
            }
            if (audio) {
                FilterChip(selected = !native, enabled = !saving,
                    onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(destination = ShortcutDestination.APPLICATION))) },
                    label = { Text("Controle do áudio padrão (Mac)") })
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.destination == ShortcutDestination.APPLICATION, enabled = !saving,
                        onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(destination = ShortcutDestination.APPLICATION, target = ""))) },
                        label = { Text("Abrir aplicativo") })
                    FilterChip(selected = draft.destination == ShortcutDestination.URL, enabled = !saving,
                        onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(destination = ShortcutDestination.URL, target = ""))) },
                        label = { Text("Abrir painel web") })
                }
            }
        }
        if (native) {
            item {
                TextButton(enabled = !state.isLoadingDevices, onClick = { onIntent(PinnedAppsIntent.LoadDevices) }) { Text("Atualizar dispositivos") }
                if (state.isLoadingDevices) LinearProgressIndicator(Modifier.fillMaxWidth())
                state.devicesError?.let { Text(it, color = DeskColors.Warning) }
                state.deviceCatalog.diagnostics.forEach { (adapter, message) -> Text("$adapter: $message", color = DeskColors.Warning, fontSize = 12.sp) }
                Text("Somente ações expostas pelo sistema/driver ficam disponíveis. Desabilitar uma câmera no sistema afeta todos os aplicativos.", color = DeskColors.TextMuted)
            }
            val devices = state.deviceCatalog.devices.filter { candidate ->
                when (draft.kind) {
                    ControlShortcutKind.CAMERA -> candidate.kind == DeviceKind.CAMERA
                    ControlShortcutKind.MICROPHONE -> candidate.kind == DeviceKind.AUDIO_INPUT
                    ControlShortcutKind.VOLUME -> candidate.kind == DeviceKind.AUDIO_OUTPUT
                    else -> true
                }
            }
            items(devices, key = { it.id }) { candidate ->
                ListItem(headlineContent = { Text(candidate.name) },
                    supportingContent = { Text(candidate.notice ?: if (candidate.isDefault == true) "Dispositivo padrão" else candidate.kind.name) },
                    trailingContent = {
                        TextButton(enabled = !saving, onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(deviceId = candidate.id, deviceOperation = null))) }) {
                            Text(if (candidate.id == draft.deviceId) "Selecionado" else "Escolher")
                        }
                    })
            }
            item {
                if (!state.isLoadingDevices && state.devicesError == null && devices.isEmpty()) Text("Nenhum dispositivo desta categoria encontrado.", color = DeskColors.TextMuted)
                if (device != null) {
                    Text("Ação para ${device.name}", color = DeskColors.TextPrimary)
                    if (device.capabilities.isEmpty()) Text("Dispositivo identificado, mas sem ações de controle disponíveis neste sistema ou sem permissão.", color = DeskColors.Warning)
                    DeviceOperation.entries.filter { it.capability in device.capabilities }.forEach { operation ->
                        FilterChip(selected = draft.deviceOperation == operation.name, enabled = !saving,
                            onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(deviceOperation = operation.name))) },
                            label = { Text(operation.displayName()) })
                    }
                    if (draft.deviceOperation == DeviceOperation.SET_LEVEL.name) {
                        Text("Nível: ${draft.deviceLevel}%", color = DeskColors.TextPrimary)
                        Slider(value = draft.deviceLevel.toFloat(), valueRange = 0f..100f,
                            onValueChange = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(deviceLevel = it.toInt()))) })
                    }
                }
            }
        } else if (audio) {
            item { Text("Toque para mutar e arraste o knob para ajustar o áudio padrão do Mac. Para um dispositivo físico específico, escolha o controle pelo agente.", color = DeskColors.TextMuted) }
        } else {
            item {
                if (draft.destination == ShortcutDestination.URL) {
                    OutlinedTextField(value = draft.target,
                        onValueChange = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(target = it))) },
                        label = { Text("URL http:// ou https://") }, singleLine = true, enabled = !saving, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Selecione o aplicativo de controle abaixo.", color = DeskColors.TextMuted)
                    if (draft.target.isNotBlank()) Text("Selecionado: ${state.installedApps.firstOrNull { it.bundlePath == draft.target }?.name ?: draft.target}", color = DeskColors.Accent)
                    state.appsError?.let { Text(it, color = DeskColors.Warning) }
                }
                Text("Esta opção abre o aplicativo/painel no computador; não controla diretamente o hardware.", color = DeskColors.TextMuted)
            }
        }
        item {
            state.controlError?.let { Text(it, color = DeskColors.Warning) }
            Button(enabled = !saving && !(native && state.isLoadingDevices), onClick = { onIntent(PinnedAppsIntent.SaveControl) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Salvando…" else "Salvar atalho")
            }
        }
        if (!audio && draft.destination == ShortcutDestination.APPLICATION) {
            items(state.installedApps, key = { it.id }) { app ->
                ListItem(headlineContent = { Text(app.name) }, leadingContent = { AppIcon(app, 40.dp) },
                    trailingContent = { TextButton(enabled = !saving, onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(target = app.bundlePath))) }) { Text("Escolher") } })
            }
        }
    }
}
