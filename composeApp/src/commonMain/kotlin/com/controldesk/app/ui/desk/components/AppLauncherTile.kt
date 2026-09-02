package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign
import com.controldesk.app.util.accentForLabel
import com.controldesk.app.util.monogramForLabel
import com.controldesk.app.util.rememberBase64Image
import com.controldesk.app.util.tileAppName
import com.controldesk.app.util.tileLabel
import com.controldesk.domain.model.GridButton

@Composable
fun AppLauncherTile(
    button: GridButton,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val dockOnly = button.isDockPinned && !button.isVisible && !button.isActive
    val borderColor = when {
        button.isActive -> DeskColors.Accent
        button.isVisible -> DeskColors.Mint
        dockOnly -> DeskColors.Idle
        else -> DeskColors.Idle
    }
    val borderWidth = when {
        button.isActive -> 3.dp
        button.isVisible -> 2.dp
        else -> 1.dp
    }
    val label = button.tileLabel()
    val appName = button.tileAppName()
    val icon = rememberBase64Image(button.iconBase64)
    val monitorTint = if (button.monitorIndex == 2) DeskColors.Mint else DeskColors.Accent
    val tileAlpha = when {
        button.isActive || button.isVisible -> 1f
        dockOnly -> 0.7f
        else -> 0.45f
    }

    val cardShape = RoundedCornerShape(16.dp)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .alpha(tileAlpha)
            .then(
                if (button.isActive) Modifier.shadow(12.dp, cardShape, ambientColor = DeskColors.Accent)
                else Modifier,
            )
            .clip(cardShape)
            .background(DeskColors.SurfaceRaised)
            .border(borderWidth, borderColor, cardShape)
            .pointerInput(button.id) {
                detectTapGestures(
                    onLongPress = { onLongClick() },
                    onTap = { onClick() },
                )
            },
    ) {
        val iconSize = DeskDesign.IconSize.dp
        val accent = accentForLabel(appName)
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(DeskDesign.IconCornerRadius.dp)),
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
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(accent.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            monogramForLabel(appName),
                            fontSize = if (compact) 22.sp else 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
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
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
        }
        if (button.monitorIndex > 0) {
            Text(
                "M${button.monitorIndex}",
                color = monitorTint,
                fontSize = 9.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(DeskColors.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, monitorTint, RoundedCornerShape(8.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
