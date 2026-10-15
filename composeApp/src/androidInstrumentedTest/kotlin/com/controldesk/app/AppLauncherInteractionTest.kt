package com.controldesk.app

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import com.controldesk.app.theme.ControlDeskTheme
import com.controldesk.app.ui.desk.components.AppLauncherTile
import com.controldesk.domain.model.ActionType
import com.controldesk.domain.model.GridButton
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AppLauncherInteractionTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun unchangedShortcutIdUsesCurrentWindowForTapAndLongPress() {
        val state = mutableStateOf(GridButton("persistent-slot", "Editor", ActionType.REMOTE_LAUNCH,
            payload = mapOf("stableWindowId" to "101")))
        var focused: String? = null
        var closed: String? = null
        composeRule.setContent {
            val button = state.value
            ControlDeskTheme {
                AppLauncherTile(
                    button = button,
                    compact = false,
                    onClick = { focused = button.payload["stableWindowId"] },
                    onLongClick = { closed = button.payload["stableWindowId"] },
                    modifier = Modifier.size(160.dp).testTag("persistent-slot"),
                )
            }
        }
        composeRule.runOnIdle { state.value = state.value.copy(payload = mapOf("stableWindowId" to "202")) }
        composeRule.onNodeWithTag("persistent-slot").performTouchInput { click() }
        composeRule.onNodeWithTag("persistent-slot").performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals("202", focused)
            assertEquals("202", closed)
        }
    }
}
