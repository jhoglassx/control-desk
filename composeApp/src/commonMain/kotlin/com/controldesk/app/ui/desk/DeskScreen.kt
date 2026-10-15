package com.controldesk.app.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Icon
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
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
    onSelectWindow: (String) -> Unit = {},
    onDismissWindowSelection: () -> Unit = {},
    onPairHost: () -> Unit,
    onManageHosts: () -> Unit,
    onAdminAccessRequest: () -> Unit = onManageHosts,
    onManageShortcuts: () -> Unit = {},
    onToggleReorganizeApps: (Boolean) -> Unit = {},
    onSelectAppForReorder: (String, Float, Float) -> Unit = { _, _, _ -> },
    onMoveSelectedAppBy: (Float, Float) -> Unit = { _, _ -> },
    onPreviewAppGridPositions: (Map<String, Int>) -> Unit = {},
    onFinishAppReorderDrag: () -> Unit = {},
    onResizeAppTile: (String) -> Unit = {},
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
    uiState.windowSelection?.let { selection ->
        AlertDialog(
            onDismissRequest = onDismissWindowSelection,
            title = { Text(if (selection.close) "Fechar janela · ${selection.appName}" else selection.appName) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    selection.windows.forEach { window ->
                        TextButton(onClick = { onSelectWindow(window.id) }) {
                            Text(buildString {
                                append(window.payload["windowTitle"]?.takeIf { it.isNotBlank() } ?: window.label)
                                if (window.monitorIndex > 0) append(" · Monitor ${window.monitorIndex}")
                            })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onDismissWindowSelection) { Text("Cancelar") } },
        )
    }
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
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = { onToggleReorganizeApps(!uiState.isReorganizingApps) },
                            modifier = Modifier
                                .semantics {
                                    contentDescription = if (uiState.isReorganizingApps) "Concluir reorganização" else "Reorganizar apps"
                                }
                                .testTag(DESK_REORGANIZE_BUTTON_TAG),
                        ) {
                            Icon(
                                imageVector = if (uiState.isReorganizingApps) Icons.Default.Done else Icons.Default.SwapVert,
                                contentDescription = null,
                                tint = if (uiState.isReorganizingApps) DeskColors.Mint else DeskColors.TextPrimary,
                            )
                        }
                        IconButton(
                            onClick = onManageShortcuts,
                            modifier = Modifier
                                .semantics { contentDescription = "Adicionar atalho" }
                                .testTag(DESK_ADD_SHORTCUT_BUTTON_TAG),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = DeskColors.TextPrimary)
                        }
                        IconButton(
                            onClick = onAdminAccessRequest,
                            modifier = Modifier
                                .semantics { contentDescription = "Configurações e dispositivos" }
                                .testTag(DESK_ADMIN_BUTTON_TAG),
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, tint = DeskColors.TextPrimary)
                        }
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
                            buttons = host?.id?.let(uiState::workspaceButtonsFor).orEmpty(),
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
                            onPreviewAppGridPositions = onPreviewAppGridPositions,
                            appGridPositions = host?.appGridPositions.orEmpty(),
                            onFinishAppReorderDrag = onFinishAppReorderDrag,
                            onResizeAppTile = onResizeAppTile,
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
internal const val DESK_ADD_SHORTCUT_BUTTON_TAG = "desk-add-shortcut-button"
