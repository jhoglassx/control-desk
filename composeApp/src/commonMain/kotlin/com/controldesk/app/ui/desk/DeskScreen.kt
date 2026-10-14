package com.controldesk.app.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import com.controldesk.app.panel.LocalPanelMode
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.ui.desk.components.DeskHeader
import com.controldesk.app.ui.desk.components.WorkspacePanel
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.GridButton

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DeskScreen(
    uiState: DeskUiState,
    onPairHost: () -> Unit,
    onManageHosts: () -> Unit,
    onAdminAccessRequest: () -> Unit = onManageHosts,
    onManageShortcuts: () -> Unit = {},
    onToggleReorganizeApps: (Boolean) -> Unit = {},
    onSelectAppForReorder: (String, Float, Float) -> Unit = { _, _, _ -> },
    onMoveSelectedAppBy: (Float, Float) -> Unit = { _, _ -> },
    onPreviewReorderApp: (String, Int) -> Unit = { _, _ -> },
    onFinishAppReorderDrag: () -> Unit = {},
    onReorderApps: (List<String>) -> Unit = {},
    onRefresh: () -> Unit,
    onSelectHost: (Int) -> Unit,
    onAppClick: (GridButton) -> Unit,
    onAppClose: (GridButton) -> Unit,
    onVolumeChange: (Int) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onMuteClick: () -> Unit,
    onMicChange: (Int) -> Unit,
    onMicChangeFinished: () -> Unit,
    onMicClick: () -> Unit,
    onClearFeedback: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val panelMode = LocalPanelMode.current.enabled
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
                onAdminAccessRequest = onAdminAccessRequest,
                panelMode = panelMode,
                modifier = Modifier.padding(padding),
            )
        } else {
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val landscape = maxWidth > maxHeight
                val horizontalInset = if (landscape) 24.dp else 32.dp
                val availableWidth = (maxWidth - horizontalInset).coerceAtLeast(0.dp)
                val columns = appGridColumnCount(
                    availableWidthDp = availableWidth.value,
                )
                val pullRefreshState = rememberPullToRefreshState()
                LaunchedEffect(uiState.isLoadingApps, pullRefreshState) {
                    if (!uiState.isLoadingApps) pullRefreshState.animateToHidden()
                }
                val selectedIndex = uiState.selectedHostIndex.coerceIn(0, uiState.hosts.lastIndex)
                val lastIndex = uiState.hosts.lastIndex
                val swipeThreshold = 56.dp
                Box(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(selectedIndex, lastIndex) {
                            var dragDistance = 0f
                            detectHorizontalDragGestures(
                                onHorizontalDrag = { _, amount -> dragDistance += amount },
                                onDragEnd = {
                                    val thresholdPx = swipeThreshold.toPx()
                                    when {
                                        dragDistance <= -thresholdPx -> onSelectHost((selectedIndex + 1).coerceAtMost(lastIndex))
                                        dragDistance >= thresholdPx -> onSelectHost((selectedIndex - 1).coerceAtLeast(0))
                                    }
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f },
                            )
                        },
                ) {
                    val host = uiState.hosts.getOrNull(selectedIndex)
                    IconButton(
                        onClick = onAdminAccessRequest,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .semantics { contentDescription = "Configurações e dispositivos" }
                            .testTag(DESK_ADMIN_BUTTON_TAG),
                    ) {
                        androidx.compose.material3.Text(
                            "⚙",
                            color = DeskColors.TextPrimary,
                            fontSize = 21.sp,
                        )
                    }
                    IconButton(
                        onClick = onManageShortcuts,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 52.dp)
                            .semantics { contentDescription = "Adicionar app fixo" },
                    ) {
                        androidx.compose.material3.Text("+", color = DeskColors.TextPrimary, fontSize = 24.sp)
                    }
                    IconButton(
                        onClick = { onToggleReorganizeApps(!uiState.isReorganizingApps) },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 96.dp)
                            .semantics {
                                contentDescription = if (uiState.isReorganizingApps) "Concluir reorganização" else "Reorganizar apps"
                            }
                            .testTag(DESK_REORGANIZE_BUTTON_TAG),
                    ) {
                        androidx.compose.material3.Text(
                            if (uiState.isReorganizingApps) "✓" else "↕",
                            color = if (uiState.isReorganizingApps) DeskColors.Mint else DeskColors.TextPrimary,
                            fontSize = 21.sp,
                        )
                    }
                    PullToRefreshBox(
                        isRefreshing = uiState.isLoadingApps,
                        onRefresh = onRefresh,
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize().testTag(DESK_PULL_REFRESH_TAG),
                        indicator = {
                            if (uiState.isLoadingApps) {
                                PullToRefreshDefaults.Indicator(
                                    state = pullRefreshState,
                                    isRefreshing = uiState.isLoadingApps,
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .testTag(DESK_PULL_REFRESH_INDICATOR_TAG),
                                    color = DeskColors.Accent,
                                )
                            }
                        },
                    ) {
                        WorkspacePanel(
                            buttons = host?.id?.let(uiState::buttonsFor).orEmpty(),
                            listHint = host?.id?.let(uiState::listHintFor),
                            onAppClick = onAppClick,
                            onAppClose = onAppClose,
                            isReorganizingApps = uiState.isReorganizingApps,
                            selectedAppIdForReorder = uiState.selectedAppIdForReorder,
                            selectedAppDragStartIndex = uiState.selectedAppDragStartIndex,
                            selectedAppDragOffsetX = uiState.selectedAppDragOffsetX,
                            selectedAppDragOffsetY = uiState.selectedAppDragOffsetY,
                            selectedAppDragCellWidth = uiState.selectedAppDragCellWidth,
                            selectedAppDragCellHeight = uiState.selectedAppDragCellHeight,
                            onSelectAppForReorder = onSelectAppForReorder,
                            onMoveSelectedAppBy = onMoveSelectedAppBy,
                            onPreviewReorderApp = onPreviewReorderApp,
                            onFinishAppReorderDrag = onFinishAppReorderDrag,
                            onReorderApps = onReorderApps,
                            volumeLevel = uiState.volumeLevel,
                            volumeMuted = uiState.volumeMuted,
                            micLevel = uiState.micLevel,
                            micMuted = uiState.micMuted,
                            onVolumeChange = onVolumeChange,
                            onVolumeChangeFinished = onVolumeChangeFinished,
                            onMuteClick = onMuteClick,
                            onMicChange = onMicChange,
                            onMicChangeFinished = onMicChangeFinished,
                            onMicClick = onMicClick,
                            compact = landscape,
                            columns = columns,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = if (landscape) 12.dp else 16.dp)
                                .padding(top = if (landscape) 8.dp else 12.dp),
                        )
                    }
                    DeskHeader(
                        uiState = uiState,
                        host = host,
                        isCurrentPage = true,
                        pageIndex = selectedIndex,
                        onManageHosts = onManageHosts,
                        onPairHost = onPairHost,
                        onAdminAccessRequest = onAdminAccessRequest,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

internal const val DESK_PULL_REFRESH_TAG = "desk-pull-refresh"
internal const val DESK_PULL_REFRESH_INDICATOR_TAG = "desk-pull-refresh-indicator"
internal const val DESK_ADMIN_BUTTON_TAG = "desk-admin-settings-button"
internal const val DESK_REORGANIZE_BUTTON_TAG = "desk-reorganize-apps-button"
