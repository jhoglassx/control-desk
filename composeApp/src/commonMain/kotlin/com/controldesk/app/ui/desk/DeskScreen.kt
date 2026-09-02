package com.controldesk.app.ui.desk

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.controldesk.app.panel.LocalPanelMode
import com.controldesk.app.theme.DeskColors
import com.controldesk.app.theme.DeskDesign
import com.controldesk.app.ui.desk.components.DeskHeader
import com.controldesk.app.ui.desk.components.WorkspacePanel
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.GridButton

@Composable
fun DeskScreen(
    uiState: DeskUiState,
    onPairHost: () -> Unit,
    onManageHosts: () -> Unit,
    onAdminAccessRequest: () -> Unit = onManageHosts,
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
            val pagerState = rememberPagerState(
                initialPage = uiState.selectedHostIndex,
                pageCount = { uiState.hosts.size },
            )
            LaunchedEffect(uiState.selectedHostIndex, uiState.hosts.size) {
                val target = uiState.selectedHostIndex.coerceIn(0, (uiState.hosts.size - 1).coerceAtLeast(0))
                if (pagerState.currentPage != target) {
                    pagerState.scrollToPage(target)
                }
            }
            LaunchedEffect(pagerState.settledPage, uiState.selectedHostIndex) {
                if (pagerState.settledPage != uiState.selectedHostIndex) {
                    onSelectHost(pagerState.settledPage)
                }
            }
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
                val landscape = maxWidth > maxHeight
                val tileSize = DeskDesign.TileSize.dp
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = uiState.hosts.size > 1,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val host = uiState.hosts.getOrNull(page)
                    val current = page == uiState.selectedHostIndex
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = if (landscape) 12.dp else 16.dp)
                            .padding(vertical = if (landscape) 8.dp else 12.dp),
                    ) {
                        DeskHeader(
                            uiState = uiState,
                            host = host,
                            isCurrentPage = current,
                            pageIndex = page,
                            onRefresh = onRefresh,
                            onManageHosts = onManageHosts,
                            onPairHost = onPairHost,
                            onAdminAccessRequest = onAdminAccessRequest,
                        )
                        Spacer(Modifier.height(if (landscape) 8.dp else 12.dp))
                        WorkspacePanel(
                            buttons = host?.id?.let(uiState::buttonsFor).orEmpty(),
                            listHint = host?.id?.let(uiState::listHintFor),
                            onAppClick = { if (current) onAppClick(it) },
                            onAppClose = { if (current) onAppClose(it) },
                            volumeLevel = uiState.volumeLevel,
                            volumeMuted = uiState.volumeMuted,
                            micLevel = uiState.micLevel,
                            micMuted = uiState.micMuted,
                            onVolumeChange = { if (current) onVolumeChange(it) },
                            onVolumeChangeFinished = { if (current) onVolumeChangeFinished() },
                            onMuteClick = { if (current) onMuteClick() },
                            onMicChange = { if (current) onMicChange(it) },
                            onMicChangeFinished = { if (current) onMicChangeFinished() },
                            onMicClick = { if (current) onMicClick() },
                            compact = landscape,
                            tileSize = tileSize,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
