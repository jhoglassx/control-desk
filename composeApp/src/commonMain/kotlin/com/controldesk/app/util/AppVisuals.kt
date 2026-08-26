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

fun emojiForLabel(label: String): String {
    val key = label.lowercase()
    return when {
        "chrome" in key -> "🌐"
        "safari" in key -> "🧭"
        "firefox" in key -> "🦊"
        "code" in key || "studio" in key || "cursor" in key -> "💻"
        "term" in key || "iterm" in key -> "⬛"
        "slack" in key || "teams" in key -> "💬"
        "mail" in key || "outlook" in key -> "✉️"
        "note" in key || "obsidian" in key -> "📝"
        "figma" in key || "sketch" in key -> "🎨"
        "spot" in key || "music" in key -> "🎵"
        "finder" in key || "explorer" in key -> "📁"
        else -> listOf("🚀", "⚙️", "📦", "🧩", "🖥️")[label.hashCode().and(0x7fffffff) % 5]
    }
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
