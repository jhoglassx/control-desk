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
