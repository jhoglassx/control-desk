package com.controldesk.app.panel

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PanelBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled, onBack)
}
