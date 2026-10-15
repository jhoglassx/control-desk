package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlin.math.roundToInt
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
    onPreviewReorder: (appId: String, rowDelta: Int, columnDelta: Int) -> Unit = { _, _, _ -> },
    compact: Boolean,
    isSelectedForReorder: Boolean = false,
    onSelectForReorder: (String, Float, Float) -> Unit = { _, _, _ -> },
    selectedDragOffsetX: Float = 0f,
    selectedDragOffsetY: Float = 0f,
    onDragBy: (Float, Float) -> Unit = { _, _ -> },
    onFinishReorderDrag: () -> Unit = {},
    spanColumns: Int = 1,
    spanRows: Int = 1,
    onResize: (() -> Unit)? = null,
    customContent: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val dockOnly = !button.isFavoriteShortcut && button.isDockPinned && !button.isVisible && !button.isActive
    val open = button.isVisible && !button.isActive
    // Azul = em uso; verde = aberto; cinza = só na Dock / fechado
    val borderColor = when {
        button.isActive -> DeskColors.Accent
        open -> DeskColors.Mint
        else -> DeskColors.Idle
    }
    val borderWidth = when {
        button.isActive -> 3.dp
        open -> 2.dp
        else -> 1.dp
    }
    val label = button.tileLabel()
    val appName = button.tileAppName()
    val icon = rememberBase64Image(button.iconBase64)
    val monitorTint = if (button.monitorIndex == 2) DeskColors.Mint else DeskColors.Accent
    val tileAlpha = when {
        button.isFavoriteShortcut || button.isActive || button.isVisible -> 1f
        dockOnly -> 0.7f
        else -> 0.45f
    }

    val cardShape = RoundedCornerShape(16.dp)
    val reorderMode = onResize != null
    val shakeTransition = rememberInfiniteTransition(label = "app-tile-reorder-${button.id}")
    val shakeAngle = if (reorderMode && !isSelectedForReorder) {
        shakeTransition.animateFloat(
            initialValue = -0.8f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 135, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "app-tile-shake-${button.id}",
        ).value
    } else 0f
    val tileDescription = buildString {
        append(label)
        if (button.isActive) append(", aplicativo ativo")
        if (button.monitorIndex > 0) append(", monitor ${button.monitorIndex}")
        if (reorderMode) {
            append(if (isSelectedForReorder) ", selecionado para mover" else ", segure e arraste qualquer parte para mover")
        }
    }
    val currentClick by rememberUpdatedState(onClick)
    val currentLongClick by rememberUpdatedState(onLongClick)
    val currentButtonId by rememberUpdatedState(button.id)
    val currentSelectForReorder by rememberUpdatedState(onSelectForReorder)
    val currentPreviewReorder by rememberUpdatedState(onPreviewReorder)
    val currentDragBy by rememberUpdatedState(onDragBy)
    val currentFinishDrag by rememberUpdatedState(onFinishReorderDrag)
    val reorderGestureModifier = if (!reorderMode) Modifier else Modifier.pointerInput(reorderMode, spanColumns, spanRows) {
        var dragDistance = androidx.compose.ui.geometry.Offset.Zero
        var draggedAppId: String? = null
        var previousRowDelta = 0
        var previousColumnDelta = 0
        val gapPx = DeskDesign.TileGap.dp.toPx()
        val tileCellWidth = (size.width - gapPx * (spanColumns - 1)) / spanColumns.coerceAtLeast(1)
        val tileCellHeight = (size.height - gapPx * (spanRows - 1)) / spanRows.coerceAtLeast(1)
        detectDragGesturesAfterLongPress(
            onDragStart = {
                dragDistance = androidx.compose.ui.geometry.Offset.Zero
                previousRowDelta = 0
                previousColumnDelta = 0
                draggedAppId = currentButtonId
                currentSelectForReorder(
                    currentButtonId,
                    tileCellWidth.toFloat(),
                    tileCellHeight.toFloat(),
                )
            },
            onDrag = { change, amount ->
                change.consume()
                dragDistance += amount
                currentDragBy(amount.x, amount.y)
                val rowDelta = (dragDistance.y / (tileCellHeight + gapPx)).roundToInt()
                val columnDelta = (dragDistance.x / (tileCellWidth + gapPx)).roundToInt()
                if (rowDelta != previousRowDelta || columnDelta != previousColumnDelta) {
                    draggedAppId?.let { currentPreviewReorder(it, rowDelta, columnDelta) }
                    previousRowDelta = rowDelta
                    previousColumnDelta = columnDelta
                }
            },
            onDragEnd = {
                currentFinishDrag()
                draggedAppId = null
            },
            onDragCancel = currentFinishDrag,
        )
    }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(spanColumns.coerceAtLeast(1).toFloat() / spanRows.coerceAtLeast(1))
            .alpha(tileAlpha)
            .then(
                if (reorderMode && isSelectedForReorder) {
                    Modifier.shadow(18.dp, cardShape, ambientColor = DeskColors.Accent)
                } else if (button.isActive) Modifier.shadow(12.dp, cardShape, ambientColor = DeskColors.Accent)
                else Modifier,
            )
            .then(
                if (reorderMode) Modifier.graphicsLayer {
                    if (isSelectedForReorder) {
                        translationX = selectedDragOffsetX
                        translationY = selectedDragOffsetY
                        rotationZ = 0f
                    } else {
                        translationX = 0f
                        translationY = 0f
                        rotationZ = shakeAngle
                    }
                } else Modifier,
            )
            // Lift the dragged tile above its row siblings; WorkspacePanel lifts
            // the containing row above other grid rows as well.
            .zIndex(if (reorderMode && isSelectedForReorder) 100f else 0f)
            .clip(cardShape)
            .background(DeskColors.SurfaceRaised)
            .border(
                width = if (reorderMode && isSelectedForReorder) 3.dp else borderWidth,
                color = if (reorderMode && isSelectedForReorder) DeskColors.Accent else borderColor,
                shape = cardShape,
            )
            .semantics {
                contentDescription = tileDescription
            }
            .then(
                if (!reorderMode && customContent != null) Modifier else if (!reorderMode) Modifier.pointerInput(button.id) {
                    detectTapGestures(onLongPress = { currentLongClick() }, onTap = { currentClick() })
                } else reorderGestureModifier,
            ),
    ) {
        val iconSize = (maxWidth * 0.58f).coerceIn(34.dp, 52.dp)
        val accent = accentForLabel(appName)
        if (customContent != null) {
            Box(Modifier.fillMaxSize().padding(bottom = if (reorderMode) 22.dp else 0.dp)) { customContent() }
        } else Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 4.dp)
                .then(if (onResize != null) Modifier.padding(bottom = 18.dp) else Modifier),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(DeskDesign.IconCornerRadius.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val controlIcon = when (button.payload["controlKind"]) {
                    "CAMERA" -> Icons.Default.CameraAlt
                    "DEVICE" -> Icons.Default.Devices
                    "WEBSITE" -> Icons.Default.Language
                    else -> null
                }
                if (controlIcon != null) {
                    Icon(controlIcon, contentDescription = label, tint = DeskColors.Accent, modifier = Modifier.fillMaxSize())
                } else if (icon != null) {
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
                fontSize = 9.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            )
        }
        if (reorderMode && isSelectedForReorder) {
            Text(
                "Selecionado",
                color = DeskColors.Accent,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
        if (button.isActive) {
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .size(8.dp)
                    .background(DeskColors.Accent, RoundedCornerShape(50))
                    .semantics { contentDescription = "Aplicativo ativo" },
            )
        }
        if (button.monitorIndex > 0) {
            Text(
                "M${button.monitorIndex}",
                color = monitorTint,
                fontSize = 8.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .shadow(4.dp, RoundedCornerShape(8.dp))
                    .background(DeskColors.Surface, RoundedCornerShape(8.dp))
                    .border(1.dp, monitorTint, RoundedCornerShape(8.dp))
                    .padding(horizontal = 4.dp, vertical = 1.dp),
            )
        }
        val instanceCount = button.payload["instanceCount"]?.toIntOrNull() ?: 0
        if (instanceCount > 1 && !reorderMode) {
            Text(
                "$instanceCount janelas",
                color = DeskColors.TextMuted,
                fontSize = 8.sp,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp),
            )
        }
        if (onResize != null) {
            IconButton(
                onClick = onResize,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(DeskColors.Surface)
                    .border(1.dp, DeskColors.Accent, RoundedCornerShape(10.dp))
                    .semantics { contentDescription = "Redimensionar ${button.label}, tamanho ${spanColumns} por ${spanRows}" },
            ) {
                Text(
                    "${spanColumns}×${spanRows}",
                    color = DeskColors.TextPrimary,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
