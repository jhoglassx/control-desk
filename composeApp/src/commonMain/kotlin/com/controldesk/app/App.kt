package com.controldesk.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.controldesk.app.theme.ControlDeskTheme
import com.controldesk.app.ui.desk.DeskScreen
import com.controldesk.app.ui.pair.PairHostScreen
import com.controldesk.app.viewmodel.DeskViewModel
import com.controldesk.app.viewmodel.PairHostViewModel
import org.koin.compose.viewmodel.koinViewModel

enum class AppScreen { Desk, Pair }

@Composable
fun App() {
    ControlDeskTheme {
        val deskViewModel = koinViewModel<DeskViewModel>()
        val pairViewModel = koinViewModel<PairHostViewModel>()
        val deskState by deskViewModel.uiState.collectAsStateWithLifecycle()
        val pairState by pairViewModel.uiState.collectAsStateWithLifecycle()
        var screen by rememberSaveable { mutableStateOf(AppScreen.Desk) }

        when (screen) {
            AppScreen.Desk -> DeskScreen(
                uiState = deskState,
                onPairHost = { screen = AppScreen.Pair },
                onManageHosts = { screen = AppScreen.Pair },
                onRefresh = deskViewModel::refreshRunningApps,
                onSelectHost = deskViewModel::selectHost,
                onAppClick = deskViewModel::onButtonClick,
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
