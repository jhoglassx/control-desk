package com.controldesk.app.ui.desk

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.util.rememberBase64Image
import com.controldesk.app.viewmodel.PinnedAppsIntent
import com.controldesk.app.viewmodel.PinnedAppsUiState
import com.controldesk.domain.model.ControlShortcut
import com.controldesk.domain.model.ControlShortcutKind
import com.controldesk.domain.model.ShortcutDestination
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.PinnedAppShortcut

@Composable
fun PinnedAppsScreen(
    host: PairedHost,
    state: PinnedAppsUiState,
    onBack: () -> Unit,
    onIntent: (PinnedAppsIntent) -> Unit,
) {
    if (state.editingControl != null) {
        ControlShortcutEditor(state, onIntent)
        return
    }
    val selectedApp = host.pinnedApps.firstOrNull { it.id == state.selectedAppId }
    if (selectedApp != null) {
        MacPathPickerScreen(
            app = selectedApp,
            state = state,
            onBack = { onIntent(PinnedAppsIntent.CancelPathSelection) },
            onIntent = onIntent,
        )
    } else {
        PinnedAppsListScreen(host, state, onBack, onIntent)
    }
}

@Composable
private fun PinnedAppsListScreen(
    host: PairedHost,
    state: PinnedAppsUiState,
    onBack: () -> Unit,
    onIntent: (PinnedAppsIntent) -> Unit,
) {
    var search by remember { mutableStateOf("") }
    val pinned = host.pinnedApps.sortedBy { it.order }
    val available = state.installedApps
        .filter { candidate -> pinned.none { it.bundlePath == candidate.bundlePath } }
        .filter { it.name.contains(search, ignoreCase = true) }
        .sortedBy { it.name.lowercase() }

    Column(
        Modifier.fillMaxSize().background(DeskColors.ScreenGradient).padding(horizontal = 20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹  Voltar") }
            Spacer(Modifier.weight(1f))
            Text("${pinned.size} fixos", color = DeskColors.TextMuted, fontSize = 12.sp)
        }
        Text("Atalhos", color = DeskColors.TextPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Adicione apps, áudio e painéis de câmera ou dispositivos. Organize tudo na tela principal.",
            color = DeskColors.TextMuted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "control-heading") {
                SectionHeading("CONTROLES E DISPOSITIVOS", "Áudio, câmeras e outros dispositivos como cards da grade.")
                Button(onClick = {
                    onIntent(PinnedAppsIntent.EditControl(ControlShortcut(
                        id = "control-${kotlin.random.Random.nextLong().toULong()}",
                        name = "", kind = ControlShortcutKind.CAMERA,
                    )))
                }) { Text("Adicionar controle ou dispositivo") }
                state.controlError?.let { Text(it, color = DeskColors.Warning) }
            }
            items(host.controlShortcuts, key = { "control-${it.id}" }) { shortcut ->
                Card(colors = CardDefaults.cardColors(containerColor = DeskColors.SurfaceRaised)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(shortcut.name, color = DeskColors.TextPrimary)
                            Text(shortcut.kind.displayName(), color = DeskColors.TextMuted, fontSize = 12.sp)
                        }
                        TextButton(onClick = { onIntent(PinnedAppsIntent.EditControl(shortcut)) }) { Text("Editar") }
                        TextButton(enabled = !state.isSavingControl, onClick = { onIntent(PinnedAppsIntent.RemoveControl(shortcut.id)) }) { Text("Remover") }
                    }
                }
            }
            if (pinned.isNotEmpty()) {
                item(key = "pinned-heading") {
                    SectionHeading("FIXADOS", "Toque em um app para abrir. Você pode associar um projeto, pasta ou arquivo.")
                }
                items(pinned, key = { "pinned-${it.id}" }) { app ->
                    PinnedAppCard(
                        app = app,
                        index = pinned.indexOf(app),
                        lastIndex = pinned.lastIndex,
                        onChoosePath = { onIntent(PinnedAppsIntent.ChoosePathFor(app.id)) },
                        onMoveUp = { onIntent(PinnedAppsIntent.MoveApp(app.id, -1)) },
                        onMoveDown = { onIntent(PinnedAppsIntent.MoveApp(app.id, 1)) },
                        onUnpin = { onIntent(PinnedAppsIntent.UnpinApp(app.id)) },
                    )
                }
            }

            item(key = "installed-heading") {
                Spacer(Modifier.height(6.dp))
                SectionHeading("ADICIONAR APP", "Fixe um app para deixá-lo disponível na tela principal.")
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                    label = { Text("Buscar aplicativo") },
                    singleLine = true,
                )
                if (state.isLoadingApps) {
                    Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("  Carregando apps do Mac…", color = DeskColors.TextMuted)
                    }
                }
                state.appsError?.let { error ->
                    Text(error, color = DeskColors.Warning, modifier = Modifier.padding(top = 10.dp))
                    TextButton(onClick = { onIntent(PinnedAppsIntent.LoadInstalledApps(host)) }) { Text("Tentar novamente") }
                }
                if (!state.isLoadingApps && state.appsError == null && state.installedApps.isEmpty()) {
                    Text("Nenhum app disponível. Tente atualizar a lista.", color = DeskColors.TextMuted, modifier = Modifier.padding(top = 10.dp))
                }
            }

            items(available, key = { "available-${it.id}" }) { app ->
                AvailableAppRow(app) { onIntent(PinnedAppsIntent.PinApp(app)) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PinnedAppCard(
    app: PinnedAppShortcut,
    index: Int,
    lastIndex: Int,
    onChoosePath: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onUnpin: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = DeskColors.SurfaceRaised)) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 48.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(app.name, color = DeskColors.TextPrimary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Fixado na posição ${index + 1}", color = DeskColors.TextMuted, fontSize = 12.sp)
                }
                TextButton(onClick = onMoveUp, enabled = index > 0) { Text("↑") }
                TextButton(onClick = onMoveDown, enabled = index < lastIndex) { Text("↓") }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                app.projectPath ?: "Sem item associado. Abre o app normalmente.",
                color = DeskColors.TextMuted,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onChoosePath) { Text(if (app.projectPath == null) "Escolher projeto ou arquivo" else "Alterar item") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onUnpin) { Text("Remover") }
            }
        }
    }
}

@Composable
private fun AvailableAppRow(app: PinnedAppShortcut, onPin: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = DeskColors.Surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 40.dp)
            Text(app.name, color = DeskColors.TextPrimary, modifier = Modifier.weight(1f).padding(horizontal = 12.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            OutlinedButton(onClick = onPin) { Text("Fixar") }
        }
    }
}

@Composable
private fun MacPathPickerScreen(
    app: PinnedAppShortcut,
    state: PinnedAppsUiState,
    onBack: () -> Unit,
    onIntent: (PinnedAppsIntent) -> Unit,
) {
    val listing = state.fileListing
    Column(Modifier.fillMaxSize().background(DeskColors.ScreenGradient).padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹  Apps fixos") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 52.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("O que deve abrir?", color = DeskColors.TextPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("${app.name} · escolha uma pasta, projeto ou arquivo", color = DeskColors.TextMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        listing?.let { current ->
            Text(current.path, color = DeskColors.TextMuted, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onIntent(PinnedAppsIntent.BrowseFiles(current.parentPath)) }, enabled = current.parentPath != null) {
                    Text("↑ Pasta anterior")
                }
                Button(onClick = { onIntent(PinnedAppsIntent.SelectPath(current.path)) }) { Text("Usar esta pasta") }
            }
        }
        if (state.isLoadingFiles) {
            Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("  Abrindo pastas do Mac…", color = DeskColors.TextMuted)
            }
        }
        state.filesError?.let { error -> Text(error, color = DeskColors.Warning, modifier = Modifier.padding(vertical = 12.dp)) }
        if (listing == null && !state.isLoadingFiles) {
            TextButton(onClick = { onIntent(PinnedAppsIntent.BrowseFiles(null)) }) { Text("Tentar novamente") }
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(listing?.entries.orEmpty(), key = { it.path }) { entry ->
                Card(colors = CardDefaults.cardColors(containerColor = DeskColors.SurfaceRaised)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(enabled = entry.isDirectory) {
                                onIntent(PinnedAppsIntent.BrowseFiles(entry.path))
                            }.padding(vertical = 8.dp),
                        ) {
                            Text("${if (entry.isDirectory) "▰" else "▤"}  ${entry.name}", color = DeskColors.TextPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(if (entry.isDirectory) "Pasta" else "Arquivo", color = DeskColors.TextMuted, fontSize = 11.sp)
                        }
                        if (entry.isDirectory) {
                            TextButton(onClick = { onIntent(PinnedAppsIntent.SelectPath(entry.path)) }) { Text("Usar pasta") }
                        } else {
                            TextButton(onClick = { onIntent(PinnedAppsIntent.SelectPath(entry.path)) }) { Text("Selecionar") }
                        }
                    }
                }
            }
        }
        Text("Você pode escolher uma pasta e navegar dentro dela, ou selecionar um arquivo.", color = DeskColors.TextMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 12.dp))
    }
}

@Composable
internal fun AppIcon(app: PinnedAppShortcut, size: androidx.compose.ui.unit.Dp) {
    val icon = rememberBase64Image(app.iconBase64)
    Box(
        Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(DeskColors.Surface),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Image(icon, contentDescription = "Ícone de ${app.name}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        } else {
            Text(app.name.take(1).uppercase(), color = DeskColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = DeskColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = DeskColors.TextMuted, fontSize = 11.sp)
    }
}
