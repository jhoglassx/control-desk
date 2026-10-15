package com.controldesk.app.ui.desk.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.controldesk.app.theme.DeskColors

@Composable
fun VolumeAppTile(
    volumeLevel: Int,
    muted: Boolean,
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
            diameter = maxWidth * 0.82f,
        )
    }
}


/** Uses the same card shell and edit gestures as app launchers. */
@Composable
fun AudioShortcutContent(
    label: String,
    level: Int,
    muted: Boolean,
    microphone: Boolean,
    editing: Boolean,
    onLevelChange: (Int) -> Unit,
    onAdjustFinished: () -> Unit,
    onMuteClick: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (editing) {
                androidx.compose.material3.Text("${level}%", color = DeskColors.Accent)
            } else {
                AudioKnob(
                    level = level,
                    muted = muted,
                    accent = if (microphone) DeskColors.Mint else DeskColors.Accent,
                    kind = if (microphone) AudioKnobKind.Mic else AudioKnobKind.Speaker,
                    onLevelChange = onLevelChange,
                    onAdjustFinished = onAdjustFinished,
                    onMuteClick = onMuteClick,
                    diameter = minOf(maxWidth, maxHeight) * 0.86f,
                )
            }
        }
        androidx.compose.material3.Text(label, color = DeskColors.TextMuted, fontSize = 9.sp, maxLines = 2,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}
