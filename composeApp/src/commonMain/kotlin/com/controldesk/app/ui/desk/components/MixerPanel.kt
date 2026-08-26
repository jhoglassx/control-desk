package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors

@Composable
fun VolumeAppTile(
    volumeLevel: Int,
    muted: Boolean,
    onMuteClick: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (muted) DeskColors.Danger else DeskColors.Accent
    Column(
        modifier
            .fillMaxWidth()
            .background(DeskColors.SurfaceRaised, RoundedCornerShape(16.dp))
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.clickable(onClick = onMuteClick)) {
            MuteGlyph(muted = muted) {
                Text("🔊", fontSize = 22.sp)
            }
        }
        Text(
            "Volume",
            color = DeskColors.TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (muted) "Mudo" else "$volumeLevel%",
            color = border,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onMuteClick),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "−",
                color = DeskColors.TextPrimary,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(DeskColors.Surface, RoundedCornerShape(8.dp))
                    .clickable(onClick = onVolumeDown)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
            Text(
                "+",
                color = DeskColors.TextPrimary,
                fontSize = 16.sp,
                modifier = Modifier
                    .background(DeskColors.Surface, RoundedCornerShape(8.dp))
                    .clickable(onClick = onVolumeUp)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun MicAppTile(
    muted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (muted) DeskColors.Danger else DeskColors.Mint
    Column(
        modifier
            .fillMaxWidth()
            .background(DeskColors.SurfaceRaised, RoundedCornerShape(16.dp))
            .border(2.dp, border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        MuteGlyph(muted = muted) {
            MicGlyph(color = if (muted) DeskColors.Danger else DeskColors.Mint)
        }
        Text(
            "Mic",
            color = DeskColors.TextPrimary,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (muted) "Off" else "On",
            color = border,
            fontSize = 12.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MuteGlyph(
    muted: Boolean,
    content: @Composable () -> Unit,
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(28.dp)) {
        content()
        if (muted) {
            Canvas(Modifier.matchParentSize()) {
                val pad = size.minDimension * 0.12f
                drawLine(
                    color = DeskColors.Danger,
                    start = Offset(pad, size.height - pad),
                    end = Offset(size.width - pad, pad),
                    strokeWidth = 3.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun MicGlyph(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = 2.dp.toPx()
        val cx = size.width / 2f
        val capsuleWidth = size.width * 0.42f
        val capsuleTop = size.height * 0.04f
        val capsuleHeight = size.height * 0.52f
        drawRoundRect(
            color = color,
            topLeft = Offset(cx - capsuleWidth / 2f, capsuleTop),
            size = Size(capsuleWidth, capsuleHeight),
            cornerRadius = CornerRadius(capsuleWidth / 2f, capsuleWidth / 2f),
        )
        val yokeWidth = capsuleWidth * 1.7f
        val yokeTop = capsuleTop + capsuleHeight * 0.38f
        drawArc(
            color = color,
            startAngle = 8f,
            sweepAngle = 164f,
            useCenter = false,
            topLeft = Offset(cx - yokeWidth / 2f, yokeTop),
            size = Size(yokeWidth, size.height * 0.42f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        val stemTop = yokeTop + size.height * 0.38f
        drawLine(
            color = color,
            start = Offset(cx, stemTop),
            end = Offset(cx, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(cx - size.width * 0.22f, size.height * 0.86f),
            end = Offset(cx + size.width * 0.22f, size.height * 0.86f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
