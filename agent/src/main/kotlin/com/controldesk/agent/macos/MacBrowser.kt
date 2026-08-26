package com.controldesk.agent

object MacBrowser {
    fun open(url: String, browser: String?) {
        val app = mapped(browser)
        if (app != null) Shell.spawn("/usr/bin/open", "-a", app, url) else Shell.spawn("/usr/bin/open", url)
    }

    fun close(browser: String?) {
        val app = mapped(browser) ?: "Google Chrome"
        Shell.osascript("""tell application "$app" to quit""", timeoutMs = 800)
    }

    private fun mapped(browser: String?): String? = when (browser?.lowercase()) {
        null, "", "chrome" -> "Google Chrome"
        "safari" -> "Safari"
        "firefox" -> "Firefox"
        "edge" -> "Microsoft Edge"
        else -> browser
    }
}
