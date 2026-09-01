package com.controldesk.agent

import com.controldesk.logging.ControlDeskLog
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    ControlDeskLog.init()
    installUncaughtExceptionLogger()
    val port = System.getenv("CONTROL_DESK_PORT")?.toIntOrNull() ?: 8741
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    val bundled = isBundledApp()
    val skipInstall = isSkipInstall()
    val headless = isHeadlessMode(args)
    val openSettings = shouldOpenSettings(args, bundled, headless, skipInstall)
    val runServerHere = headless || !bundled || skipInstall
    if (headless) {
        System.setProperty("control.desk.headless", "true")
    }

    if (isMac) {
        System.setProperty("apple.awt.application.name", "Control Desk Agent")
        // Headless / LaunchAgent: sem ícone no Dock. A janela de PIN só aparece no processo de configurações.
        System.setProperty("apple.awt.UIElement", if (headless) "true" else "false")
        MacAccessibility.warnIfNeeded()
        MacWindowLister.warmUp()
        MacChromeTabs.warmUp()
        MacAppIconResolver.warmUp()
        MacFocusApp.warmUp()
    }

    if (!runServerHere) {
        if (isMac) {
            val ok = MacBackgroundService.ensureInstalledAndUpdated()
            if (!ok) {
                ControlDeskLog.agent.e { "Instalação falhou — veja ~/.control-desk/install.log" }
            }
        }
        if (openSettings) {
            launchAgentSettings(port, exitProcessOnClose = true)
        }
        return
    }

    if (openSettings) {
        Thread { runHeadlessService(port) }.start()
        Thread.sleep(250)
        ControlDeskLog.agent.i { "Abrindo janela de configurações…" }
        ControlDeskLog.agent.i { "Cmd+Q / Fechar encerra a janela e deixa o agente em segundo plano (sem Dock)." }
        launchAgentSettings(port, exitProcessOnClose = true)
        return
    }

    runHeadlessService(port)
}

private fun isBundledApp(): Boolean {
    val path = AgentPaths::class.java.protectionDomain.codeSource?.location?.path ?: return false
    return path.contains(".app/Contents")
}

private fun isSkipInstall(): Boolean {
    val env = System.getenv("CONTROL_DESK_SKIP_INSTALL") ?: return false
    return env.equals("1", ignoreCase = true) || env.equals("true", ignoreCase = true)
}

private fun isHeadlessMode(args: Array<String>): Boolean {
    if (args.any { it == "--headless" || it == "-headless" }) return true
    if (System.getenv("CONTROL_DESK_HEADLESS")?.equals("1", ignoreCase = true) == true) return true
    if (System.getenv("CONTROL_DESK_HEADLESS")?.equals("true", ignoreCase = true) == true) return true
    return System.getProperty("control.desk.headless")?.equals("true", ignoreCase = true) == true
}

private fun shouldOpenSettings(
    args: Array<String>,
    bundled: Boolean,
    headless: Boolean,
    skipInstall: Boolean,
): Boolean {
    if (headless) return false
    if (args.any { it == "--no-settings" || it == "-no-settings" }) return false
    if (args.any { it == "--settings" || it == "-settings" }) return true
    val env = System.getenv("CONTROL_DESK_OPEN_SETTINGS")
    if (env != null) {
        return env.equals("1", ignoreCase = true) || env.equals("true", ignoreCase = true)
    }
    // Packaged .app: install/update service and show PIN + permissions. Dev Gradle: same.
    return !skipInstall
}

private fun runHeadlessService(port: Int) {
    AgentPort.replaceOccupant(port)
    AgentKeepAlive.arm(port)
    val authStore = AgentAuthStore()
    val executor = NativePlatformExecutor()
    val server = AgentServer(port = port, authStore = authStore, executor = executor)
    val mdns = MdnsAnnouncer(port)

    server.start()
    runCatching { mdns.start() }.onFailure {
        ControlDeskLog.agent.w(it) { "mDNS unavailable: ${it.message}" }
    }

    val pin = authStore.currentPin() ?: authStore.activatePin()
    val hints = NetworkInfo.connectionHints(port)
    val logDir = java.io.File(System.getProperty("user.home"), ".control-desk")
    logDir.mkdirs()
    val logFile = java.io.File(logDir, "agent.log")
    logFile.appendText(
        buildString {
            appendLine("=== Control Desk Agent (headless) ===")
            appendLine("started=${System.currentTimeMillis()}")
            appendLine("port=$port")
            appendLine("pin=$pin")
            hints.forEach { appendLine(it) }
            appendLine("Acessibilidade: Ajustes → Privacidade → Acessibilidade → ControlDeskAgent")
            appendLine()
        },
    )
    ControlDeskLog.agent.i { "headless mode on port $port" }
    ControlDeskLog.agent.i { "PIN: $pin" }
    hints.forEach { ControlDeskLog.agent.i { it } }
    ControlDeskLog.agent.i { "log: ${logFile.absolutePath}" }

    Runtime.getRuntime().addShutdownHook(Thread { mdns.close() })

    runBlocking {
        awaitCancellation()
    }
}

private fun installUncaughtExceptionLogger() {
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
        ControlDeskLog.agent.e(error) { "uncaught on ${thread.name}" }
        previous?.uncaughtException(thread, error)
    }
}
