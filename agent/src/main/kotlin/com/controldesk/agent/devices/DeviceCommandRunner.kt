package com.controldesk.agent.devices

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

fun interface DeviceCommandRunner { fun run(command: List<String>): String }

/** Reads both pipes concurrently; successful diagnostics on stderr cannot corrupt JSON on stdout. */
class ProcessDeviceCommandRunner(private val timeoutSeconds: Long = 8) : DeviceCommandRunner {
    override fun run(command: List<String>): String {
        val process = ProcessBuilder(command).start()
        val output = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().use { it.readText() } }
        val errors = CompletableFuture.supplyAsync { process.errorStream.bufferedReader().use { it.readText() } }
        try {
            check(process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) { "Tempo limite no controle nativo. Confirme a permissão no computador e atualize o estado antes de tentar novamente." }
            val result = output.get(1, TimeUnit.SECONDS).trim().removePrefix("\uFEFF")
            val error = errors.get(1, TimeUnit.SECONDS).trim()
            check(process.exitValue() == 0) { error.ifBlank { result }.take(600).ifBlank { "Falha no controle nativo do dispositivo" } }
            return result
        } finally {
            if (process.isAlive) process.destroyForcibly()
            output.cancel(true)
            errors.cancel(true)
        }
    }
}
object NativeDeviceCommandRunner : DeviceCommandRunner by ProcessDeviceCommandRunner()
