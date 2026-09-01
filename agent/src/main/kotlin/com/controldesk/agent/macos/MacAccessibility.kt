package com.controldesk.agent

import com.controldesk.logging.ControlDeskLog

object MacAccessibility {
    fun isTrusted(): Boolean = Shell.osascript(
        """
        tell application "System Events"
            return UI elements enabled
        end tell
        """.trimIndent(),
        timeoutMs = 800,
    ).equals("true", ignoreCase = true)

    fun warnIfNeeded() {
        if (!isTrusted()) {
            val message =
                "Acessibilidade desligada. Ajustes → Privacidade e Segurança → Acessibilidade → ControlDeskAgent (ou java/Terminal em dev)."
            ControlDeskLog.agent.w { message }
        }
    }

    fun openPrivacySettings() {
        Shell.run(
            "/usr/bin/open",
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility",
            timeoutMs = 2_000,
        )
    }
}
