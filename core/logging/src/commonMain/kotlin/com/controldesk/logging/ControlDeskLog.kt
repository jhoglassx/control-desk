package com.controldesk.logging

import co.touchlab.kermit.Logger

/**
 * Logger KMP (Kermit) com Firebase Crashlytics (GitLive) em Android, iOS e agente JVM.
 *
 * ```
 * ControlDeskLog.init() // iOS / agente
 * ControlDeskLog.init(applicationContext) // Android
 * ControlDeskLog.app.e(error) { "falha" }
 * ```
 */
object ControlDeskLog {
    private var initialized = false

    val app: Logger get() = Logger.withTag("app")
    val agent: Logger get() = Logger.withTag("agent")
    val gateway: Logger get() = Logger.withTag("gateway")
    val install: Logger get() = Logger.withTag("install")

    fun init(platformContext: Any? = null) {
        if (initialized) return
        initialized = true
        FirebaseBootstrap.ensureInitialized(platformContext)
        installPlatformLogWriters()
    }
}

internal expect fun installPlatformLogWriters()
