package com.controldesk.agent

import java.io.File

object MacAppQuit {
    private val blocked = setOf(
        "Finder", "Dock", "SystemUIServer", "Window Server", "Control Center",
        "NotificationCenter", "loginwindow", "Wallpaper", "Spotlight",
        "ControlDeskAgent", "Control Desk Agent",
    )
    private val helpers = listOf(" helper", "/helper", "crashpad", "renderer", "gpu", "plugin-container")

    fun quit(appName: String) {
        val app = appName.trim()
        if (app.isEmpty() || app in blocked) return
        MacWindowLister.suppress(app, ms = 2_000L)
        val bundle = MacAppIconResolver.pathFor(app)
        val exe = bundle?.let { mainExecutable(it) }
        val self = ProcessHandle.current().pid()
        var signaled = 0
        ProcessHandle.allProcesses().forEach { handle ->
            if (handle.pid() == self) return@forEach
            val command = handle.info().command().orElse("")
            if (!matchesMainApp(command, app, bundle, exe)) return@forEach
            if (handle.destroy()) signaled += 1
        }
        if (signaled == 0) {
            Shell.spawn("/usr/bin/killall", "-TERM", app)
            if (exe != null && !exe.equals(app, ignoreCase = true)) {
                Shell.spawn("/usr/bin/killall", "-TERM", exe)
            }
        }
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
