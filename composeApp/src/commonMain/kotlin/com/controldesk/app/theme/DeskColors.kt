package com.controldesk.app.theme

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

object DeskColors {
    val Background = Color(0xFF0B0F14)
    val Surface = Color(0xFF151B24)
    val SurfaceRaised = Color(0xFF1C2430)
    val Stroke = Color(0xFF2A3443)
    val TextPrimary = Color(0xFFF2F5F8)
    val TextMuted = Color(0xFF9AA6B5)
    val Accent = Color(0xFF3B82F6)
    val AccentSoft = Color(0xFF1E3A8A)
    val Mint = Color(0xFF34D399)
    val MintSoft = Color(0xFF064E3B)
    val Danger = Color(0xFFEF4444)
    val DangerSoft = Color(0xFF7F1D1D)
    val Warning = Color(0xFFFBBF24)
    val Live = Color(0xFF22C55E)
    val Idle = Color(0xFF64748B)

    val ScreenGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0B0F14), Color(0xFF121A24), Color(0xFF0D1117)),
        start = Offset.Zero,
        end = Offset(900f, 1600f),
    )
}

object DeskDesign {
    /** Stream Deck MK.2 key size (72×72 px artwork). */
    const val TileSize = 72
    const val TileGap = 8
    const val HorizontalPad = 16
    /** Icon area inside a tile (~70% of tile). */
    const val IconSize = 50
    const val IconCornerRadius = 10
}
