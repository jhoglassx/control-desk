package com.controldesk.agent

import com.controldesk.protocol.AppLaunchRequest

object MacFocusApp {
    fun warmUp() {
        Thread({ Shell.run("/usr/bin/osascript", "-e", "return 1", timeoutMs = 500) }, "focus-warmup").apply {
            isDaemon = true
            start()
        }
    }

    fun focus(request: AppLaunchRequest) {
        val app = request.target.trim()
        val bundlePath = request.bundlePath?.trim().orEmpty()
        if (app.isEmpty() && bundlePath.isEmpty()) return
        val pid = request.pid?.takeIf { it > 0 }
        val resolved = request.stableWindowId?.takeIf { request.projectPath.isNullOrBlank() }
            ?.let { MacWindowTarget.resolve(it, pid) }
        val axIndex = (resolved?.axWindowIndex ?: request.axWindowIndex)?.takeIf { it > 0 }
        val windowTitle = (resolved?.windowTitle ?: request.windowTitle)?.takeIf { it.isNotBlank() }
        if (shouldFocusExistingProcess(request)) {
            val result = Shell.osascript(focusWindowScript(requireNotNull(pid), axIndex, windowTitle), timeoutMs = 1_500)
            if (result != "no_windows" || resolved != null) {
                check(result == "focused") { result.ifBlank { "O macOS não confirmou o foco da janela" } }
                return
            }
        }
        if (bundlePath.isNotEmpty()) {
            val appFile = java.io.File(bundlePath)
            require(appFile.isDirectory && appFile.extension.equals("app", ignoreCase = true)) {
                "O aplicativo selecionado não está disponível neste Mac."
            }
            val projectPath = request.projectPath?.trim()?.takeIf { it.isNotEmpty() }
            if (projectPath != null) {
                val project = java.io.File(projectPath)
                require(project.exists()) { "O projeto ou documento não foi encontrado: $projectPath" }
                Shell.spawn("/usr/bin/open", "-a", bundlePath, projectPath)
            } else {
                Shell.spawn("/usr/bin/open", "-a", bundlePath)
            }
            return
        }
        val title = request.windowTitle?.trim().orEmpty()
        if (title.isNotEmpty()) {
            Shell.osascript(
                """
                tell application "System Events"
                    tell process "${Shell.escape(app)}"
                        set frontmost to true
                        try
                            perform action "AXRaise" of (first window whose name contains "${Shell.escape(title)}")
                        end try
                    end tell
                end tell
                """.trimIndent(),
                timeoutMs = 400,
            )
        }
        Shell.spawn("/usr/bin/open", "-a", app)
    }

    internal fun focusWindowScript(pid: Int, index: Int?, title: String?): String {
        val safeTitle = title?.takeIf(String::isNotBlank)?.let(Shell::escape)
        val selection = when {
            index != null -> buildString {
                append("set targetWindow to window $index of targetProcess")
                if (safeTitle != null) append("\nif name of targetWindow is not \"$safeTitle\" then error \"A janela mudou; atualize a lista\"")
            }
            safeTitle != null -> """
                set matchingWindows to (every window of targetProcess whose name is "$safeTitle")
                if (count of matchingWindows) is not 1 then error "Janela não encontrada ou título ambíguo"
                set targetWindow to item 1 of matchingWindows
            """.trimIndent()
            else -> ""
        }
        val raise = if (selection.isBlank()) "" else """
            $selection
            perform action "AXRaise" of targetWindow
        """.trimIndent()
        return """
            tell application "System Events"
                set targetProcess to first application process whose unix id is $pid
                if (count of windows of targetProcess) is 0 then return "no_windows"
                set frontmost of targetProcess to true
                $raise
                return "focused"
            end tell
        """.trimIndent()
    }

    internal fun shouldFocusExistingProcess(request: AppLaunchRequest): Boolean =
        request.pid?.let { it > 0 } == true && request.projectPath.isNullOrBlank() &&
            (!request.stableWindowId.isNullOrBlank() || !request.windowTitle.isNullOrBlank() ||
                (request.axWindowIndex ?: 0) > 0)
}
