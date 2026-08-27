package com.controldesk.agent

import com.controldesk.protocol.RunningAppInfo
import java.io.File
import java.net.URI
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

object MacDockApps {
    private val cache = AtomicReference<List<RunningAppInfo>>(emptyList())
    private val cachedAt = AtomicLong(0)
    private val ignored = setOf(
        "ControlDeskAgent", "Control Desk Agent", "Downloads", "Trash", "Launchpad",
    )
    private val labelRegex = Regex("""<key>file-label</key>\s*<string>([^<]*)</string>""")
    private val urlRegex = Regex("""<key>_CFURLString</key>\s*<string>([^<]*)</string>""")
    private val defaultsLabelRegex = Regex(""""file-label"\s*=\s*(?:"([^"]*)"|([^;]+))\s*;""")
    private val defaultsUrlRegex = Regex(""""_CFURLString"\s*=\s*"([^"]+)"""")

    fun merge(windows: List<RunningAppInfo>): List<RunningAppInfo> {
        val pins = pinned()
        if (pins.isEmpty()) return windows
        val byName = pins.associateBy { it.appName.lowercase() }
        val byPath = pins.mapNotNull { pin ->
            val path = MacAppIconResolver.pathFor(pin.appName)?.lowercase() ?: return@mapNotNull null
            path to pin
        }.toMap()
        val annotated = windows.map { app ->
            val pin = byName[app.appName.lowercase()]
                ?: MacAppIconResolver.pathFor(app.appName)?.lowercase()?.let { byPath[it] }
            if (pin == null) app else app.copy(isDockPinned = true, dockIndex = pin.dockIndex)
        }
        return annotated + extrasNotRunning(annotated, pins)
    }

    fun extrasNotRunning(windows: List<RunningAppInfo>): List<RunningAppInfo> =
        extrasNotRunning(windows, pinned())

    private fun extrasNotRunning(windows: List<RunningAppInfo>, pins: List<RunningAppInfo>): List<RunningAppInfo> {
        val runningNames = windows.map { it.appName.lowercase() }.toSet()
        val runningPaths = windows.mapNotNull { MacAppIconResolver.pathFor(it.appName)?.lowercase() }.toSet()
        return pins.filter { pin ->
            if (pin.appName in ignored) return@filter false
            if (pin.appName.lowercase() in runningNames) return@filter false
            val path = MacAppIconResolver.pathFor(pin.appName)?.lowercase()
            path == null || path !in runningPaths
        }
    }

    private fun pinned(): List<RunningAppInfo> {
        val now = System.currentTimeMillis()
        if (now - cachedAt.get() < 8_000L) {
            val hit = cache.get()
            if (hit.isNotEmpty()) return hit
        }
        val parsed = query()
        cache.set(parsed)
        cachedAt.set(now)
        return parsed
    }

    private fun query(): List<RunningAppInfo> {
        val plist = File(System.getProperty("user.home"), "Library/Preferences/com.apple.dock.plist")
        if (!plist.isFile) return cache.get()
        val xml = Shell.run(
            "/usr/bin/plutil",
            "-extract",
            "persistent-apps",
            "xml1",
            "-o",
            "-",
            plist.absolutePath,
            timeoutMs = 2_000,
        )
        val fromXml = parseXml(xml)
        if (fromXml.isNotEmpty()) return fromXml
        val fromDefaults = parseDefaults(
            Shell.run("/usr/bin/defaults", "read", "com.apple.dock", "persistent-apps", timeoutMs = 2_000),
        )
        return fromDefaults.ifEmpty { cache.get() }
    }

    internal fun parseXml(xml: String): List<RunningAppInfo> {
        if (xml.isBlank() || !xml.contains("file-label")) return emptyList()
        val blocks = xml.split("<key>GUID</key>").drop(1)
        if (blocks.isEmpty()) {
            val labels = labelRegex.findAll(xml).map { it.groupValues[1].unescapeXml() }.toList()
            val urls = urlRegex.findAll(xml).map { it.groupValues[1].unescapeXml() }.toList()
            return labels.mapIndexedNotNull { index, label -> toPinned(index, label, urls.getOrNull(index)) }
        }
        return blocks.mapIndexedNotNull { index, block ->
            val label = labelRegex.find(block)?.groupValues?.get(1)?.unescapeXml()
            val url = urlRegex.find(block)?.groupValues?.get(1)?.unescapeXml()
            toPinned(index, label, url)
        }
    }

    internal fun parseDefaults(raw: String): List<RunningAppInfo> {
        if (raw.isBlank()) return emptyList()
        val labels = defaultsLabelRegex.findAll(raw).map { match ->
            match.groupValues[1].ifBlank { match.groupValues[2] }.trim()
        }.toList()
        val urls = defaultsUrlRegex.findAll(raw).map { it.groupValues[1] }.toList()
        return labels.mapIndexedNotNull { index, label -> toPinned(index, label, urls.getOrNull(index)) }
    }

    private fun toPinned(index: Int, label: String?, url: String?): RunningAppInfo? {
        val path = url?.toFilePath()
        val appName = label?.takeIf { it.isNotBlank() }
            ?: path?.let { File(it).name.removeSuffix(".app") }
            ?: return null
        if (appName in ignored) return null
        if (path != null && !path.endsWith(".app", ignoreCase = true)) return null
        path?.let { MacAppIconResolver.remember(appName, it) }
        return RunningAppInfo(
            name = appName,
            appName = appName,
            isActive = false,
            isVisible = false,
            monitorIndex = 0,
            isDockPinned = true,
            dockIndex = index,
        )
    }

    private fun String.toFilePath(): String? {
        val decoded = runCatching { URI(this).path }.getOrNull() ?: return null
        return decoded.trimEnd('/')
    }

    private fun String.unescapeXml(): String = replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
