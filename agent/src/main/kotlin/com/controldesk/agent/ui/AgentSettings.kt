package com.controldesk.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.window.application
import com.controldesk.protocol.SetupResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URI

fun launchAgentSettings(port: Int, exitProcessOnClose: Boolean = true) {
    application(exitProcessOnExit = exitProcessOnClose) {
        var deviceCatalog by remember { mutableStateOf<com.controldesk.protocol.DeviceCatalogResponse?>(null) }
        var deviceCheckError by remember { mutableStateOf<String?>(null) }
        var checkingDevices by remember { mutableStateOf(false) }
        var deviceCheckRevision by remember { mutableStateOf(0) }
        var setup by remember { mutableStateOf<SetupResponse?>(null) }
        var status by remember { mutableStateOf("Iniciando…") }
        var accessibility by remember { mutableStateOf<Boolean?>(null) }
        var expandedAddresses by remember { mutableStateOf(false) }
        var notice by remember { mutableStateOf<String?>(null) }
        var confirmRevocation by remember { mutableStateOf(false) }
        var accessibilityPromptRequested by remember { mutableStateOf(false) }
        LaunchedEffect(port) {
            while (true) {
                val next = withContext(Dispatchers.IO) { fetchSetup(port) }
                setup = next
                accessibility = next?.accessibilityEnabled
                status = when {
                    next?.ok != true -> "Iniciando serviço…"
                    next.accessibilityEnabled == false -> "Rodando — autorize esta versão"
                    else -> "Rodando"
                }
                if (next?.ok == true && next.accessibilityEnabled == false && !accessibilityPromptRequested) {
                    accessibilityPromptRequested = true
                    withContext(Dispatchers.IO) { MacAccessibility.requestAccess() }
                }
                delay(2_000)
            }
        }
        LaunchedEffect(port, setup?.ok, deviceCheckRevision) {
            if (setup?.ok != true) return@LaunchedEffect
            checkingDevices = true
            val result = withContext(Dispatchers.IO) { fetchSetupDevices(port) }
            deviceCatalog = result.getOrNull()
            deviceCheckError = result.exceptionOrNull()?.message
            checkingDevices = false
        }
        Window(
            onCloseRequest = ::exitApplication,
            title = "Control Desk Agent",
            state = rememberWindowState(width = 700.dp, height = 740.dp),
        ) {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.Top,
                ) {
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),
                    ) {
                        Text("Control Desk", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(5.dp))
                        Text("Conecte este computador ao app do celular", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
                        Spacer(Modifier.height(16.dp))

                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(18.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text("●", color = if (setup?.ok == true) Color(0xFF16A34A) else Color(0xFFF59E0B))
                                Spacer(Modifier.width(8.dp))
                                Text(status, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Versão do serviço: ${setup?.agentVersion ?: "consultando…"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                            )
                            if (setup?.buildId != null) Text("Compilação: ${setup?.buildId?.take(12)}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            if (setup?.buildId != null && setup?.buildId != AgentBuildIdentity.VALUE) {
                                Text("A configuração e o serviço são de compilações diferentes. Reabra o app em Aplicativos para sincronizar.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF92400E))
                            }
                            Spacer(Modifier.height(14.dp))
                            Text("PIN DE CONEXÃO", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                            Text(setup?.pin ?: "—", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            Text("Válido por 10 minutos. Digite este código no Control Desk do celular.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = {
                        Thread {
                            val changed = fetchNewPin(port)
                            java.awt.EventQueue.invokeLater {
                                notice = if (changed) "Novo PIN gerado. Ele é válido por 10 minutos."
                                else "Não foi possível gerar um novo PIN. Verifique se o agente está ativo."
                            }
                        }.start()
                    }) { Text("Gerar novo PIN") }
                    notice?.let { Text(it, color = Color(0xFF15803D), style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("1. Instale no celular", fontWeight = FontWeight.SemiBold)
                            Text("Use ‘Buscar Mac na mesma rede’ na tela de pareamento. Se não aparecer, deixe os dois aparelhos na mesma rede Wi‑Fi.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = { expandedAddresses = !expandedAddresses }) {
                                Text(if (expandedAddresses) "Ocultar detalhes de rede" else "Problemas para encontrar? Ver endereço manual")
                            }
                            if (expandedAddresses) {
                                NetworkInfo.connectionHints(port).filterNot { it.startsWith("Local:") }.forEach {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    when (accessibility) {
                        false -> Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF7E6))) {
                            Column(Modifier.fillMaxWidth().padding(14.dp)) {
                                Text("Conclua a permissão de Acessibilidade", color = Color(0xFF92400E), fontWeight = FontWeight.SemiBold)
                                Text(
                                    "O macOS precisa autorizar o agente para controlar janelas. Se ControlDeskAgent já estiver na lista, ligue a chave. Se não estiver, use + e escolha ControlDeskAgent em Aplicativos. Depois volte aqui; o status será atualizado automaticamente.",
                                    color = Color(0xFF92400E),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { MacAccessibility.openPrivacySettings() }) { Text("Abrir Ajustes do macOS") }
                            }
                        }
                        true -> Text("Acessibilidade ativada", color = Color(0xFF15803D), style = MaterialTheme.typography.bodySmall)
                        null -> Unit
                    }
                    Spacer(Modifier.height(12.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Dispositivos e permissões", fontWeight = FontWeight.SemiBold)
                            Text("A primeira abertura solicita Acessibilidade no Mac. A confirmação da chave é feita por você nos Ajustes. As permissões e capacidades dos dispositivos são verificadas abaixo.", style = MaterialTheme.typography.bodySmall)
                            Text(when {
                                System.getProperty("os.name").lowercase().contains("mac") -> "Listar câmeras não inicia captura nem precisa solicitar acesso de captura. O macOS não expõe bloqueio global de câmera neste adaptador; conceder mais permissões não habilita essa ação."
                                System.getProperty("os.name").lowercase().contains("win") -> "Para habilitar/desabilitar câmeras, feche e inicie o agente como administrador (Executar como administrador). Confirme o UAC do Windows."
                                else -> "Use a sessão do seu usuário com acesso ao servidor de áudio e permissão de escrita no dispositivo de vídeo. Instale v4l-utils e pactl quando indicados; mudanças de grupo podem exigir nova sessão."
                            }, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            if (checkingDevices) Text("Verificando dispositivos…", style = MaterialTheme.typography.bodySmall)
                            deviceCheckError?.let { Text("Não foi possível verificar: $it", color = Color(0xFF92400E), style = MaterialTheme.typography.bodySmall) }
                            deviceCatalog?.let { catalog ->
                                Text("${catalog.devices.count { it.kind == "CAMERA" }} câmera(s) · ${catalog.devices.count { it.kind == "AUDIO_INPUT" }} entrada(s) · ${catalog.devices.count { it.kind == "AUDIO_OUTPUT" }} saída(s)", style = MaterialTheme.typography.bodySmall)
                                catalog.diagnostics.forEach { (adapter, message) -> Text("$adapter: $message", color = Color(0xFF92400E), style = MaterialTheme.typography.bodySmall) }
                                catalog.devices.filter { it.notice != null }.map { it.notice!! }.distinct().forEach { message ->
                                    Text(message, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            OutlinedButton(enabled = !checkingDevices, onClick = { deviceCheckRevision++ }) { Text("Verificar novamente") }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("O agente inicia junto com sua sessão e continua ativo ao fechar esta janela.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        TextButton(onClick = { confirmRevocation = true }) { Text("Desconectar dispositivos") }
                        Spacer(Modifier.weight(1f))
                        Button(onClick = ::exitApplication) { Text("Concluir") }
                    }
                }
            }
            if (confirmRevocation) {
                AlertDialog(
                    onDismissRequest = { confirmRevocation = false },
                    title = { Text("Revogar todos os pareamentos?") },
                    text = { Text("Todos os celulares perderão acesso e precisarão parear novamente.") },
                    confirmButton = {
                        TextButton(onClick = {
                            confirmRevocation = false
                            Thread {
                                val revoked = revokeAllTokens(port)
                                        java.awt.EventQueue.invokeLater {
                                            notice = revoked?.let { "$it pareamento(s) revogado(s)." }
                                                ?: "Não foi possível revogar os pareamentos."
                                        }
                            }.start()
                        }) { Text("Revogar todos") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRevocation = false }) { Text("Cancelar") }
                    },
                )
            }
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun fetchSetup(port: Int): SetupResponse? = runCatching {
    val connection = URI("http://127.0.0.1:$port/setup/health").toURL().openConnection() as HttpURLConnection
    connection.connectTimeout = 1_000
    connection.readTimeout = 1_000
    val text = connection.inputStream.bufferedReader().use { it.readText() }
    connection.disconnect()
    json.decodeFromString<SetupResponse>(text)
}.getOrNull()

private fun fetchNewPin(port: Int): Boolean {
    return runCatching {
        val connection = URI("http://127.0.0.1:$port/setup/new-pin").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write("{}".toByteArray()) }
        val response = json.decodeFromString<SetupResponse>(connection.inputStream.bufferedReader().readText())
        connection.disconnect()
        response.ok
    }.getOrDefault(false)
}

private fun revokeAllTokens(port: Int): Int? = runCatching {
    val connection = URI("http://127.0.0.1:$port/setup/revoke-tokens").toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    connection.outputStream.use { it.write("{}".toByteArray()) }
    val response = json.parseToJsonElement(connection.inputStream.bufferedReader().readText()).jsonObject
    connection.disconnect()
    response["revoked"]?.jsonPrimitive?.intOrNull
}.getOrNull()


private fun fetchSetupDevices(port: Int): Result<com.controldesk.protocol.DeviceCatalogResponse> = runCatching {
    val connection = URI("http://127.0.0.1:$port/setup/devices").toURL().openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 1_000
        connection.readTimeout = 20_000
        val status = connection.responseCode
        check(status != 404) { "O serviço em execução é de outra compilação. Reabra ControlDeskAgent em Aplicativos para atualizar o serviço; isso não é uma permissão de câmera negada." }
        check(status == 200) { "O agente não confirmou o diagnóstico de dispositivos (HTTP $status)." }
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        json.decodeFromString<com.controldesk.protocol.DeviceCatalogResponse>(text)
    } finally { connection.disconnect() }
}
