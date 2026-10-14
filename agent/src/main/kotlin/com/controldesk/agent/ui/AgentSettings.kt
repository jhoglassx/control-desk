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
                        Text("Conecte este Mac ao app do celular", style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
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
