package com.controldesk.agent

object MacAccessibility {
    fun warnIfNeeded() {
        val trusted = Shell.osascript(
            """
            tell application "System Events"
                return UI elements enabled
            end tell
            """.trimIndent(),
            timeoutMs = 800,
        ).equals("true", ignoreCase = true)
        if (!trusted) {
            println("[agent] Acessibilidade desligada. Ajustes → Privacidade e Segurança → Acessibilidade → ControlDeskAgent (ou java/Terminal em dev).")
        }
    }
}
