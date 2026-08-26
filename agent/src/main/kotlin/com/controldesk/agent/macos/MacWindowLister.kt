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
        "Spotlight", "JavaApplicationStub", "WindowManager", "Control Desk Agent",
    )

    fun warmUp() {
        Thread({
            while (true) {
                runCatching {
                    val windows = query()
                    cache.set(windows)
                    MacAppIconResolver.resolveMissing(windows.map { it.appName }, limit = 3)
                }
                Thread.sleep(400)
            }
        }, "window-lister").apply {
            isDaemon = true
            start()
        }
    }

    fun list(includeIcons: Boolean): List<RunningAppInfo> {
        val snapshot = cache.get().ifEmpty { query() }
        val merged = MacChromeTabs.mergeTabs(snapshot)
        return merged.map { app ->
            val cached = MacAppIconResolver.iconFor(app.appName)
            when {
                cached != null -> app.copy(iconBase64 = cached)
                includeIcons -> app.copy(iconBase64 = MacAppIconResolver.resolveNow(app.appName))
                else -> app
            }
        }
    }

    private fun query(): List<RunningAppInfo> {
        val raw = Shell.run("/usr/bin/osascript", "-l", "JavaScript", "-e", LIST_SCRIPT, timeoutMs = 1_400)
        if (raw.isBlank()) return cache.get()
        val parsed = runCatching { Json.parseToJsonElement(raw).jsonArray }.getOrNull() ?: return cache.get()
        val windows = parsed.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val appName = obj.str("appName") ?: return@mapIndexedNotNull null
            if (appName in ignored) return@mapIndexedNotNull null
            obj.str("bundlePath")?.let { MacAppIconResolver.remember(appName, it) }
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
                isActive = obj.bool("isActive"),
                isVisible = visible,
                monitorIndex = obj.int("monitorIndex")?.coerceAtLeast(1) ?: 1,
            )
        }
        return windows.sortedWith(
            compareByDescending<RunningAppInfo> { it.isActive }
                .thenBy { it.monitorIndex }
                .thenByDescending { it.isVisible }
                .thenBy { it.name.lowercase() },
        )
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean = this[key]?.jsonPrimitive?.booleanOrNull == true
    private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

    private val LIST_SCRIPT = """
        ObjC.import('AppKit');
        function screenRects() {
          var screens = $.NSScreen.screens;
          var n = Number(screens.count);
          var primaryH = Number(screens.objectAtIndex(0).frame.size.height);
          var out = [];
          for (var i = 0; i < n; i++) {
            var f = screens.objectAtIndex(i).frame;
            var x = Number(f.origin.x), y = Number(f.origin.y);
            var w = Number(f.size.width), h = Number(f.size.height);
            out.push({ index: i + 1, x: x, y: primaryH - y - h, w: w, h: h });
          }
          return out;
        }
        function monitorFor(px, py, pw, ph, screens) {
          var best = 1, bestArea = -1;
          for (var i = 0; i < screens.length; i++) {
            var s = screens[i];
            var ix = Math.max(px, s.x), iy = Math.max(py, s.y);
            var ax = Math.min(px + pw, s.x + s.w), ay = Math.min(py + ph, s.y + s.h);
            var area = Math.max(0, ax - ix) * Math.max(0, ay - iy);
            if (area > bestArea) { bestArea = area; best = s.index; }
          }
          return best;
        }
        function bundlePath(pid) {
          try {
            var app = $.NSRunningApplication.runningApplicationWithProcessIdentifier(pid);
            if (!app || !app.bundleURL) return '';
            return ObjC.unwrap(app.bundleURL.path) || '';
          } catch (e) { return ''; }
        }
        function run() {
          var screens = screenRects();
          var se = Application('System Events');
          var procs = se.applicationProcesses.whose({ backgroundOnly: false })();
          var rows = [];
          var n = 1;
          for (var i = 0; i < procs.length; i++) {
            var p = procs[i];
            var name = p.name();
            var front = false;
            var pid = 0;
            try { front = p.frontmost(); } catch (e) {}
            try { pid = p.unixId(); } catch (e) {}
            var path = bundlePath(pid);
            var windows = [];
            try { windows = p.windows(); } catch (e) { windows = []; }
            if (!windows || windows.length === 0) continue;
            for (var w = 0; w < windows.length; w++) {
              var title = '';
              var minimized = false;
              var pos = [0, 0], size = [0, 0];
              try { title = windows[w].name(); } catch (e) {}
              try { minimized = windows[w].valueOf().attributes.byName('AXMinimized').value(); } catch (e) {}
              try { pos = windows[w].position(); } catch (e) {}
              try { size = windows[w].size(); } catch (e) {}
              var px = Number(pos[0] || 0), py = Number(pos[1] || 0);
              var pw = Number(size[0] || 0), ph = Number(size[1] || 0);
              rows.push({
                appName: name,
                windowTitle: title,
                isActive: front && w === 0,
                visible: !minimized,
                minimized: minimized,
                windowIndex: w,
                axWindowIndex: w + 1,
                windowNumber: n++,
                monitorIndex: monitorFor(px, py, pw, ph, screens),
                bundlePath: path
              });
            }
          }
          return JSON.stringify(rows);
        }
    """.trimIndent()
}
