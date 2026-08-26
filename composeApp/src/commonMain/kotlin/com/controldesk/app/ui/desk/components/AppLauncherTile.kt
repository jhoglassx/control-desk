package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.util.accentForLabel
import com.controldesk.app.util.emojiForLabel
import com.controldesk.app.util.rememberBase64Image
import com.controldesk.app.util.tileLabel
import com.controldesk.app.util.tileSubtitle
import com.controldesk.domain.model.GridButton

@Composable
fun AppLauncherTile(
    button: GridButton,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val borderColor = when {
        button.isActive -> DeskColors.Accent
        button.isVisible -> DeskColors.Mint
        else -> DeskColors.Idle
    }
    val borderWidth = when {
        button.isActive -> 3.dp
        button.isVisible -> 2.dp
        else -> 1.dp
    }
    val chip = when {
        button.isActive -> "Em uso"
        button.isVisible -> "Visível"
        else -> "Oculto"
    }
    val label = button.tileLabel()
    val subtitle = button.tileSubtitle()
    val instanceBadge = subtitle?.takeIf { it.startsWith("#") }
    val icon = rememberBase64Image(button.iconBase64)

    Column(
        modifier
            .alpha(if (!button.isVisible && !button.isActive) 0.45f else 1f)
            .then(
                if (button.isActive) Modifier.shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = DeskColors.Accent)
                else Modifier,
            )
            .background(DeskColors.SurfaceRaised, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
            if (icon != null) {
                Image(bitmap = icon, contentDescription = label, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            } else {
                Text(emojiForLabel(label), fontSize = 22.sp, color = accentForLabel(label))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = DeskColors.TextPrimary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (subtitle != null && instanceBadge == null) {
            Text(subtitle, color = DeskColors.TextMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (instanceBadge != null) {
            Text(instanceBadge, color = DeskColors.Accent, fontSize = 10.sp)
        }
        Text(chip, color = borderColor, fontSize = 10.sp)
        if (button.monitorIndex > 0) {
            Text("Monitor ${button.monitorIndex}", color = DeskColors.TextMuted, fontSize = 9.sp)
        }
    }
}
