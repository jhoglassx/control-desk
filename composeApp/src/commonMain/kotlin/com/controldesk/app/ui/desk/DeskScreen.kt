package com.controldesk.app.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
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
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val landscape = maxWidth > maxHeight
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = if (landscape) 12.dp else 16.dp)
                        .padding(vertical = if (landscape) 8.dp else 12.dp),
                ) {
                    DeskHeader(
                        uiState = uiState,
                        onRefresh = onRefresh,
                        onManageHosts = onManageHosts,
                        onPairHost = onPairHost,
                        onSelectHost = onSelectHost,
                    )
                    Spacer(Modifier.height(if (landscape) 8.dp else 12.dp))
                    WorkspacePanel(
                        buttons = uiState.buttons,
                        listHint = uiState.listHint,
                        onAppClick = onAppClick,
                        volumeLevel = uiState.volumeLevel,
                        volumeMuted = uiState.volumeMuted,
                        micMuted = uiState.micMuted,
                        onVolumeDown = onVolumeDown,
                        onVolumeUp = onVolumeUp,
                        onMuteClick = onMuteClick,
                        onMicClick = onMicClick,
                        compact = landscape,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}
