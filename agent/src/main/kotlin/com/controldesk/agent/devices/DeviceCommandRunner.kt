package com.controldesk.agent.devices

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun interface DeviceCommandRunner {
    fun run(command: List<String>): String
}

/** Drains output while waiting, checks exit status and never opens a shell. */
object NativeDeviceCommandRunner : DeviceCommandRunner {
    override fun run(command: List<String>): String {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readText() } }
        try {
            check(process.waitFor(8, TimeUnit.SECONDS)) { "Tempo limite ao consultar/executar controle de dispositivo" }
            val result = output.get(1, TimeUnit.SECONDS).trim().removePrefix("\uFEFF")
            check(process.exitValue() == 0) { result.take(600).ifBlank { "Falha no controle nativo do dispositivo" } }
            return result
        } finally {
            if (process.isAlive) process.destroyForcibly()
            output.cancel(true)
        }
    }
}
