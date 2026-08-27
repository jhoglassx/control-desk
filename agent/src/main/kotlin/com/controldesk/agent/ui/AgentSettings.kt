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
        LaunchedEffect(port) {
            while (true) {
                val next = withContext(Dispatchers.IO) { fetchSetup(port) }
                setup = next
                status = if (next?.ok == true) "Rodando" else "Iniciando…"
                delay(2_000)
            }
        }
        Window(onCloseRequest = ::exitApplication, title = "Control Desk — Configurações") {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Top) {
                    Text("Control Desk — Configurações", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(12.dp))
                    Text("Status: $status")
                    Text("Porta: ${setup?.port ?: port}")
                    Text("PIN: ${setup?.pin ?: "—"}")
                    Spacer(Modifier.height(12.dp))
                    NetworkInfo.connectionHints(port).forEach { Text(it) }
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { Thread { fetchNewPin(port) }.start() }) {
                        Text("Gerar novo PIN")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = ::exitApplication) { Text("Fechar") }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Fechar ou Cmd+Q encerra esta janela. O agente continua em segundo plano, sem ícone no Dock.",
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
