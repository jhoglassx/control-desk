package com.controldesk.agent

import java.io.File
import java.util.concurrent.TimeUnit

object MacBackgroundService {
    private const val LABEL = "com.controldesk.agent"
    private const val INSTALLED_APP = "/Applications/ControlDeskAgent.app"

    /**
     * Garante uma única instância do agente em /Applications + LaunchAgent.
     * Atualiza o app instalado quando a versão do bundle em execução é mais nova.
     */
    fun ensureInstalledAndUpdated() {
        if (System.getenv("CONTROL_DESK_SKIP_INSTALL") == "1") {
            log("SKIP_INSTALL=1 — não altera instalação")
            return
        }

        val script = locateInstallScript()
        if (script == null) {
            log("install-macos.sh não encontrado no bundle do app")
            return
        }

        val sourceApp = locateSourceAppBundle()
        if (sourceApp == null) {
            log("Bundle do app não detectado — pulando instalação automática")
            return
        }

        log("Sincronizando instalação (fonte: ${sourceApp.absolutePath})")
        val ok = runScript(script, sourceApp)
        if (!ok) {
            log("Falha ao executar install-macos.sh — tente: Instalar em segundo plano.command")
        }
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
            val path = codeSource.replace("%20", " ")
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
                log("install-macos.sh exit=${process.exitValue()}\n$output")
                false
            } else {
                log("install-macos.sh OK\n$output")
                true
            }
        }.getOrElse { e ->
            log("install-macos.sh erro: ${e.message}")
            false
        }

    private fun log(message: String) {
        val logFile = File(System.getProperty("user.home"), ".control-desk/install.log")
        logFile.parentFile?.mkdirs()
        logFile.appendText("${System.currentTimeMillis()} $message\n")
    }
}
