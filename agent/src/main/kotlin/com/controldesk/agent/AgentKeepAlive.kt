package com.controldesk.agent

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hands the HTTP agent off to a true background process when the GUI/Gradle JVM exits.
 *
 * Cmd+Q / Fechar / Ctrl+C kill this process (so the Dock icon goes away). The shutdown
 * hook starts a detached `--headless` child with `apple.awt.UIElement=true`, or kickstarts
 * the LaunchAgent when this is the packaged .app. Production Macs should use
 * `:agent:installMacos` / DMG so launchd owns the service across reboot.
 *
 * Disable with `CONTROL_DESK_NO_RESPAWN=1`. Headless / LaunchAgent processes never respawn
 * (launchd KeepAlive already owns that).
 */
object AgentKeepAlive {
    const val MAIN_CLASS = "com.controldesk.agent.AgentMainKt"

    private val armed = AtomicBoolean(false)

    fun arm(port: Int) {
        if (!shouldRespawn()) return
        if (!armed.compareAndSet(false, true)) return
        Runtime.getRuntime().addShutdownHook(
            Thread({
                runCatching { respawn(port) }.onFailure { error ->
                    log("respawn falhou: ${error.message}")
                }
            }, "control-desk-keep-alive"),
        )
    }

    internal fun shouldRespawn(
        headless: Boolean = isHeadless(),
        noRespawn: Boolean = isNoRespawn(),
    ): Boolean = !headless && !noRespawn

    internal fun javaBinary(): String {
        val fromHandle = ProcessHandle.current().info().command().orElse("")
        if (fromHandle.isNotBlank() && File(fromHandle).canExecute()) return fromHandle
        val home = System.getProperty("java.home") ?: return "java"
        val name = if (isWindows()) "java.exe" else "java"
        return File(home, "bin/$name").absolutePath
    }

    internal fun posixDetachScript(
        java: String,
        classpath: String,
        port: Int,
        logFile: File,
    ): String {
        val quotedJava = shellQuote(java)
        val quotedCp = shellQuote(classpath)
        val quotedLog = shellQuote(logFile.absolutePath)
        val quotedMain = shellQuote(MAIN_CLASS)
        return """
            (
              for i in 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 16 17 18 19 20 21 22 23 24 25; do
                if ! /usr/sbin/lsof -nP -iTCP:$port -sTCP:LISTEN >/dev/null 2>&1; then
                  break
                fi
                sleep 0.2
              done
              CONTROL_DESK_HEADLESS=1 CONTROL_DESK_PORT=$port \
                exec $quotedJava -cp $quotedCp -Dcontrol.desk.headless=true -Dapple.awt.UIElement=true $quotedMain --headless
            ) >> $quotedLog 2>&1 < /dev/null &
        """.trimIndent()
    }

    internal fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\"'\"'") + "'"

    internal fun shouldPreferLaunchAgent(
        bundled: Boolean,
        installedAppExists: Boolean,
        windows: Boolean = isWindows(),
    ): Boolean = !windows && bundled && installedAppExists

    private fun respawn(port: Int) {
        val installed = File("/Applications/ControlDeskAgent.app").isDirectory
        if (shouldPreferLaunchAgent(bundled = isBundledProcess(), installedAppExists = installed)) {
            log("encerrando foreground — relançando LaunchAgent com.controldesk.agent")
            kickstartLaunchAgent()
            return
        }
        val java = javaBinary()
        val classpath = System.getProperty("java.class.path").orEmpty()
        if (classpath.isBlank()) {
            log("respawn abortado: java.class.path vazio")
            return
        }
        val logFile = File(File(System.getProperty("user.home"), ".control-desk"), "agent-daemon.log")
        logFile.parentFile?.mkdirs()
        log("encerrando foreground — respawn headless na porta $port")
        if (isWindows()) {
            spawnWindows(java, classpath, port, logFile)
        } else {
            spawnPosix(java, classpath, port, logFile)
        }
    }

    private fun spawnPosix(java: String, classpath: String, port: Int, logFile: File) {
        val script = posixDetachScript(java, classpath, port, logFile)
        ProcessBuilder("/bin/bash", "-c", script)
            .directory(File(System.getProperty("user.dir") ?: System.getProperty("user.home")))
            .start()
    }

    private fun spawnWindows(java: String, classpath: String, port: Int, logFile: File) {
        ProcessBuilder(
            "cmd", "/c", "start", "ControlDeskAgent", "/b",
            java, "-cp", classpath,
            "-Dcontrol.desk.headless=true",
            MAIN_CLASS, "--headless",
        )
            .directory(File(System.getProperty("user.dir") ?: System.getProperty("user.home")))
            .redirectOutput(ProcessBuilder.Redirect.appendTo(logFile))
            .redirectError(ProcessBuilder.Redirect.appendTo(logFile))
            .apply {
                environment()["CONTROL_DESK_HEADLESS"] = "1"
                environment()["CONTROL_DESK_PORT"] = port.toString()
            }
            .start()
    }

    private fun kickstartLaunchAgent() {
        val plist = File(System.getProperty("user.home"), "Library/LaunchAgents/com.controldesk.agent.plist")
        val script = """
            uid="${'$'}(id -u)"
            sleep 1
            launchctl kickstart -k "gui/${'$'}uid/com.controldesk.agent" >/dev/null 2>&1 || \
              launchctl bootstrap "gui/${'$'}uid" ${shellQuote(plist.absolutePath)} >/dev/null 2>&1 || true
        """.trimIndent()
        ProcessBuilder("/bin/bash", "-c", script).start()
    }

    private fun isBundledProcess(): Boolean {
        val path = AgentKeepAlive::class.java.protectionDomain.codeSource?.location?.path ?: return false
        return path.contains(".app/Contents")
    }

    private fun isHeadless(): Boolean {
        if (System.getenv("CONTROL_DESK_HEADLESS")?.equals("1", ignoreCase = true) == true) return true
        if (System.getenv("CONTROL_DESK_HEADLESS")?.equals("true", ignoreCase = true) == true) return true
        return System.getProperty("control.desk.headless")?.equals("true", ignoreCase = true) == true
    }

    private fun isNoRespawn(): Boolean {
        val env = System.getenv("CONTROL_DESK_NO_RESPAWN") ?: return false
        return env.equals("1", ignoreCase = true) || env.equals("true", ignoreCase = true)
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").orEmpty().lowercase().contains("win")

    private fun log(message: String) {
        val logFile = File(File(System.getProperty("user.home"), ".control-desk"), "agent.log")
        runCatching {
            logFile.parentFile?.mkdirs()
            logFile.appendText("${System.currentTimeMillis()} [keep-alive] $message\n")
        }
        println("[agent] $message")
    }
}
