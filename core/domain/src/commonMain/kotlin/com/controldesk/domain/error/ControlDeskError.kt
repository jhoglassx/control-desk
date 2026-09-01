package com.controldesk.domain.error

sealed class ControlDeskError(message: String) : Exception(message) {
    class Unauthorized(message: String = SESSION_EXPIRED) : ControlDeskError(message)
    class Offline(message: String = AGENT_OFFLINE) : ControlDeskError(message)
    class Generic(message: String) : ControlDeskError(message)

    companion object {
        const val SESSION_EXPIRED =
            "Sessão expirada. Vá em + Parear, use o PIN do agente e pareie o PC de novo."
        const val AGENT_OFFLINE =
            "Agente offline. Inicie no Mac: ./gradlew :agent:run"
        const val EMPTY_WINDOWS =
            "Nenhuma janela detectada. No Mac, permita Acessibilidade para o agente (foco nas janelas)."
        const val ACCESSIBILITY_OFF =
            "No Mac, ative Acessibilidade para ControlDeskAgent (Ajustes → Privacidade e Segurança)."
    }
}
