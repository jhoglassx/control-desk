package com.controldesk.logging

internal actual fun platformInitializeFirebase(platformContext: Any?) {
    GoogleServicesConfig.load() ?: error("google-services.json ausente no classpath do agente")
}
