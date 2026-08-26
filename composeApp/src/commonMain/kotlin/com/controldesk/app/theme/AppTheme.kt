package com.controldesk.app.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ControlDeskScheme = darkColorScheme(
    primary = DeskColors.Accent,
    onPrimary = DeskColors.TextPrimary,
    secondary = DeskColors.Mint,
    background = DeskColors.Background,
    surface = DeskColors.Surface,
    onBackground = DeskColors.TextPrimary,
    onSurface = DeskColors.TextPrimary,
    error = DeskColors.Danger,
)

@Composable
fun ControlDeskTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ControlDeskScheme,
        content = content,
    )
}
