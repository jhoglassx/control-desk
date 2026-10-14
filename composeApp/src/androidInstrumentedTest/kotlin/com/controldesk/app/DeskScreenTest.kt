package com.controldesk.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import com.controldesk.app.ui.desk.DESK_PULL_REFRESH_INDICATOR_TAG
import com.controldesk.app.ui.desk.DESK_PULL_REFRESH_TAG
import com.controldesk.app.theme.ControlDeskTheme
import com.controldesk.app.ui.desk.DeskScreen
import com.controldesk.app.ui.desk.PinnedAppsScreen
import com.controldesk.app.viewmodel.DeskUiState
import com.controldesk.app.viewmodel.PinnedAppsUiState
import com.controldesk.app.viewmodel.PinnedAppsIntent
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.model.PinnedAppShortcut
import com.controldesk.domain.model.MacFileEntry
import com.controldesk.domain.model.MacFileListing
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.controldesk.app.ui.desk.DESK_ADMIN_BUTTON_TAG

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
                    onAppClose = {},
                    onVolumeChange = {},
                    onVolumeChangeFinished = {},
                    onMuteClick = {},
                    onMicChange = {},
                    onMicChangeFinished = {},
                    onMicClick = {},
                    onClearFeedback = {},
                )
            }
        }
        composeRule.onNodeWithText("Parear computador").assertIsDisplayed()
        composeRule.onNodeWithText("Pareie Mac, Windows ou Linux na mesma rede e controle apps, volume e microfone.").assertIsDisplayed()
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
        var adminAccessRequested = false
        composeRule.setContent {
            ControlDeskTheme {
                DeskScreen(
                    uiState = DeskUiState(hosts = listOf(host), buttons = listOf(button)),
                    onPairHost = {},
                    onManageHosts = {},
                    onAdminAccessRequest = { adminAccessRequested = true },
                    onRefresh = {},
                    onSelectHost = {},
                    onAppClick = {},
                    onAppClose = {},
                    onVolumeChange = {},
                    onVolumeChangeFinished = {},
                    onMuteClick = {},
                    onMicChange = {},
                    onMicChangeFinished = {},
                    onMicClick = {},
                    onClearFeedback = {},
                )
            }
        }
        composeRule.onNodeWithText("Mac Mini").assertIsDisplayed()
        composeRule.onNodeWithText("GitHub").assertIsDisplayed()
        composeRule.onNodeWithText("50%").assertIsDisplayed()
        composeRule.onNodeWithText("75%").assertIsDisplayed()
        composeRule.onNodeWithTag(DESK_ADMIN_BUTTON_TAG).assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertTrue(adminAccessRequested) }
    }

    @Test
    fun shortcutsButtonRequestsDedicatedPinnedAppsScreen() {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac Mini",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "token",
        )
        var catalogRequested = false
        composeRule.setContent {
            ControlDeskTheme {
                DeskScreen(
                    uiState = DeskUiState(hosts = listOf(host)),
                    onPairHost = {}, onManageHosts = {}, onManageShortcuts = { catalogRequested = true },
                    onRefresh = {}, onSelectHost = {}, onAppClick = {}, onAppClose = {},
                    onVolumeChange = {}, onVolumeChangeFinished = {}, onMuteClick = {},
                    onMicChange = {}, onMicChangeFinished = {}, onMicClick = {}, onClearFeedback = {},
                )
            }
        }
        composeRule.onNodeWithContentDescription("Adicionar app fixo").performClick()
        composeRule.runOnIdle { assertTrue(catalogRequested) }
    }

    @Test
    fun mainScreenReorganizeModeMakesWholeAppCardDraggableAndShowsSelection() {
        val host = PairedHost(
            id = "10.0.2.2:8741", name = "Mac Mini", host = "10.0.2.2", port = 8741,
            osType = OsType.MACOS, sortOrder = 0, token = "token",
        )
        val fixed = GridButton(
            id = "favorite-studio", label = "Android Studio", actionType = ActionType.REMOTE_LAUNCH,
            payload = mapOf("pinnedAppId" to "studio"), isFavoriteShortcut = true,
        )
        val running = GridButton(
            id = "app-safari", label = "Safari · GitHub", actionType = ActionType.REMOTE_LAUNCH,
            payload = mapOf("target" to "Safari"), isActive = true, isVisible = true, monitorIndex = 2,
        )
        var uiState by mutableStateOf(DeskUiState(hosts = listOf(host), buttons = listOf(fixed, running)))
        composeRule.setContent {
            ControlDeskTheme {
                DeskScreen(
                    uiState = uiState,
                    onPairHost = {}, onManageHosts = {},
                    onToggleReorganizeApps = { enabled -> uiState = uiState.copy(isReorganizingApps = enabled) },
                    onSelectAppForReorder = { appId, cellWidth, cellHeight ->
                        uiState = uiState.copy(
                            selectedAppIdForReorder = appId,
                            selectedAppDragStartIndex = uiState.buttons.indexOfFirst { it.id == appId },
                            selectedAppDragCellWidth = cellWidth,
                            selectedAppDragCellHeight = cellHeight,
                        )
                    },
                    onMoveSelectedAppBy = { deltaX, deltaY ->
                        uiState = uiState.copy(
                            selectedAppDragOffsetX = uiState.selectedAppDragOffsetX + deltaX,
                            selectedAppDragOffsetY = uiState.selectedAppDragOffsetY + deltaY,
                        )
                    },
                    onFinishAppReorderDrag = {
                        uiState = uiState.copy(
                            selectedAppIdForReorder = null,
                            selectedAppDragOffsetX = 0f,
                            selectedAppDragOffsetY = 0f,
                            selectedAppDragStartIndex = null,
                        )
                    },
                    onPreviewReorderApp = { appId, targetIndex ->
                        val currentIndex = uiState.buttons.indexOfFirst { it.id == appId }
                        if (currentIndex >= 0 && targetIndex in uiState.buttons.indices) {
                            uiState = uiState.copy(
                                buttons = uiState.buttons.toMutableList().apply {
                                    add(targetIndex, removeAt(currentIndex))
                                },
                            )
                        }
                    },
                    onRefresh = {}, onSelectHost = {}, onAppClick = {}, onAppClose = {},
                    onVolumeChange = {}, onVolumeChangeFinished = {}, onMuteClick = {},
                    onMicChange = {}, onMicChangeFinished = {}, onMicClick = {}, onClearFeedback = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Reorganizar apps").performClick()
        composeRule.onNodeWithContentDescription("Android Studio, segure e arraste qualquer parte para mover").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("GitHub, aplicativo ativo, monitor 2, segure e arraste qualquer parte para mover").assertIsDisplayed()
        composeRule.onNodeWithText("M2").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Android Studio, segure e arraste qualquer parte para mover")
            .performTouchInput {
                down(Offset(width - 2f, centerY))
                advanceEventTime(600)
                moveBy(Offset(width.toFloat() + 16f, 0f))
                up()
            }
        composeRule.runOnIdle {
            assertEquals(listOf(running.id, fixed.id), uiState.buttons.map { it.id })
        }
        composeRule.onNodeWithContentDescription("Android Studio, segure e arraste qualquer parte para mover").assertIsDisplayed()
        composeRule.onAllNodesWithText("Selecionado").assertCountEquals(0)
        composeRule.onNodeWithContentDescription("Concluir reorganização").performClick()
        composeRule.onAllNodesWithContentDescription("Android Studio, selecionado para mover").assertCountEquals(0)
    }

    @Test
    fun shortcutManagerShowsCatalogErrorAndRetryAction() {
        val host = PairedHost(
            id = "10.0.2.2:8741", name = "Mac Mini", host = "10.0.2.2", port = 8741,
            osType = OsType.MACOS, sortOrder = 0, token = "token",
        )
        var retryRequested = false
        composeRule.setContent {
            ControlDeskTheme {
                PinnedAppsScreen(
                    host = host,
                    state = PinnedAppsUiState(appsError = "Agente do Mac desatualizado"),
                    onBack = {},
                    onIntent = { retryRequested = true },
                )
            }
        }
        composeRule.onNodeWithText("Agente do Mac desatualizado").assertIsDisplayed()
        composeRule.onNodeWithText("Tentar novamente").performClick()
        composeRule.runOnIdle { assertTrue(retryRequested) }
    }

    @Test
    fun macFilePickerOffersDirectSelectionForAFile() {
        val shortcut = PinnedAppShortcut("studio", "Android Studio", "/Applications/Android Studio.app")
        val host = PairedHost(
            id = "10.0.2.2:8741", name = "Mac Mini", host = "10.0.2.2", port = 8741,
            osType = OsType.MACOS, sortOrder = 0, token = "token", pinnedApps = listOf(shortcut),
        )
        var selected: String? = null
        composeRule.setContent {
            ControlDeskTheme {
                PinnedAppsScreen(
                    host = host,
                    state = PinnedAppsUiState(
                        selectedAppId = shortcut.id,
                        fileListing = MacFileListing(
                            path = "/Users/test/Projects",
                            entries = listOf(MacFileEntry("sample-android-app", "/Users/test/Projects/sample-android-app", true)),
                        ),
                    ),
                    onBack = {},
                    onIntent = { intent -> if (intent is PinnedAppsIntent.SelectPath) selected = intent.path },
                )
            }
        }
        composeRule.onNodeWithText("O que deve abrir?").assertIsDisplayed()
        composeRule.onNodeWithText("Usar pasta").performClick()
        composeRule.runOnIdle { assertEquals("/Users/test/Projects/sample-android-app", selected) }
    }

    @Test
    fun pullToRefreshShowsProgressAndHidesItWhenFinished() {
        val host = PairedHost(
            id = "10.0.2.2:8741",
            name = "Mac Mini",
            host = "10.0.2.2",
            port = 8741,
            osType = OsType.MACOS,
            sortOrder = 0,
            token = "token",
        )
        var uiState by mutableStateOf(DeskUiState(hosts = listOf(host)))
        var refreshCalls = 0
        composeRule.setContent {
            ControlDeskTheme {
                DeskScreen(
                    uiState = uiState,
                    onPairHost = {},
                    onManageHosts = {},
                    onRefresh = {
                        refreshCalls++
                        uiState = uiState.copy(isLoadingApps = true)
                    },
                    onSelectHost = {},
                    onAppClick = {},
                    onAppClose = {},
                    onVolumeChange = {},
                    onVolumeChangeFinished = {},
                    onMuteClick = {},
                    onMicChange = {},
                    onMicChangeFinished = {},
                    onMicClick = {},
                    onClearFeedback = {},
                )
            }
        }

        composeRule.onNodeWithTag(DESK_PULL_REFRESH_TAG).performTouchInput {
            swipe(
                start = Offset(centerX, height * 0.2f),
                end = Offset(centerX, height * 0.8f),
                durationMillis = 500,
            )
        }

        composeRule.waitForIdle()
        composeRule.runOnIdle { assertEquals(1, refreshCalls) }
        composeRule.onNodeWithTag(DESK_PULL_REFRESH_INDICATOR_TAG).assertIsDisplayed()

        composeRule.runOnIdle { uiState = uiState.copy(isLoadingApps = false) }
        composeRule.waitForIdle()
        composeRule.onAllNodesWithTag(DESK_PULL_REFRESH_INDICATOR_TAG).assertCountEquals(0)
    }
}
