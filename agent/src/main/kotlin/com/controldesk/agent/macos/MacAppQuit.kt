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

    fun quit(request: AppCloseRequest) {
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
        if (action == CloseAction.IGNORE) return
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
        val title = request.windowTitle.orEmpty()
        if (index == null && title.isBlank()) return
        val selector = index?.let { "window $it" }
            ?: "first window whose name is \"${Shell.escape(title)}\""
        Shell.osascript(
            """
            tell application "System Events"
                tell (first application process whose unix id is ${request.pid})
                    try
                        close $selector
                    end try
                end tell
            end tell
            """.trimIndent(),
            timeoutMs = 1_000,
        )
    }

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
                windowCount != null && windowCount > 1 ->
                    if (hasWindowIdentity) CloseAction.CLOSE_WINDOW else CloseAction.IGNORE
                windowCount == 1 -> CloseAction.TERMINATE_PROCESS
                windowCount == null && hasWindowIdentity -> CloseAction.CLOSE_WINDOW
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
