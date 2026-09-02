package com.controldesk.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import com.controldesk.app.panel.LocalPanelMode
import com.controldesk.app.panel.PanelModeConfig
import com.controldesk.app.panel.applyPanelDisplayOnFocus
import com.controldesk.app.panel.enablePanelDisplay
import com.controldesk.app.panel.isHomeLauncherIntent

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        enablePanelDisplay()
        val panelMode = PanelModeConfig(
            enabled = true,
            isDefaultLauncher = isHomeLauncherIntent(),
        )
        setContent {
            CompositionLocalProvider(LocalPanelMode provides panelMode) {
                App()
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyPanelDisplayOnFocus()
        }
    }
}
