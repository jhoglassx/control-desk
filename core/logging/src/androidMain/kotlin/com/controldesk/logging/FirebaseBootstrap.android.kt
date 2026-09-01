package com.controldesk.logging

import android.content.Context
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.initialize

internal actual fun platformInitializeFirebase(platformContext: Any?) {
    val context = platformContext as? Context
        ?: error("ControlDeskLog.init(context) requires Application context on Android")
    Firebase.initialize(context)
}
