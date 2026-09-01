package com.controldesk.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Agente JVM (Compose Desktop): Crashlytics não existe para JVM.
 * Envia Warn/Error para Firestore `agent_logs` no mesmo projeto Firebase.
 */
internal class AgentFirestoreLogWriter : LogWriter() {
  override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
    if (severity < Severity.Warn) return
    AgentFirestoreUploader.enqueue(
      severity = severity.name,
      tag = tag,
      message = message,
      stack = throwable?.stackTraceToString(),
    )
  }
}

internal object AgentFirestoreUploader {
  private val queue = LinkedBlockingQueue<LogEvent>()
  private val started = AtomicBoolean(false)

  fun enqueue(severity: String, tag: String, message: String, stack: String?) {
    val config = GoogleServicesConfig.load() ?: return
    queue.offer(LogEvent(severity, tag, message, stack, config))
    if (started.compareAndSet(false, true)) {
      Thread({ drainLoop() }, "agent-firestore-log").apply { isDaemon = true }.start()
    }
  }

  private fun drainLoop() {
    while (true) {
      val event = queue.take()
      runCatching { post(event) }
    }
  }

  private fun post(event: LogEvent) {
    val body = buildJsonObject {
      put("fields", buildJsonObject {
        put("severity", buildJsonObject { put("stringValue", event.severity) })
        put("tag", buildJsonObject { put("stringValue", event.tag) })
        put("message", buildJsonObject { put("stringValue", event.message) })
        put("host", buildJsonObject { put("stringValue", System.getenv("HOSTNAME") ?: "mac") })
        put("ts", buildJsonObject { put("integerValue", System.currentTimeMillis().toString()) })
        event.stack?.let { stack ->
          put("stack", buildJsonObject { put("stringValue", stack.take(8_000)) })
        }
      })
    }.toString()
    val url =
      "https://firestore.googleapis.com/v1/projects/${event.config.projectId}/databases/(default)/documents/agent_logs?key=${event.config.apiKey}"
    val connection = URI(url).toURL().openConnection() as HttpURLConnection
    connection.requestMethod = "POST"
    connection.doOutput = true
    connection.setRequestProperty("Content-Type", "application/json")
    connection.outputStream.use { it.write(body.toByteArray()) }
    connection.inputStream.use { it.readBytes() }
    connection.disconnect()
  }

  private data class LogEvent(
    val severity: String,
    val tag: String,
    val message: String,
    val stack: String?,
    val config: GoogleServicesConfig.Config,
  )
}

internal object GoogleServicesConfig {
  data class Config(
    val projectId: String,
    val apiKey: String,
    val applicationId: String,
  )

  fun load(): Config? {
    val stream = GoogleServicesConfig::class.java.classLoader
      ?.getResourceAsStream("google-services.json")
      ?: return null
    val root = runCatching {
      Json.parseToJsonElement(stream.bufferedReader().readText()).jsonObject
    }.getOrNull() ?: return null
    val projectId = root["project_info"]?.jsonObject?.get("project_id")?.jsonPrimitive?.content ?: return null
    val client = root["client"]?.jsonArray?.firstOrNull()?.jsonObject ?: return null
    val appId = client["client_info"]?.jsonObject?.get("mobilesdk_app_id")?.jsonPrimitive?.content ?: return null
    val apiKey = client["api_key"]?.jsonArray?.firstOrNull()
      ?.jsonObject?.get("current_key")?.jsonPrimitive?.content ?: return null
    return Config(projectId = projectId, apiKey = apiKey, applicationId = appId)
  }
}
