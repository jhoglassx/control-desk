package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign

@Composable
fun MixerPanel(
    volumeLevel: Int,
    volumeMuted: Boolean,
    micMuted: Boolean,
    enabled: Boolean,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeLevelChange: (Int) -> Unit,
    onMuteClick: () -> Unit,
    onMicClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isDragging by remember { mutableFloatStateOf(Float.NaN) }
    val sliderValue = if (!isDragging.isNaN()) isDragging else volumeLevel.toFloat()
    val trackColor = if (volumeMuted) DeskColors.Danger else DeskColors.Accent

    Row(
        modifier
            .fillMaxWidth()
            .height(DeskDesign.MixerHeight.dp)
            .background(DeskColors.Surface, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onVolumeDown, enabled = enabled) { Text("−", color = DeskColors.TextPrimary, fontSize = 18.sp) }
        Slider(
            value = sliderValue.coerceIn(0f, 100f),
            onValueChange = { isDragging = it },
            onValueChangeFinished = {
                if (!isDragging.isNaN()) onVolumeLevelChange(isDragging.toInt())
                isDragging = Float.NaN
            },
            valueRange = 0f..100f,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = if (volumeMuted) DeskColors.Danger else DeskColors.Accent,
                activeTrackColor = trackColor,
                inactiveTrackColor = DeskColors.Stroke,
            ),
        )
        TextButton(onClick = onVolumeUp, enabled = enabled) { Text("+", color = DeskColors.TextPrimary, fontSize = 18.sp) }
        TextButton(onClick = onMuteClick, enabled = enabled) {
            Text(
                if (volumeMuted) "Mudo" else "Mute",
                color = if (volumeMuted) DeskColors.Danger else DeskColors.TextPrimary,
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onMicClick, enabled = enabled) {
            Text(
                if (micMuted) "Mic off" else "Mic on",
                color = if (micMuted) DeskColors.Danger else DeskColors.Mint,
                fontSize = 12.sp,
                modifier = if (micMuted) Modifier.background(DeskColors.DangerSoft, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp) else Modifier,
            )
        }
    }
}
