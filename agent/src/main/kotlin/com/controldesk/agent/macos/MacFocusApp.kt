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
        if (app.isEmpty()) return
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
}
