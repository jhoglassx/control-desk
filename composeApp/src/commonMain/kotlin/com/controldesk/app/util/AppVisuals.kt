package com.controldesk.app.util

import androidx.compose.ui.graphics.Color
import com.controldesk.app.theme.DeskColors
import com.controldesk.domain.model.GridButton

fun GridButton.tileLabel(): String {
    val cleaned = label.replace(" · min", "", ignoreCase = true)
    val parts = cleaned.split(" · ", limit = 2)
    val appName = parts[0].ifBlank { cleaned }
    val title = parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    return title ?: appName
}

fun GridButton.tileAppName(): String {
    val cleaned = label.replace(" · min", "", ignoreCase = true)
    return cleaned.substringBefore(" · ").ifBlank { cleaned }
}

fun monogramForLabel(label: String): String {
    val name = label.trim().substringBefore(" · ").substringBefore(" - ")
    val letter = name.firstOrNull { it.isLetterOrDigit() } ?: '?'
    return letter.uppercaseChar().toString()
}

fun accentForLabel(label: String): Color {
    val palette = listOf(
        DeskColors.Accent,
        DeskColors.Mint,
        Color(0xFFA78BFA),
        Color(0xFFF59E0B),
        Color(0xFF22D3EE),
    )
    return palette[label.hashCode().and(0x7fffffff) % palette.size]
}
