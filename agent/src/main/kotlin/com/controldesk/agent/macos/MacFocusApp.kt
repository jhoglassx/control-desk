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
        val axIndex = request.axWindowIndex?.takeIf { it > 0 }
        val windowTitle = request.windowTitle?.takeIf { it.isNotBlank() }
        if (shouldFocusExistingProcess(request)) {
            val windowSelector = when {
                axIndex != null -> "window $axIndex"
                windowTitle != null -> "first window whose name is \"${Shell.escape(windowTitle)}\""
                else -> null
            }
            val raiseWindow = windowSelector?.let { selector ->
                "try\n    perform action \"AXRaise\" of $selector\nend try"
            }.orEmpty()
            Shell.osascript(
                """
                tell application "System Events"
                    tell (first application process whose unix id is $pid)
                        set frontmost to true
                        $raiseWindow
                    end tell
                end tell
                """.trimIndent(),
                timeoutMs = 700,
            )
            return
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
            val raised = Shell.osascript(
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
            if (raised.isNotBlank() || true) {
                // AXRaise is best-effort; always also activate via Launch Services.
            }
        }
        Shell.spawn("/usr/bin/open", "-a", app)
    }

    internal fun shouldFocusExistingProcess(request: AppLaunchRequest): Boolean =
        request.pid?.let { it > 0 } == true
}
