package com.controldesk.agent

import java.util.concurrent.TimeUnit

object Shell {
    fun run(vararg command: String, timeoutMs: Long = 2_000): String {
        val process = ProcessBuilder(*command)
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ""
        }
        return process.inputStream.bufferedReader().readText().trim()
    }

    /** Commands acknowledged to the mobile must report failure instead of being fire-and-forget. */
    fun runChecked(vararg command: String, timeoutMs: Long = 8_000) {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val output = java.util.concurrent.CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader().use { it.readText() }
        }
        try {
            check(process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)) { "O macOS não confirmou a abertura no tempo esperado. Verifique o computador antes de tentar novamente." }
            val detail = output.get(1, TimeUnit.SECONDS).trim().take(600)
            check(process.exitValue() == 0) { detail.ifBlank { "O macOS recusou a abertura do aplicativo" } }
        } finally {
            if (process.isAlive) process.destroyForcibly()
            output.cancel(true)
        }
    }

    fun spawn(vararg command: String) {
        ProcessBuilder(*command)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start()
    }

    fun osascript(script: String, timeoutMs: Long = 1_500): String =
        run("/usr/bin/osascript", "-e", script, timeoutMs = timeoutMs)

    fun escape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
}
