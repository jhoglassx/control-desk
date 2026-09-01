package com.controldesk.app

import androidx.compose.ui.window.ComposeUIViewController
import com.controldesk.app.di.appModule
import com.controldesk.app.di.platformModule
import com.controldesk.logging.ControlDeskLog
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    ControlDeskLog.init()
    if (GlobalContext.getOrNull() == null) {
        startKoin {
            modules(appModule(), platformModule())
        }
    }
    return ComposeUIViewController { App() }
}
