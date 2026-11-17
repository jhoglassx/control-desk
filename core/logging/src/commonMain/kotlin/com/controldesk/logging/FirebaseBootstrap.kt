package com.controldesk.logging

import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal object FirebaseBootstrap {
    private val ready = AtomicBoolean(false)
    private val initializing = AtomicBoolean(false)

    val isReady: Boolean
        get() = ready.load()

    fun ensureInitialized(platformContext: Any? = null) {
        if (ready.load() || !initializing.compareAndSet(false, true)) return
        try {
            runCatching { platformInitializeFirebase(platformContext) }
                .onSuccess { ready.store(true) }
        } finally {
            initializing.store(false)
        }
    }
}

internal expect fun platformInitializeFirebase(platformContext: Any?)
