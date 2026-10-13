package com.controldesk.agent

import com.controldesk.logging.ControlDeskLog
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer

object MacAccessibility {
    private interface ApplicationServices : Library {
        // AXIsProcessTrusted returns Apple's one-byte Boolean type.
        fun AXIsProcessTrusted(): Byte
        fun AXIsProcessTrustedWithOptions(options: Pointer): Byte
    }

    private interface CoreFoundation : Library {
        fun CFDictionaryCreateMutable(
            allocator: Pointer?,
            capacity: Long,
            keyCallBacks: Pointer?,
            valueCallBacks: Pointer?,
        ): Pointer

        fun CFDictionarySetValue(dictionary: Pointer, key: Pointer, value: Pointer)
        fun CFRelease(value: Pointer)
    }

    private const val APPLICATION_SERVICES =
        "/System/Library/Frameworks/ApplicationServices.framework/ApplicationServices"
    private const val CORE_FOUNDATION =
        "/System/Library/Frameworks/CoreFoundation.framework/CoreFoundation"

    private val applicationServices by lazy {
        Native.load(APPLICATION_SERVICES, ApplicationServices::class.java)
    }
    private val coreFoundation by lazy { Native.load(CORE_FOUNDATION, CoreFoundation::class.java) }

    fun isTrusted(): Boolean = runCatching {
        applicationServices.AXIsProcessTrusted().toInt() != 0
    }.onFailure {
        ControlDeskLog.agent.w(it) { "Could not query macOS Accessibility permission" }
    }.getOrDefault(false)

    /** Asks macOS to present the native Accessibility permission flow for this app. */
    fun requestAccess() {
        if (isTrusted()) return
        runCatching {
            val appServicesSymbols = NativeLibrary.getInstance(APPLICATION_SERVICES)
            val coreFoundationSymbols = NativeLibrary.getInstance(CORE_FOUNDATION)
            val promptKey = appServicesSymbols.getGlobalVariableAddress("kAXTrustedCheckOptionPrompt").getPointer(0)
            val trueValue = coreFoundationSymbols.getGlobalVariableAddress("kCFBooleanTrue").getPointer(0)
            val options = coreFoundation.CFDictionaryCreateMutable(null, 1, null, null)
            try {
                coreFoundation.CFDictionarySetValue(options, promptKey, trueValue)
                applicationServices.AXIsProcessTrustedWithOptions(options)
            } finally {
                coreFoundation.CFRelease(options)
            }
        }.onFailure {
            ControlDeskLog.agent.w(it) { "Could not request macOS Accessibility permission" }
        }
    }

    fun warnIfNeeded() {
        if (!isTrusted()) {
            val message =
                "Acessibilidade desligada. Ajustes → Privacidade e Segurança → Acessibilidade → ControlDeskAgent (ou java/Terminal em dev)."
            ControlDeskLog.agent.w { message }
        }
    }

    fun openPrivacySettings() {
        Shell.run(
            "/usr/bin/open",
            "x-apple.systempreferences:com.apple.preference.security?Privacy_Accessibility",
            timeoutMs = 2_000,
        )
    }
}
