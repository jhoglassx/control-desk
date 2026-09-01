package com.controldesk.logging

internal actual fun platformInitializeFirebase(platformContext: Any?) {
    // Firebase/Crashlytics no iOS é configurado no Xcode (SPM + iOSApp.swift).
    // Builds iOS via Gradle ainda exigem Xcode instalado, mas não CocoaPods no sync.
}
