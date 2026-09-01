package com.controldesk.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import co.touchlab.kermit.platformLogWriter
import java.io.File

internal actual fun installPlatformLogWriters() {
    val logFile = File(System.getProperty("user.home"), ".control-desk/agent.log")
    Logger.setLogWriters(
        buildList {
            add(platformLogWriter())
            add(JvmFileLogWriter(logFile))
            if (FirebaseBootstrap.isReady) add(AgentFirestoreLogWriter())
        },
    )
}

internal class JvmFileLogWriter(
    private val file: File,
) : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val line = buildString {
            append(System.currentTimeMillis())
            append(" [")
            append(severity.name)
            append('/')
            append(tag)
            append("] ")
            append(message)
            append('\n')
        }
        synchronized(file) {
            file.parentFile?.mkdirs()
            file.appendText(line)
            if (throwable != null) {
                file.appendText(throwable.stackTraceToString())
                if (!throwable.stackTraceToString().endsWith('\n')) {
                    file.appendText("\n")
                }
            }
        }
    }
}
