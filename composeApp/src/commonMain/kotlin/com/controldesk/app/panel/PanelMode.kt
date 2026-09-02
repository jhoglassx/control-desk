package com.controldesk.app.panel

import androidx.compose.runtime.staticCompositionLocalOf

data class PanelModeConfig(
    val enabled: Boolean = false,
    val isDefaultLauncher: Boolean = false,
)

val LocalPanelMode = staticCompositionLocalOf { PanelModeConfig() }

/** PIN padrão para acessar configurações no modo painel dedicado. */
const val PANEL_ADMIN_PIN = "8741"
