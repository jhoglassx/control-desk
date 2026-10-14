package com.controldesk.agent

import com.controldesk.protocol.AppCloseRequest
import java.io.File

object MacAppQuit {
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
        if (matchingProcesses.isEmpty()) return

        if (shouldCloseWindowOnly(request) && pid != null) {
            closeWindow(request)
            MacWindowLister.refreshNow()
            return
        }

        MacWindowLister.suppress(app, ms = 2_000L)
        val self = ProcessHandle.current().pid()
        val targetProcesses = matchingProcesses.filter { it.pid() != self }
        targetProcesses.forEach { it.destroy() }
        if (targetProcesses.isEmpty() && pid == null) {
            Shell.spawn("/usr/bin/killall", "-TERM", app)
            if (exe != null && !exe.equals(app, ignoreCase = true)) {
                Shell.spawn("/usr/bin/killall", "-TERM", exe)
            }
        }
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

    internal fun shouldCloseWindowOnly(request: AppCloseRequest): Boolean {
        val windowCount = request.windowCount ?: return false
        return request.pid != null && windowCount > 1 &&
            (request.axWindowIndex != null || !request.windowTitle.isNullOrBlank())
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
