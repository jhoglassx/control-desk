package com.controldesk.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controldesk.app.panel.LocalPanelMode
import com.controldesk.app.panel.PanelBackHandler
import com.controldesk.app.theme.ControlDeskTheme
import com.controldesk.app.ui.admin.AdminPinDialog
import com.controldesk.app.ui.desk.DeskScreen
import com.controldesk.app.ui.pair.PairHostScreen
import com.controldesk.app.viewmodel.DeskViewModel
import com.controldesk.app.viewmodel.PairHostViewModel
import org.koin.compose.viewmodel.koinViewModel

enum class AppScreen { Desk, Pair }

@Composable
fun App() {
    ControlDeskTheme {
        val panelMode = LocalPanelMode.current
        val deskViewModel = koinViewModel<DeskViewModel>()
        val pairViewModel = koinViewModel<PairHostViewModel>()
        val deskState by deskViewModel.uiState.collectAsStateWithLifecycle()
        val pairState by pairViewModel.uiState.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableStateOf(AppScreen.Desk) }
        var showAdminPin by rememberSaveable { mutableStateOf(false) }

        fun openAdminSettings() {
            if (panelMode.enabled && deskState.hasHosts) {
                showAdminPin = true
            } else {
                screen = AppScreen.Pair
            }
        }

        PanelBackHandler(panelMode.enabled && screen == AppScreen.Desk) { }
        PanelBackHandler(panelMode.enabled && screen == AppScreen.Pair) {
            screen = AppScreen.Desk
        }

        if (showAdminPin) {
            AdminPinDialog(
                onDismiss = { showAdminPin = false },
                onSuccess = {
                    showAdminPin = false
                    screen = AppScreen.Pair
                },
            )
        }

        when (screen) {
            AppScreen.Desk -> DeskScreen(
                uiState = deskState,
                onPairHost = ::openAdminSettings,
                onManageHosts = ::openAdminSettings,
                onAdminAccessRequest = ::openAdminSettings,
                onRefresh = deskViewModel::refreshRunningApps,
                onSelectHost = deskViewModel::selectHost,
                onAppClick = deskViewModel::onButtonClick,
                onAppClose = deskViewModel::onAppClose,
                onVolumeChange = deskViewModel::onVolumeLevelChange,
                onVolumeChangeFinished = deskViewModel::onVolumeAdjustFinished,
                onMuteClick = deskViewModel::onVolumeKnobClick,
                onMicChange = deskViewModel::onMicLevelChange,
                onMicChangeFinished = deskViewModel::onMicAdjustFinished,
                onMicClick = deskViewModel::onMicKnobClick,
                onClearFeedback = deskViewModel::clearFeedback,
            )
            AppScreen.Pair -> PairHostScreen(
                uiState = pairState,
                onBack = { screen = AppScreen.Desk },
                onNameChange = pairViewModel::onNameChange,
                onHostChange = pairViewModel::onHostChange,
                onPortChange = pairViewModel::onPortChange,
                onPinChange = pairViewModel::onPinChange,
                onOsTypeChange = pairViewModel::onOsTypeChange,
                showLauncherHint = panelMode.enabled,
                onPair = {
                    pairViewModel.pair {
                        deskViewModel.refreshHosts()
                        screen = AppScreen.Desk
                    }
                },
                onRemoveHost = pairViewModel::removeHost,
            )
        }
    }
}
