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
import com.controldesk.domain.model.ControlShortcutKind
import com.controldesk.domain.model.ShortcutDestination

internal fun ControlShortcutKind.displayName(): String = when (this) {
    ControlShortcutKind.VOLUME -> "Saída de áudio"
    ControlShortcutKind.MICROPHONE -> "Microfone"
    ControlShortcutKind.CAMERA -> "Câmera"
    ControlShortcutKind.DEVICE -> "Outro dispositivo"
    ControlShortcutKind.WEBSITE -> "Site ou painel web"
}

@Composable
internal fun ControlShortcutEditor(state: PinnedAppsUiState, onIntent: (PinnedAppsIntent) -> Unit) {
    val draft = state.editingControl ?: return
    val audio = draft.kind == ControlShortcutKind.VOLUME || draft.kind == ControlShortcutKind.MICROPHONE
    val saving = state.isSavingControl
    LazyColumn(
        Modifier.fillMaxSize().background(DeskColors.ScreenGradient).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(enabled = !saving, onClick = { onIntent(PinnedAppsIntent.CancelControl) }) { Text("Voltar") }
            Text("Configurar atalho", fontSize = 24.sp, color = DeskColors.TextPrimary)
            Text("Escolha a ação e o nome que aparecerá na grade.", color = DeskColors.TextMuted)
        }
        item {
            ControlShortcutKind.entries.forEach { kind ->
                FilterChip(
                    selected = draft.kind == kind,
                    enabled = !saving,
                    onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(kind = kind, name = draft.name.ifBlank { kind.displayName() }, destination = if (kind == ControlShortcutKind.WEBSITE) ShortcutDestination.URL else draft.destination))) },
                    label = { Text(kind.displayName()) },
                )
            }
            OutlinedTextField(
                value = draft.name,
                onValueChange = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(name = it))) },
                label = { Text("Nome do atalho") }, singleLine = true,
                enabled = !saving, modifier = Modifier.fillMaxWidth(),
            )
        }
        if (audio) {
            item {
                Text("Controla o dispositivo de áudio padrão do Mac. Toque para mutar; arraste o controle para ajustar o nível. Posição e tamanho são salvos como nos apps.", color = DeskColors.TextMuted)
                Text("Não seleciona uma entrada ou saída física específica. Controle nativo Windows/Linux ainda não disponível.", color = DeskColors.Warning)
            }
        } else {
            item {
                Text("O que este atalho deve abrir no computador?", color = DeskColors.TextPrimary)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = draft.destination == ShortcutDestination.APPLICATION,
                        enabled = !saving,
                        onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(destination = ShortcutDestination.APPLICATION, target = ""))) },
                        label = { Text("Aplicativo") })
                    FilterChip(selected = draft.destination == ShortcutDestination.URL,
                        enabled = !saving,
                        onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(destination = ShortcutDestination.URL, target = ""))) },
                        label = { Text("URL / painel web") })
                }
                if (draft.destination == ShortcutDestination.URL) {
                    OutlinedTextField(value = draft.target,
                        onValueChange = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(target = it))) },
                        label = { Text("URL http:// ou https://") },
                        placeholder = { Text("http://192.168.1.50") }, singleLine = true,
                        enabled = !saving, modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Selecione o aplicativo de controle abaixo.", color = DeskColors.TextMuted)
                    if (draft.target.isNotBlank()) Text("Selecionado: ${state.installedApps.firstOrNull { it.bundlePath == draft.target }?.name ?: draft.target}", color = DeskColors.Accent)
                    if (state.isLoadingApps) LinearProgressIndicator(Modifier.fillMaxWidth())
                    state.appsError?.let { Text(it, color = DeskColors.Warning) }
                }
                Text("Abre o aplicativo ou painel configurado; não liga/desliga a câmera nem altera a captura em Teams, Zoom ou OBS. Essas ações exigem integrações específicas. Não há descoberta automática de hardware nesta versão.", color = DeskColors.TextMuted)
            }
        }
        item {
            state.controlError?.let { Text(it, color = DeskColors.Warning) }
            Button(enabled = !saving, onClick = { onIntent(PinnedAppsIntent.SaveControl) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (saving) "Salvando…" else "Salvar atalho")
            }
        }
        if (!audio && draft.destination == ShortcutDestination.APPLICATION) {
            items(state.installedApps, key = { it.id }) { app ->
                ListItem(
                    headlineContent = { Text(app.name) },
                    leadingContent = { AppIcon(app, 40.dp) },
                    trailingContent = {
                        TextButton(enabled = !saving, onClick = { onIntent(PinnedAppsIntent.UpdateControl(draft.copy(target = app.bundlePath))) }) {
                            Text(if (draft.target == app.bundlePath) "Selecionado" else "Escolher")
                        }
                    },
                )
            }
        }
    }
}
