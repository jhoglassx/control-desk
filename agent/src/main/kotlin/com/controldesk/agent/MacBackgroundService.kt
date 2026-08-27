package com.controldesk.agent

import java.io.File
import java.util.concurrent.TimeUnit

object MacBackgroundService {
    private const val INSTALLED_APP = "/Applications/ControlDeskAgent.app"

    /**
     * Garante uma única instância do agente em /Applications + LaunchAgent.
     * Atualiza o app instalado quando a versão do bundle em execução é mais nova.
     */
    fun ensureInstalledAndUpdated(): Boolean {
        if (System.getenv("CONTROL_DESK_SKIP_INSTALL") == "1") {
            log("SKIP_INSTALL=1 — não altera instalação")
            return true
        }

        val script = locateInstallScript()
        if (script == null) {
            log("install-macos.sh não encontrado no bundle do app")
            notifyUser(
                title = "Control Desk Agent",
                text = "Não achei o instalador dentro do app. Use “Instalar em segundo plano.command” no DMG.",
                dialog = true,
            )
            return false
        }

        val sourceApp = locateSourceAppBundle()
        if (sourceApp == null) {
            log("Bundle do app não detectado — pulando instalação automática")
            notifyUser(
                title = "Control Desk Agent",
                text = "Não achei o ControlDeskAgent.app para copiar. Abra o DMG e use o comando de instalar.",
                dialog = true,
            )
            return false
        }

        val installed = File(INSTALLED_APP)
        if (installed.isDirectory &&
            sourceApp.canonicalFile == installed.canonicalFile &&
            serviceAlreadyRunning()
        ) {
            log("já em $INSTALLED_APP e porta 8741 ativa — nada a fazer")
            return true
        }

        log("Sincronizando instalação (fonte: ${sourceApp.absolutePath})")
        val ok = runScript(script, sourceApp)
        if (ok || serviceAlreadyRunning()) {
            if (!ok) {
                log("install-macos.sh falhou, mas o agente já responde na porta 8741 — ok")
            }
            notifyUser(
                title = "Control Desk Agent",
                text = "Instalado em segundo plano na porta 8741. PIN: grep pin= ~/.control-desk/agent.log",
                dialog = false,
            )
            return true
        }
        notifyUser(
            title = "Control Desk Agent",
            text = "A instalação falhou. Veja ~/.control-desk/install.log ou use “Instalar em segundo plano.command”.",
            dialog = true,
        )
        return false
    }

    private fun locateInstallScript(): File? {
        val candidates = listOf(
            System.getProperty("compose.application.resources.dir")
                ?.let { File(it, "install-macos.sh") },
            System.getProperty("compose.application.resources.dir")
                ?.let { File(it, "macos/install-macos.sh") },
            locateAppBundle()?.let { File(it, "Contents/app/resources/install-macos.sh") },
            locateAppBundle()?.let { File(it, "Contents/app/resources/macos/install-macos.sh") },
        )
        return candidates.firstOrNull { it?.isFile == true }
    }

    private fun locateSourceAppBundle(): File? = locateAppBundle()

    private fun locateAppBundle(): File? {
        val codeSource = MacBackgroundService::class.java.protectionDomain?.codeSource?.location?.path
        if (codeSource != null) {
            val path = java.net.URLDecoder.decode(codeSource, Charsets.UTF_8)
            val contents = File(path).parentFile?.parentFile
            if (contents?.name == "Contents" && contents.parentFile?.name?.endsWith(".app") == true) {
                return contents.parentFile
            }
        }
        val installed = File(INSTALLED_APP)
        return installed.takeIf { it.isDirectory }
    }

    private fun runScript(script: File, sourceApp: File): Boolean =
        runCatching {
            val process = ProcessBuilder("bash", script.absolutePath, sourceApp.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().readText()
            val finished = process.waitFor(120, TimeUnit.SECONDS)
            if (!finished || process.exitValue() != 0) {
                log("install-macos.sh exit=${if (finished) process.exitValue() else "timeout"}\n$output")
                false
            } else {
                log("install-macos.sh OK\n$output")
                true
            }
        }.getOrElse { e ->
            log("install-macos.sh erro: ${e.message}")
            false
        }

    private fun serviceAlreadyRunning(): Boolean =
        runCatching {
            val connection = java.net.URI("http://127.0.0.1:8741/health").toURL().openConnection()
            connection.connectTimeout = 400
            connection.readTimeout = 400
            connection.getInputStream().bufferedReader().readText().contains("\"ok\":true")
        }.getOrDefault(false)

    private fun notifyUser(title: String, text: String, dialog: Boolean) {
        if (!System.getProperty("os.name").orEmpty().lowercase().contains("mac")) return
        val script = if (dialog) {
            """display dialog ${appleString(text)} with title ${appleString(title)} buttons {"OK"} default button 1"""
        } else {
            """display notification ${appleString(text)} with title ${appleString(title)}"""
        }
        runCatching {
            ProcessBuilder("osascript", "-e", script).start()
        }
    }

    private fun appleString(value: String): String =
        "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun log(message: String) {
        val logFile = File(System.getProperty("user.home"), ".control-desk/install.log")
        logFile.parentFile?.mkdirs()
        logFile.appendText("${System.currentTimeMillis()} $message\n")
    }
}
