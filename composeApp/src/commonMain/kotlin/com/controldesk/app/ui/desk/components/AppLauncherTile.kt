package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    compact: Boolean,
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
    val label = button.tileLabel()
    val subtitle = button.tileSubtitle()
    val icon = rememberBase64Image(button.iconBase64)
    val monitorTint = if (button.monitorIndex == 2) DeskColors.Mint else DeskColors.Accent

    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (!button.isVisible && !button.isActive) 0.45f else 1f)
            .then(
                if (button.isActive) Modifier.shadow(12.dp, RoundedCornerShape(16.dp), ambientColor = DeskColors.Accent)
                else Modifier,
            )
            .background(DeskColors.SurfaceRaised, RoundedCornerShape(16.dp))
            .border(borderWidth, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(if (compact) 32.dp else 40.dp).clip(RoundedCornerShape(9.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                )
            } else {
                Text(emojiForLabel(label), fontSize = if (compact) 20.sp else 24.sp, color = accentForLabel(label))
            }
        }
        Text(
            label,
            color = DeskColors.TextPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        if (!compact && subtitle != null && !subtitle.startsWith("#")) {
            Text(
                subtitle,
                color = DeskColors.TextMuted,
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                lineHeight = 12.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (button.monitorIndex > 0) {
            Text(
                "M${button.monitorIndex}",
                color = monitorTint,
                fontSize = 10.sp,
                modifier = Modifier
                    .background(DeskColors.Surface, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        } else if (button.isActive) {
            Text("Em uso", color = DeskColors.Accent, fontSize = 10.sp)
        }
    }
}
