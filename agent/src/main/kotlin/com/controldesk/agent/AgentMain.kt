package com.controldesk.agent

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking

fun main(args: Array<String>) {
    val port = System.getenv("CONTROL_DESK_PORT")?.toIntOrNull() ?: 8741
    val isMac = System.getProperty("os.name").lowercase().contains("mac")
    val bundled = isBundledApp()
    val skipInstall = isSkipInstall()
    val headless = isHeadlessMode(args)
    val openSettings = shouldOpenSettings(args, bundled, headless, skipInstall)
    val runServerHere = headless || !bundled || skipInstall

    if (isMac) {
        System.setProperty("apple.awt.application.name", "Control Desk Agent")
        System.setProperty("apple.awt.UIElement", "false")
        MacAccessibility.warnIfNeeded()
        MacWindowLister.warmUp()
        MacChromeTabs.warmUp()
        MacAppIconResolver.warmUp()
        MacFocusApp.warmUp()
    }

    if (!runServerHere) {
        if (isMac) {
            MacBackgroundService.ensureInstalledAndUpdated()
        }
        if (openSettings) {
            launchAgentSettings(port, exitProcessOnClose = true)
        } else {
            println(
                "[agent] Serviço em segundo plano instalado/atualizado. " +
                    "Abra 'Abrir configurações.command' ou use --settings para ver o PIN.",
            )
        }
        return
    }

    if (openSettings) {
        Thread { runHeadlessService(port) }.start()
        Thread.sleep(250)
        println("[agent] Abrindo janela de configurações…")
        launchAgentSettings(port, exitProcessOnClose = false)
        println("[agent] Janela fechada. Serviço ainda ativo na porta $port. Ctrl+C para parar.")
        runBlocking { awaitCancellation() }
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
    if (args.any { it == "--settings" || it == "-settings" }) return true
    val env = System.getenv("CONTROL_DESK_OPEN_SETTINGS")
    if (env != null) {
        return env.equals("1", ignoreCase = true) || env.equals("true", ignoreCase = true)
    }
    // Gradle / java -jar / SKIP_INSTALL: show PIN/IP. Packaged .app stays silent unless --settings.
    return !bundled || skipInstall
}

private fun runHeadlessService(port: Int) {
    val authStore = AgentAuthStore()
    val executor = NativePlatformExecutor()
    val server = AgentServer(port = port, authStore = authStore, executor = executor)
    val mdns = MdnsAnnouncer(port)

    server.start()
    runCatching { mdns.start() }.onFailure {
        println("[agent] mDNS unavailable: ${it.message}")
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
    println("[agent] headless mode on port $port")
    println("[agent] PIN: $pin")
    hints.forEach { println("[agent] $it") }
    println("[agent] log: ${logFile.absolutePath}")

    Runtime.getRuntime().addShutdownHook(Thread { mdns.close() })

    runBlocking {
        awaitCancellation()
    }
}
