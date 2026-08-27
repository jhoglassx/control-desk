package com.controldesk.domain.usecase

import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.AgentGateway

class ExecuteGridButtonUseCase(
    private val gateway: AgentGateway,
) {
    suspend operator fun invoke(button: GridButton, host: PairedHost) {
        if (host.token.isBlank()) {
            throw ControlDeskError.Unauthorized()
        }
        when (button.actionType) {
            ActionType.REMOTE_LAUNCH -> {
                requirePayload(button, "target")
                gateway.launchApp(host, button)
            }
            ActionType.REMOTE_CLOSE -> {
                requirePayload(button, "target")
                gateway.closeApp(host, button)
            }
            ActionType.REMOTE_BROWSER_TAB -> gateway.switchBrowserTab(host, button)
            ActionType.REMOTE_BROWSER_OPEN_SITE -> {
                val url = requirePayload(button, "url")
                gateway.openBrowser(host, url)
            }
            ActionType.REMOTE_BROWSER_CLOSE_APP -> gateway.closeBrowser(host)
            ActionType.REMOTE_VOLUME -> {
                val mute = button.payload["mute"]?.toBooleanStrictOrNull()
                val delta = button.payload["delta"]?.toIntOrNull()
                val level = button.payload["level"]?.toIntOrNull()
                if (mute == null && delta == null && level == null) {
                    throw ControlDeskError.Generic("Payload de volume precisa de delta, level ou mute")
                }
                gateway.setVolume(host, level = level, delta = delta, mute = mute)
            }
            ActionType.REMOTE_MIC_MUTE -> {
                val mute = button.payload["mute"]?.toBooleanStrictOrNull()
                val toggle = button.payload["toggle"]?.toBooleanStrictOrNull() ?: true
                gateway.setMic(host, mute = mute, toggle = mute == null && toggle)
            }
            ActionType.OPEN_URL -> throw ControlDeskError.Generic("OPEN_URL é local only")
        }
    }

    private fun requirePayload(button: GridButton, key: String): String {
        return button.payload[key]?.takeIf { it.isNotBlank() }
            ?: throw ControlDeskError.Generic("Payload obrigatório ausente: $key")
    }
}
