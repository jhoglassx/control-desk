package com.controldesk.app.panel

import android.content.Intent
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

fun ComponentActivity.enablePanelDisplay() {
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(window, false)
    hideSystemBars()
}

fun ComponentActivity.applyPanelDisplayOnFocus() {
    hideSystemBars()
}

fun ComponentActivity.isHomeLauncherIntent(): Boolean {
    val categories = intent?.categories ?: return false
    return intent?.action == Intent.ACTION_MAIN && Intent.CATEGORY_HOME in categories
}

private fun ComponentActivity.hideSystemBars() {
    val controller = WindowInsetsControllerCompat(window, window.decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        window.attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }
}
