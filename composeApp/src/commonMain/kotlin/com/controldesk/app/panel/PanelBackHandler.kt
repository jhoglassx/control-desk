package com.controldesk.app.panel

import androidx.compose.runtime.Composable

@Composable
expect fun PanelBackHandler(enabled: Boolean, onBack: () -> Unit)
