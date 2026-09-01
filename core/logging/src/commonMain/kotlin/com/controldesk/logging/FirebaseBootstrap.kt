package com.controldesk.logging

internal object FirebaseBootstrap {
    private val lock = Any()
    private var ready = false

    val isReady: Boolean
        get() = ready

    fun ensureInitialized(platformContext: Any? = null) {
        if (ready) return
        synchronized(lock) {
            if (ready) return
            runCatching { platformInitializeFirebase(platformContext) }
            .onSuccess { ready = true }
        }
    }
}

internal expect fun platformInitializeFirebase(platformContext: Any?)
