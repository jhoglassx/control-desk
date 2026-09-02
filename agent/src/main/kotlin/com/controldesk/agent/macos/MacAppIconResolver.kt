package com.controldesk.agent

import java.io.File
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Ícone do app **em execução**: `NSRunningApplication.bundleURL` +
 * `NSWorkspace.iconForFile` (o mesmo ícone do Dock/Finder).
 * PNG 72px em base64 vai no `RunningAppInfo.iconBase64` para o celular.
 */
object MacAppIconResolver {
    private val memory = ConcurrentHashMap<String, String>()
    private val paths = ConcurrentHashMap<String, String>()

    fun warmUp() {
        AgentPaths.icons
    }

    fun remember(appName: String, bundlePath: String) {
        if (appName.isBlank() || bundlePath.isBlank()) return
        paths[appName] = bundlePath
    }

    fun pathFor(appName: String): String? = paths[appName]

    fun iconFor(appName: String): String? {
        if (appName.isBlank()) return null
        memory[appName]?.let { return it }
        val disk = diskFile(appName)
        if (disk.isFile && disk.length() > 0) {
            val encoded = Base64.getEncoder().encodeToString(disk.readBytes())
            memory[appName] = encoded
            return encoded
        }
        return null
    }

    fun resolveMissing(appNames: List<String>, limit: Int) {
        appNames.distinct().asSequence()
            .filter { iconFor(it) == null }
            .take(limit)
            .forEach { resolveNow(it) }
    }

    fun resolveNow(appName: String): String? {
        iconFor(appName)?.let { return it }
        val bundle = paths[appName] ?: lookupBundle(appName) ?: return null
        paths[appName] = bundle
        val png = fromWorkspace(bundle) ?: fromIcns(bundle) ?: return null
        val encoded = Base64.getEncoder().encodeToString(png.readBytes())
        memory[appName] = encoded
        png.copyTo(diskFile(appName), overwrite = true)
        png.delete()
        return encoded
    }

    private fun lookupBundle(appName: String): String? {
        val path = Shell.run(
            "/usr/bin/osascript",
            "-l",
            "JavaScript",
            "-e",
            """ObjC.import('AppKit'); ObjC.unwrap($.NSWorkspace.sharedWorkspace.fullPathForApplication('${Shell.escape(appName)}')) || ''""",
            timeoutMs = 400,
        )
        return path.takeIf { it.startsWith("/") }
    }

    private fun fromIcns(bundlePath: String): File? {
        val resources = File(bundlePath, "Contents/Resources")
        if (!resources.isDirectory) return null
        val plistIcon = Shell.run(
            "/usr/bin/defaults",
            "read",
            File(bundlePath, "Contents/Info").absolutePath,
            "CFBundleIconFile",
            timeoutMs = 250,
        ).trim().trim('"')
        val candidates = listOfNotNull(
            plistIcon.takeIf { it.isNotBlank() }?.let { name ->
                File(resources, if (name.endsWith(".icns", true)) name else "$name.icns")
            },
            File(resources, "AppIcon.icns"),
            File(resources, "app.icns"),
            File(resources, "${File(bundlePath).nameWithoutExtension}.icns"),
        )
        val icns = candidates.firstOrNull { it.isFile } ?: return null
        return sipsPng(icns)
    }

    private fun fromWorkspace(bundlePath: String): File? {
        val tiff = File.createTempFile("cd-icon-", ".tiff")
        val wrote = Shell.run(
            "/usr/bin/osascript",
            "-l",
            "JavaScript",
            "-e",
            WORKSPACE_SCRIPT,
            bundlePath,
            tiff.absolutePath,
            timeoutMs = 900,
        )
        if (wrote != "ok" || !tiff.isFile || tiff.length() == 0L) {
            tiff.delete()
            return null
        }
        val png = sipsPng(tiff)
        tiff.delete()
        return png
    }

    private fun sipsPng(source: File): File? {
        val png = File.createTempFile("cd-icon-", ".png")
        Shell.run(
            "/usr/bin/sips",
            "-z", "72", "72",
            "-s", "format", "png",
            source.absolutePath,
            "--out", png.absolutePath,
            timeoutMs = 600,
        )
        if (!png.isFile || png.length() == 0L) {
            png.delete()
            return null
        }
        return png
    }

    private fun diskFile(appName: String): File {
        val safe = appName.replace(Regex("[^A-Za-z0-9._-]+"), "_").take(80).ifBlank { "app" }
        return File(AgentPaths.icons, "$safe.png")
    }

    private val WORKSPACE_SCRIPT = """
        function run(argv) {
          ObjC.import('AppKit');
          var src = $.NSWorkspace.sharedWorkspace.iconForFile(argv[0]);
          var reps = src.representations;
          var n = Number(reps.count);
          var chosen = null;
          var best = 99999;
          for (var i = 0; i < n; i++) {
            var r = reps.objectAtIndex(i);
            var w = Number(r.pixelsWide);
            if (w >= 72 && w < best) { chosen = r; best = w; }
          }
          if (!chosen && n > 0) chosen = reps.objectAtIndex(0);
          if (!chosen) return 'noicon';
          var img = $.NSImage.alloc.initWithSize($.NSMakeSize(72, 72));
          img.addRepresentation(chosen);
          img.TIFFRepresentation.writeToFileAtomically(argv[1], true);
          return 'ok';
        }
    """.trimIndent()
}
