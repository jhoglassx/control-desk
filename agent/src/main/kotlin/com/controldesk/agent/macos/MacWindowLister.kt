package com.controldesk.agent

import com.controldesk.protocol.RunningAppInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicReference

object MacWindowLister {
    private val cache = AtomicReference<List<RunningAppInfo>>(emptyList())
    private val ignored = setOf(
        "ControlDeskAgent", "AutoFill", "coreauthd", "loginwindow", "Wallpaper",
        "Window Server", "SystemUIServer", "Dock", "Control Center", "NotificationCenter",
        "Finder", "Spotlight", "JavaApplicationStub",
    )

    fun warmUp() {
        Thread({
            while (true) {
                runCatching { cache.set(query(includeIcons = false)) }
                Thread.sleep(900)
            }
        }, "window-lister").apply {
            isDaemon = true
            start()
        }
    }

    fun list(includeIcons: Boolean): List<RunningAppInfo> {
        val snapshot = cache.get()
        val apps = if (snapshot.isNotEmpty()) snapshot else query(includeIcons = false)
        if (!includeIcons) return apps
        return apps.take(32).map { app ->
            if (!app.iconBase64.isNullOrBlank()) app
            else app.copy(iconBase64 = MacAppIconResolver.iconFor(app.appName))
        } + apps.drop(32)
    }

    private fun query(includeIcons: Boolean): List<RunningAppInfo> {
        val raw = Shell.run("/usr/bin/osascript", "-l", "JavaScript", "-e", LIST_SCRIPT, timeoutMs = 1_200)
        if (raw.isBlank()) return cache.get()
        val parsed = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return cache.get()
        val windows = parsed.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val appName = obj.str("appName") ?: return@mapIndexedNotNull null
            if (appName in ignored) return@mapIndexedNotNull null
            val title = obj.str("windowTitle").orEmpty()
            val minimized = obj.bool("minimized")
            val visible = obj.bool("visible") && !minimized
            val label = when {
                title.isNotBlank() && title != appName -> "$appName · $title"
                minimized -> "$appName · min"
                else -> appName
            }
            RunningAppInfo(
                name = label,
                appName = appName,
                windowTitle = title.takeIf { it.isNotBlank() },
                windowIndex = obj.int("windowIndex") ?: index,
                axWindowIndex = obj.int("axWindowIndex"),
                windowNumber = obj.int("windowNumber") ?: (index + 1),
                iconBase64 = if (includeIcons) MacAppIconResolver.iconFor(appName) else null,
                isActive = obj.bool("isActive"),
                isVisible = visible,
                monitorIndex = obj.int("monitorIndex") ?: 1,
            )
        }
        val withChrome = MacChromeTabs.mergeTabs(windows)
        return withChrome.sortedWith(
            compareByDescending<RunningAppInfo> { it.isActive }
                .thenByDescending { it.isVisible }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull == true
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private val LIST_SCRIPT = """
        function run() {
          var se = Application('System Events');
          var procs = se.applicationProcesses.whose({ backgroundOnly: false })();
          var rows = [];
          var n = 1;
          for (var i = 0; i < procs.length; i++) {
            var p = procs[i];
            var name = p.name();
            var front = false;
            try { front = p.frontmost(); } catch (e) {}
            var windows = [];
            try { windows = p.windows(); } catch (e) { windows = []; }
            if (!windows || windows.length === 0) continue;
            for (var w = 0; w < windows.length; w++) {
              var title = '';
              var minimized = false;
              try { title = windows[w].name(); } catch (e) {}
              try { minimized = windows[w].valueOf().attributes.byName('AXMinimized').value(); } catch (e) {}
              rows.push({
                appName: name,
                windowTitle: title,
                isActive: front && w === 0,
                visible: !minimized,
                minimized: minimized,
                windowIndex: w,
                axWindowIndex: w + 1,
                windowNumber: n++,
                monitorIndex: 1
              });
            }
          }
          return JSON.stringify(rows);
        }
    """.trimIndent()
}
