package com.controldesk.agent

import com.controldesk.protocol.AppCloseRequest
import java.io.File

object MacAppQuit {
    internal enum class CloseAction { CLOSE_WINDOW, TERMINATE_PROCESS, IGNORE }

    private val blocked = setOf(
        "Finder", "Dock", "SystemUIServer", "Window Server", "Control Center",
        "NotificationCenter", "loginwindow", "Wallpaper", "Spotlight",
        "ControlDeskAgent", "Control Desk Agent",
    )
    private val helpers = listOf(" helper", "/helper", "crashpad", "renderer", "gpu", "plugin-container")

    fun quit(incoming: AppCloseRequest) {
        val request = incoming.stableWindowId?.let { id ->
            val window = MacWindowTarget.resolve(id, incoming.pid)
            incoming.copy(target = window.appName, axWindowIndex = window.axWindowIndex, windowTitle = window.windowTitle,
                windowCount = window.windowCount)
        } ?: incoming
        val app = request.target.trim()
        if (app.isEmpty() || app in blocked) return
        val bundle = MacAppIconResolver.pathFor(app)
        val exe = bundle?.let { mainExecutable(it) }
        val pid = request.pid?.takeIf { it > 0 }?.toLong()
        val matchingProcesses = ProcessHandle.allProcesses().use { processes ->
            processes.filter { handle ->
                if (pid != null) handle.pid() == pid && isMainProcess(handle, app, bundle, exe)
                else isMainProcess(handle, app, bundle, exe)
            }.toList()
        }
        val action = closeAction(request, matchingProcesses.size)
        check(action != CloseAction.IGNORE) { "Não foi possível identificar a janela para fechar. Atualize a lista." }
        if (action == CloseAction.CLOSE_WINDOW) {
            closeWindow(request)
            MacWindowLister.refreshNow()
            return
        }

        val self = ProcessHandle.current().pid()
        val targetProcesses = matchingProcesses.filter { it.pid() != self }
        targetProcesses.forEach { it.destroy() }
        MacWindowLister.refreshNow()
    }

    private fun isMainProcess(handle: ProcessHandle, app: String, bundle: String?, exe: String?): Boolean {
        val command = handle.info().command().orElse("")
        return matchesMainApp(command, app, bundle, exe)
    }

    private fun closeWindow(request: AppCloseRequest) {
        val index = request.axWindowIndex?.takeIf { it > 0 }
        val title = request.windowTitle?.trim()?.takeIf { it.isNotEmpty() }
        require(index != null || title != null) { "A janela não tem identificação para ser fechada" }
        val pid = requireNotNull(request.pid?.takeIf { it > 0 }) { "Não foi possível identificar o processo da janela" }
        val script = closeWindowScript(pid, index, title, resolvedById = request.stableWindowId != null)
        val result = Shell.osascript(script, timeoutMs = 3_000)
        check(result == CLOSE_WINDOW_SUCCESS) {
            result.ifBlank { "O macOS não confirmou o fechamento da janela" }
        }
    }

    internal fun closeWindowScript(pid: Int, index: Int?, title: String?, resolvedById: Boolean = false): String {
        val safeTitle = title?.takeIf { it.isNotBlank() }
            ?.replace('\n', ' ')
            ?.replace('\r', ' ')
            ?.let(Shell::escape)
        val selectWindow = when {
            resolvedById && index != null && index > 0 -> buildString {
                append("set targetWindow to window $index of targetProcess")
                if (safeTitle != null) append("\nif name of targetWindow is not \"$safeTitle\" then error \"A janela mudou; atualize a lista\"")
            }
            safeTitle != null -> """
                set matchingWindows to (every window of targetProcess whose name is "$safeTitle")
                if (count of matchingWindows) is not 1 then error "Janela não encontrada ou título ambíguo"
                set targetWindow to item 1 of matchingWindows
            """.trimIndent()
            index != null && index > 0 -> "set targetWindow to window $index of targetProcess"
            else -> error("A janela não tem identificação para ser fechada")
        }
        return """
            tell application "System Events"
                set targetProcess to first application process whose unix id is $pid
                $selectWindow
                click (first button of targetWindow whose subrole is "AXCloseButton")
                return "$CLOSE_WINDOW_SUCCESS"
            end tell
        """.trimIndent()
    }

    private const val CLOSE_WINDOW_SUCCESS = "closed"

    internal fun matchesMainApp(command: String, app: String, bundle: String?, exe: String?): Boolean {
        if (command.isBlank()) return false
        val lower = command.lowercase()
        if (helpers.any { lower.contains(it) }) return false
        if (bundle != null && command.startsWith("$bundle/Contents/MacOS/")) {
            val leaf = command.substringAfterLast('/')
            return exe == null ||
                leaf.equals(exe, ignoreCase = true) ||
                leaf.equals(app, ignoreCase = true)
        }
        val leaf = command.substringAfterLast('/')
        return leaf.equals(app, ignoreCase = true) ||
            (exe != null && leaf.equals(exe, ignoreCase = true))
    }

    internal fun closeAction(request: AppCloseRequest, matchingProcessCount: Int): CloseAction {
        if (matchingProcessCount <= 0) return CloseAction.IGNORE
        val hasWindowIdentity = request.axWindowIndex?.let { it > 0 } == true ||
            !request.windowTitle.isNullOrBlank()
        val windowCount = request.windowCount

        if (request.pid?.let { it > 0 } == true) {
            return when {
                hasWindowIdentity -> CloseAction.CLOSE_WINDOW
                else -> CloseAction.IGNORE
            }
        }

        return if (matchingProcessCount == 1 && windowCount == 1) {
            CloseAction.TERMINATE_PROCESS
        } else {
            CloseAction.IGNORE
        }
    }

    private fun mainExecutable(bundlePath: String): String? {
        val macos = File(bundlePath, "Contents/MacOS")
        if (!macos.isDirectory) return null
        val byName = File(macos, File(bundlePath).name.removeSuffix(".app"))
        if (byName.isFile) return byName.name
        return macos.listFiles()
            ?.firstOrNull { it.isFile && !it.name.contains("Helper", ignoreCase = true) }
            ?.name
    }
}
