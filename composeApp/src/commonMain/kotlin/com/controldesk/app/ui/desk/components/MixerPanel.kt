package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.controldesk.app.theme.DeskColors

@Composable
fun VolumeAppTile(
    volumeLevel: Int,
    muted: Boolean,
    compact: Boolean,
    onLevelChange: (Int) -> Unit,
    onAdjustFinished: () -> Unit,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AudioControlTile(
        level = volumeLevel,
        muted = muted,
        accent = DeskColors.Accent,
        kind = AudioKnobKind.Speaker,
        compact = compact,
        onLevelChange = onLevelChange,
        onAdjustFinished = onAdjustFinished,
        onMuteClick = onMuteClick,
        modifier = modifier,
    )
}

@Composable
fun MicAppTile(
    micLevel: Int,
    muted: Boolean,
    compact: Boolean,
    onLevelChange: (Int) -> Unit,
    onAdjustFinished: () -> Unit,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AudioControlTile(
        level = micLevel,
        muted = muted,
        accent = DeskColors.Mint,
        kind = AudioKnobKind.Mic,
        compact = compact,
        onLevelChange = onLevelChange,
        onAdjustFinished = onAdjustFinished,
        onMuteClick = onMuteClick,
        modifier = modifier,
    )
}

@Composable
private fun AudioControlTile(
    level: Int,
    muted: Boolean,
    accent: Color,
    kind: AudioKnobKind,
    compact: Boolean,
    onLevelChange: (Int) -> Unit,
    onAdjustFinished: () -> Unit,
    onMuteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        AudioKnob(
            level = level,
            muted = muted,
            accent = accent,
            kind = kind,
            onLevelChange = onLevelChange,
            onAdjustFinished = onAdjustFinished,
            onMuteClick = onMuteClick,
            diameter = maxWidth * if (compact) 0.90f else 0.82f,
        )
    }
}
