package com.controldesk.agent

import com.controldesk.logging.ControlDeskLog
import java.util.concurrent.TimeUnit

/**
 * Ensures a single listener on the agent port. A previous Cmd+Q leaves a headless
 * daemon on 8741; `:agent:run` must close that process and bind again.
 */
object AgentPort {
    private val ourMarkers = listOf(
        "com.controldesk.agent",
        "AgentMainKt",
        "ControlDeskAgent",
        "control.desk.headless",
        "control-desk",
    )

    /**
     * Gradle `:agent:run` (UI, not bundled) takes over port 8741.
     * The LaunchAgent / `--headless` process must not bootout itself.
     */
    internal fun shouldReplaceOccupant(headless: Boolean, bundled: Boolean): Boolean =
        !headless && !bundled

    fun replaceOccupant(port: Int) {
        if (!shouldReplaceOccupant(headless = isHeadless(), bundled = isBundled())) return
        val self = ProcessHandle.current().pid()
        val pids = listeningPids(port).filter { it != self }
        if (pids.isEmpty() && !isLaunchAgentLoaded()) return

        ControlDeskLog.agent.i { "Porta $port em uso — encerrando o processo anterior e reiniciando…" }
        stopLaunchAgent()
        val afterBootout = listeningPids(port).filter { it != self }
        afterBootout.forEach { pid ->
            if (isOurProcess(commandLine(pid))) {
                stopPid(pid)
            }
        }
        if (!waitUntilFree(port, self)) {
            listeningPids(port).filter { it != self }.forEach(::stopPid)
            waitUntilFree(port, self)
        }
    }

    internal fun isOurProcess(command: String): Boolean {
        if (command.isBlank()) return false
        val lower = command.lowercase()
        return ourMarkers.any { lower.contains(it.lowercase()) }
    }

    internal fun parseLsofPids(output: String): List<Long> =
        output.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.all(Char::isDigit) }
            .mapNotNull { it.toLongOrNull() }
            .distinct()
            .toList()

    private fun listeningPids(port: Int): List<Long> {
        val lsof = listOf("/usr/sbin/lsof", "/usr/bin/lsof", "lsof")
            .firstOrNull { path ->
                path == "lsof" || java.io.File(path).canExecute()
            } ?: return emptyList()
        val output = runCatching {
            val process = ProcessBuilder(lsof, "-nP", "-iTCP:$port", "-sTCP:LISTEN", "-t")
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText()
        }.getOrDefault("")
        return parseLsofPids(output)
    }

    private fun commandLine(pid: Long): String {
        val fromHandle = ProcessHandle.of(pid).orElse(null)?.info()?.let { info ->
            val cmd = info.command().orElse("")
            val args = info.arguments().orElse(emptyArray()).joinToString(" ")
            "$cmd $args"
        }.orEmpty()
        if (fromHandle.isNotBlank()) return fromHandle
        return runCatching {
            val process = ProcessBuilder("ps", "-p", pid.toString(), "-o", "command=")
                .redirectErrorStream(true)
                .start()
            process.waitFor(2, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText().trim()
        }.getOrDefault("")
    }

    private fun stopPid(pid: Long) {
        val handle = ProcessHandle.of(pid).orElse(null)
        if (handle != null) {
            handle.destroy()
            val gone = runCatching { handle.onExit().get(2, TimeUnit.SECONDS) }.isSuccess
            if (!gone && handle.isAlive) {
                handle.destroyForcibly()
                runCatching { handle.onExit().get(2, TimeUnit.SECONDS) }
            }
            return
        }
        ProcessBuilder("kill", "-TERM", pid.toString()).start().waitFor(1, TimeUnit.SECONDS)
        Thread.sleep(200)
        if (ProcessHandle.of(pid).isPresent) {
            ProcessBuilder("kill", "-KILL", pid.toString()).start().waitFor(1, TimeUnit.SECONDS)
        }
    }

    private fun stopLaunchAgent() {
        if (!isMac()) return
        val uid = runCatching {
            val process = ProcessBuilder("id", "-u").start()
            process.waitFor(1, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText().trim()
        }.getOrNull() ?: return
        ProcessBuilder("launchctl", "bootout", "gui/$uid/com.controldesk.agent")
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .start()
            .waitFor(3, TimeUnit.SECONDS)
    }

    private fun isLaunchAgentLoaded(): Boolean {
        if (!isMac()) return false
        val uid = runCatching {
            val process = ProcessBuilder("id", "-u").start()
            process.waitFor(1, TimeUnit.SECONDS)
            process.inputStream.bufferedReader().readText().trim()
        }.getOrNull() ?: return false
        val process = ProcessBuilder("launchctl", "print", "gui/$uid/com.controldesk.agent")
            .redirectErrorStream(true)
            .start()
        val finished = process.waitFor(2, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return false
        }
        return process.exitValue() == 0
    }

    private fun waitUntilFree(port: Int, self: Long): Boolean {
        repeat(25) {
            val others = listeningPids(port).filter { it != self }
            if (others.isEmpty()) return true
            Thread.sleep(200)
        }
        return listeningPids(port).none { it != self }
    }

    private fun isMac(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("mac")

    private fun isHeadless(): Boolean {
        if (System.getenv("CONTROL_DESK_HEADLESS")?.equals("1", ignoreCase = true) == true) return true
        if (System.getenv("CONTROL_DESK_HEADLESS")?.equals("true", ignoreCase = true) == true) return true
        return System.getProperty("control.desk.headless")?.equals("true", ignoreCase = true) == true
    }

    private fun isBundled(): Boolean {
        val path = AgentPort::class.java.protectionDomain.codeSource?.location?.path ?: return false
        return path.contains(".app/Contents")
    }
}
