package com.controldesk.logging

import co.touchlab.kermit.Logger
import co.touchlab.kermit.platformLogWriter

internal actual fun installPlatformLogWriters() {
    Logger.setLogWriters(
        buildList {
            add(platformLogWriter())
            if (FirebaseBootstrap.isReady) add(GitLiveCrashlyticsLogWriter())
        },
    )
}
