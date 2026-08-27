package com.controldesk.agent

object MacAppQuit {
    private val blocked = setOf(
        "Finder", "Dock", "SystemUIServer", "Window Server", "Control Center",
        "NotificationCenter", "loginwindow", "Wallpaper", "Spotlight",
        "ControlDeskAgent", "Control Desk Agent",
    )

    fun quit(appName: String) {
        val app = appName.trim()
        if (app.isEmpty() || app in blocked) return
        val name = Shell.escape(app)
        Shell.spawn(
            "/usr/bin/osascript",
            "-l",
            "JavaScript",
            "-e",
            """
            ObjC.import('AppKit');
            var original = "$name";
            var needle = original.toLowerCase();
            var apps = $.NSWorkspace.sharedWorkspace.runningApplications;
            var n = Number(apps.count);
            var hit = false;
            for (var i = 0; i < n; i++) {
              var a = apps.objectAtIndex(i);
              if (Number(a.activationPolicy) !== 0) continue;
              var loc = (ObjC.unwrap(a.localizedName) || '').toLowerCase();
              var path = '';
              try { path = ObjC.unwrap(a.bundleURL.path) || ''; } catch (e) {}
              var base = path.split('/').pop().replace(/\.app${'$'}/i, '').toLowerCase();
              if (loc !== needle && base !== needle) continue;
              a.terminate();
              hit = true;
            }
            if (!hit) {
              try { Application(original).quit(); } catch (e) {}
            }
            """.trimIndent(),
        )
    }
}
