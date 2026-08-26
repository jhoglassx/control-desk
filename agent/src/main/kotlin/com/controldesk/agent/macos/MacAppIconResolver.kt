package com.controldesk.agent

object MacAppIconResolver {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun warmUp() = Unit

    fun iconFor(appName: String): String? {
        if (appName.isBlank()) return null
        cache[appName]?.let { return it }
        val path = Shell.osascript(
            """POSIX path of (path to application "${Shell.escape(appName)}")""",
            timeoutMs = 600,
        )
        if (path.isBlank()) return null
        val icns = java.io.File(path, "Contents/Resources")
            .takeIf { it.isDirectory }
            ?.listFiles { file -> file.extension.equals("icns", true) }
            ?.firstOrNull()
            ?: return null
        val png = java.io.File.createTempFile("cd-icon-", ".png")
        val converted = Shell.run(
            "/usr/bin/sips", "-s", "format", "png", icns.absolutePath, "--out", png.absolutePath,
            timeoutMs = 800,
        )
        if (!png.isFile || png.length() == 0L) {
            png.delete()
            return null
        }
        val base64 = java.util.Base64.getEncoder().encodeToString(png.readBytes())
        png.delete()
        if (converted.isNotBlank() || base64.isNotBlank()) {
            cache[appName] = base64
            return base64
        }
        return null
    }
}
