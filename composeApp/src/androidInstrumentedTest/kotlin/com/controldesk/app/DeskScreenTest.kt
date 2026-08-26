package com.controldesk.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.controldesk.app.theme.ControlDeskTheme
import com.controldesk.app.ui.desk.DeskScreen
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import org.junit.Rule
import org.junit.Test

class DeskScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsPairCta() {
        composeRule.setContent {
            ControlDeskTheme {
                DeskScreen(
                    uiState = DeskUiState(),
                    onPairHost = {},
                    onManageHosts = {},
                    onRefresh = {},
                    onSelectHost = {},
                    onAppClick = {},
                    onVolumeDown = {},
                    onVolumeUp = {},
                    onVolumeLevelChange = {},
                    onMuteClick = {},
                    onMicClick = {},
                    onClearFeedback = {},
                )
            }
        }
        composeRule.onNodeWithText("Parear computador").assertIsDisplayed()
        composeRule.onNodeWithText("Conecte seu computador").assertIsDisplayed()
    }

    @Test
    fun withHostShowsHeaderAndGrid() {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac Mini",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "token",
        )
        val button = GridButton(
            id = "app-Safari",
            label = "Safari · GitHub",
            actionType = ActionType.REMOTE_LAUNCH,
            isActive = true,
            isVisible = true,
        )
        composeRule.setContent {
            ControlDeskTheme {
                DeskScreen(
                    uiState = DeskUiState(hosts = listOf(host), buttons = listOf(button)),
                    onPairHost = {},
                    onManageHosts = {},
                    onRefresh = {},
                    onSelectHost = {},
                    onAppClick = {},
                    onVolumeDown = {},
                    onVolumeUp = {},
                    onVolumeLevelChange = {},
                    onMuteClick = {},
                    onMicClick = {},
                    onClearFeedback = {},
                )
            }
        }
        composeRule.onNodeWithText("Control Desk").assertIsDisplayed()
        composeRule.onNodeWithText("Safari").assertIsDisplayed()
    }
}
