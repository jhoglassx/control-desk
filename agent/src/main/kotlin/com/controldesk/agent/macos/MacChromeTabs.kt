package com.controldesk.agent

import com.controldesk.protocol.BrowserTabSwitchRequest
import com.controldesk.protocol.RunningAppInfo
import java.util.concurrent.atomic.AtomicReference

object MacChromeTabs {
    private val cache = AtomicReference<List<RunningAppInfo>>(emptyList())

    fun warmUp() {
        Thread({
            while (true) {
                runCatching { cache.set(listTabs()) }
                Thread.sleep(1_200)
            }
        }, "chrome-tabs").apply {
            isDaemon = true
            start()
        }
    }

    fun mergeTabs(windows: List<RunningAppInfo>): List<RunningAppInfo> {
        val chromeWindows = windows.filter { it.appName.contains("Chrome", ignoreCase = true) }
        if (chromeWindows.isEmpty()) return windows
        val tabs = cache.get()
        if (tabs.isEmpty()) return windows
        val others = windows.filterNot { it.appName.contains("Chrome", ignoreCase = true) }
        val orderedChrome = chromeWindows.sortedBy { it.windowIndex ?: 0 }
        val monitorByWindow = orderedChrome.mapIndexed { index, window -> (index + 1) to window.monitorIndex }.toMap()
        val activeChromeWindow = orderedChrome.indexOfFirst { it.isActive }.takeIf { it >= 0 }?.plus(1)
        val icon = MacAppIconResolver.iconFor("Google Chrome")
        val mapped = tabs.map { tab ->
            val windowIndex = tab.browserWindowIndex ?: 1
            tab.copy(
                monitorIndex = monitorByWindow[windowIndex] ?: chromeWindows.first().monitorIndex,
                iconBase64 = icon ?: tab.iconBase64,
                isActive = activeChromeWindow != null &&
                    windowIndex == activeChromeWindow &&
                    (tab.windowIndex ?: 1) == 1,
            )
        }
        return others + mapped
    }

    fun switchTab(request: BrowserTabSwitchRequest) {
        val browser = mappedBrowser(request.browser)
        when (request.action) {
            "next" -> Shell.osascript("""tell application "$browser" to tell front window to set active tab index to (active tab index) + 1""", timeoutMs = 400)
            "prev" -> Shell.osascript("""tell application "$browser" to tell front window to set active tab index to (active tab index) - 1""", timeoutMs = 400)
            else -> {
                val title = request.value?.trim().orEmpty()
                if (title.isNotEmpty()) {
                    Shell.osascript(
                        """
                        tell application "$browser"
                            set wins to windows
                            repeat with w in wins
                                set i to 1
                                repeat with t in tabs of w
                                    if title of t contains "${Shell.escape(title)}" then
                                        set index of w to 1
                                        set active tab index of w to i
                                        activate
                                        return
                                    end if
                                    set i to i + 1
                                end repeat
                            end repeat
                        end tell
                        """.trimIndent(),
                        timeoutMs = 500,
                    )
                }
                Shell.spawn("/usr/bin/open", "-a", browser)
            }
        }
    }

    private fun listTabs(): List<RunningAppInfo> {
        val raw = Shell.osascript(
            """
            tell application "Google Chrome"
                if (count of windows) is 0 then return ""
                set out to ""
                set wIndex to 0
                repeat with w in windows
                    set tIndex to 0
                    set wIndex to wIndex + 1
                    repeat with t in tabs of w
                        set tIndex to tIndex + 1
                        set out to out & title of t & tab & URL of t & tab & wIndex & tab & tIndex & linefeed
                    end repeat
                end repeat
                return out
            end tell
            """.trimIndent(),
            timeoutMs = 700,
        )
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence().mapIndexedNotNull { index, line ->
            val parts = line.split('\t')
            if (parts.size < 2) return@mapIndexedNotNull null
            val title = parts[0]
            RunningAppInfo(
                name = "Chrome · $title",
                appName = "Google Chrome",
                windowTitle = title,
                windowIndex = parts.getOrNull(3)?.toIntOrNull() ?: index,
                windowNumber = 10_000 + index,
                isActive = false,
                isVisible = true,
                browserWindowIndex = parts.getOrNull(2)?.toIntOrNull(),
                isBrowserTab = true,
                monitorIndex = 1,
            )
        }.toList()
    }

    private fun mappedBrowser(name: String): String = when (name.lowercase()) {
        "safari" -> "Safari"
        "firefox" -> "Firefox"
        "edge" -> "Microsoft Edge"
        else -> "Google Chrome"
    }
}
