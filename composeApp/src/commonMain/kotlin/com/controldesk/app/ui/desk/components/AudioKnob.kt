package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val StartAngle = 135f
private const val Sweep = 270f
private const val DegreesPerPercent = Sweep / 100f

enum class AudioKnobKind { Speaker, Mic }

@Composable
fun AudioKnob(
    level: Int,
    muted: Boolean,
    accent: Color,
    kind: AudioKnobKind,
    onLevelChange: (Int) -> Unit,
    onAdjustFinished: () -> Unit,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 72.dp,
) {
    var dragging by remember { mutableStateOf(false) }
    var displayLevel by remember { mutableIntStateOf(level) }
    var remainder by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(level) {
        if (!dragging) displayLevel = level
    }
    val ring = if (muted) DeskColors.Danger else accent
    Box(
        modifier
            .size(diameter)
            .pointerInput(onLevelChange, onAdjustFinished, onMuteClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
                    if (slop == null) {
                        onMuteClick()
                        return@awaitEachGesture
                    }
                    dragging = true
                    remainder = 0f
                    var current = displayLevel
                    var lastAngle = pointerAngle(slop.position, center)
                    drag(down.id) { change ->
                        val angle = pointerAngle(change.position, center)
                        remainder += shortestDelta(lastAngle, angle) / DegreesPerPercent
                        lastAngle = angle
                        val steps = remainder.toInt()
                        if (steps != 0) {
                            remainder -= steps
                            val next = (current + steps).coerceIn(0, 100)
                            if (next != current) {
                                current = next
                                displayLevel = next
                                onLevelChange(next)
                            }
                        }
                        change.consume()
                    }
                    dragging = false
                    onAdjustFinished()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 6.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2f, this.size.height - inset * 2f)
            val topLeft = Offset(inset, inset)
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val radius = this.size.minDimension / 2f
            val progress = (displayLevel.coerceIn(0, 100) / 100f) * Sweep

            drawCircle(color = DeskColors.SurfaceRaised, radius = radius - 1.dp.toPx(), center = Offset(cx, cy))
            val iconColor = ring.copy(alpha = 0.42f)
            val iconSize = radius * 0.88f
            when (kind) {
                AudioKnobKind.Speaker -> drawSpeakerGlyph(Offset(cx, cy), iconSize, iconColor)
                AudioKnobKind.Mic -> drawMicGlyph(Offset(cx, cy), iconSize, iconColor)
            }
            drawArc(
                color = DeskColors.Stroke,
                startAngle = StartAngle,
                sweepAngle = Sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0.5f) {
                drawArc(
                    color = ring,
                    startAngle = StartAngle,
                    sweepAngle = progress,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            val indicatorAngle = (StartAngle + progress) * (PI.toFloat() / 180f)
            val tickR = radius - inset
            drawCircle(
                color = ring,
                radius = 4.dp.toPx(),
                center = Offset(cx + cos(indicatorAngle) * tickR, cy + sin(indicatorAngle) * tickR),
            )
        }
        Text(
            "$displayLevel%",
            color = ring,
            fontSize = (diameter.value * 0.20f).coerceIn(12f, 20f).sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            style = TextStyle(
                shadow = Shadow(
                    color = DeskColors.SurfaceRaised.copy(alpha = 0.92f),
                    blurRadius = 10f,
                ),
            ),
        )
    }
}

private fun DrawScope.drawSpeakerGlyph(center: Offset, size: Float, color: Color) {
    val left = center.x - size * 0.38f
    val neck = center.x - size * 0.12f
    val cone = center.x + size * 0.18f
    val bodyH = size * 0.28f
    val path = Path().apply {
        moveTo(left, center.y - bodyH / 2f)
        lineTo(neck, center.y - bodyH / 2f)
        lineTo(cone, center.y - size * 0.38f)
        lineTo(cone, center.y + size * 0.38f)
        lineTo(neck, center.y + bodyH / 2f)
        lineTo(left, center.y + bodyH / 2f)
        close()
    }
    drawPath(path, color)
    val stroke = size * 0.08f
    fun wave(radius: Float) {
        drawArc(
            color = color,
            startAngle = -40f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.15f, center.y - radius),
            size = Size(radius * 1.35f, radius * 2f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
    wave(size * 0.28f)
    wave(size * 0.44f)
}

private fun DrawScope.drawMicGlyph(center: Offset, size: Float, color: Color) {
    val stroke = size * 0.09f
    val capW = size * 0.28f
    val capH = size * 0.42f
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - capW / 2f, center.y - size * 0.42f),
        size = Size(capW, capH),
        cornerRadius = CornerRadius(capW / 2f, capW / 2f),
    )
    val yokeW = capW * 1.75f
    drawArc(
        color = color,
        startAngle = 8f,
        sweepAngle = 164f,
        useCenter = false,
        topLeft = Offset(center.x - yokeW / 2f, center.y - size * 0.12f),
        size = Size(yokeW, size * 0.42f),
        style = Stroke(width = stroke, cap = StrokeCap.Round),
    )
    drawLine(
        color = color,
        start = Offset(center.x, center.y + size * 0.28f),
        end = Offset(center.x, center.y + size * 0.40f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = color,
        start = Offset(center.x - size * 0.16f, center.y + size * 0.40f),
        end = Offset(center.x + size * 0.16f, center.y + size * 0.40f),
        strokeWidth = stroke,
        cap = StrokeCap.Round,
    )
}

private fun pointerAngle(position: Offset, center: Offset): Float {
    return atan2(position.y - center.y, position.x - center.x) * (180f / PI.toFloat())
}

private fun shortestDelta(from: Float, to: Float): Float {
    var delta = to - from
    while (delta > 180f) delta -= 360f
    while (delta < -180f) delta += 360f
    return delta
}
