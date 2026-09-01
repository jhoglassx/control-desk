package com.controldesk.agent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.controldesk.protocol.SetupResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URI

fun launchAgentSettings(port: Int, exitProcessOnClose: Boolean = true) {
    application(exitProcessOnExit = exitProcessOnClose) {
        var setup by remember { mutableStateOf<SetupResponse?>(null) }
        var status by remember { mutableStateOf("Iniciando…") }
        var accessibility by remember { mutableStateOf<Boolean?>(null) }
        LaunchedEffect(port) {
            while (true) {
                val next = withContext(Dispatchers.IO) { fetchSetup(port) }
                setup = next
                accessibility = next?.accessibilityEnabled
                status = when {
                    next?.ok != true -> "Iniciando serviço…"
                    next.accessibilityEnabled == false -> "Rodando — falta Acessibilidade"
                    else -> "Rodando"
                }
                delay(2_000)
            }
        }
        Window(onCloseRequest = ::exitApplication, title = "Control Desk Agent") {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Top) {
                    Text("Control Desk Agent", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                    Text("Status: $status")
                    Text("Porta: ${setup?.port ?: port}")
                    Text("PIN: ${setup?.pin ?: "—"}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(12.dp))
                    NetworkInfo.connectionHints(port).forEach { Text(it) }
                    Spacer(Modifier.height(16.dp))
                    when (accessibility) {
                        false -> {
                            Text(
                                "Ative Acessibilidade para ControlDeskAgent — sem isso o celular não vê qual janela está em uso.",
                                color = Color(0xFFB45309),
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { MacAccessibility.openPrivacySettings() }) {
                                Text("Abrir Ajustes de Acessibilidade")
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        true -> Text("Acessibilidade: ativa ✓", color = Color(0xFF15803D))
                        null -> Unit
                    }
                    Button(onClick = { Thread { fetchNewPin(port) }.start() }) {
                        Text("Gerar novo PIN")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = ::exitApplication) { Text("Fechar (fica em segundo plano)") }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Feche esta janela quando terminar. O agente continua na porta $port sem ícone no Dock. " +
                            "Para ver o PIN de novo, abra Control Desk Agent em Aplicativos.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

private val json = Json { ignoreUnknownKeys = true }

private fun fetchSetup(port: Int): SetupResponse? = runCatching {
    val text = URI("http://127.0.0.1:$port/setup").toURL().readText()
    json.decodeFromString<SetupResponse>(text)
}.getOrNull()

private fun fetchNewPin(port: Int) {
    runCatching {
        val connection = URI("http://127.0.0.1:$port/setup/new-pin").toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write("{}".toByteArray()) }
        connection.inputStream.bufferedReader().readText()
        connection.disconnect()
    }
}
