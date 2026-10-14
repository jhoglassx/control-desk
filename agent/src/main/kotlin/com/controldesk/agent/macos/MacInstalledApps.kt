package com.controldesk.agent

import com.controldesk.protocol.InstalledAppInfo
import java.io.File

/** Small installed-app catalog used by mobile shortcut management. */
object MacInstalledApps {
    fun list(home: String = System.getProperty("user.home")): List<InstalledAppInfo> =
        list(home, MacAppIconResolver::resolveForBundle)

    internal fun list(
        home: String,
        resolveIcon: (appName: String, bundlePath: String) -> String?,
    ): List<InstalledAppInfo> {
        val roots = listOf(File("/Applications"), File("/System/Applications"), File(home, "Applications"))
        return roots.flatMap { root ->
            if (!root.isDirectory) return@flatMap emptyList()
            root.walkTopDown()
                .maxDepth(3)
                .filter { it.isDirectory && it.extension.equals("app", ignoreCase = true) }
                .map { app ->
                    val name = app.name.removeSuffix(".app")
                    InstalledAppInfo(name, app.absolutePath, resolveIcon(name, app.absolutePath))
                }
                .toList()
        }
            .distinctBy { it.bundlePath }
            .sortedBy { it.name.lowercase() }
    }
}
