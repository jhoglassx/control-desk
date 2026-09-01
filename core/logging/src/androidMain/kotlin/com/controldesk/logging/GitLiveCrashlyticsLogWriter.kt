package com.controldesk.logging

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.crashlytics.crashlytics

internal class GitLiveCrashlyticsLogWriter : LogWriter() {
    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        if (!FirebaseBootstrap.isReady) return
        val line = "[$tag] $message"
        when (severity) {
            Severity.Verbose, Severity.Debug -> return
            Severity.Info -> Firebase.crashlytics.log(line)
            Severity.Warn -> {
                Firebase.crashlytics.log(line)
                throwable?.let { Firebase.crashlytics.recordException(it) }
            }
            Severity.Error, Severity.Assert -> {
                Firebase.crashlytics.log(line)
                if (throwable != null) {
                    Firebase.crashlytics.recordException(throwable)
                } else {
                    Firebase.crashlytics.recordException(RemoteLogException(line))
                }
            }
        }
    }
}

internal class RemoteLogException(message: String) : Exception(message)
