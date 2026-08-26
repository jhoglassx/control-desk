package com.controldesk.app.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.ui.desk.components.DeskHeader
import com.controldesk.app.ui.desk.components.MixerPanel
import com.controldesk.app.ui.desk.components.WorkspacePanel
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.GridButton

@Composable
fun DeskScreen(
    uiState: DeskUiState,
    onPairHost: () -> Unit,
    onManageHosts: () -> Unit,
    onRefresh: () -> Unit,
    onSelectHost: (Int) -> Unit,
    onAppClick: (GridButton) -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    onVolumeLevelChange: (Int) -> Unit,
    onMuteClick: () -> Unit,
    onMicClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.feedback) {
        val message = uiState.feedback ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onClearFeedback()
    }

    Scaffold(
        modifier = modifier.fillMaxSize().background(DeskColors.ScreenGradient),
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (!uiState.hasHosts) {
            EmptyDeskState(
                onPairHost = onPairHost,
                modifier = Modifier.padding(padding),
            )
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                DeskHeader(
                    uiState = uiState,
                    onRefresh = onRefresh,
                    onManageHosts = onManageHosts,
                    onPairHost = onPairHost,
                    onSelectHost = onSelectHost,
                )
                Spacer(Modifier.height(12.dp))
                WorkspacePanel(
                    buttons = uiState.buttons,
                    listHint = uiState.listHint,
                    onAppClick = onAppClick,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.height(10.dp))
                MixerPanel(
                    volumeLevel = uiState.volumeLevel,
                    volumeMuted = uiState.volumeMuted,
                    micMuted = uiState.micMuted,
                    enabled = uiState.hasHosts,
                    onVolumeDown = onVolumeDown,
                    onVolumeUp = onVolumeUp,
                    onVolumeLevelChange = onVolumeLevelChange,
                    onMuteClick = onMuteClick,
                    onMicClick = onMicClick,
                )
            }
        }
    }
}
