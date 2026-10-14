package com.controldesk.agent.macos

import com.controldesk.protocol.BrowseFileEntry
import com.controldesk.protocol.BrowseFilesResponse
import java.io.File

internal object MacFileBrowser {
    private const val MAX_ENTRIES = 200

    fun list(path: String?): BrowseFilesResponse {
        val home = File(System.getProperty("user.home")).canonicalFile
        val directory = File(path?.takeIf { it.isNotBlank() } ?: home.path).canonicalFile
        require(directory.toPath().startsWith(home.toPath())) { "Navegação limitada à pasta do usuário no Mac" }
        require(directory.isDirectory && directory.canRead()) { "Esta pasta não pode ser aberta" }

        val entries = directory.listFiles().orEmpty()
            .asSequence()
            .filterNot { it.name.startsWith(".") }
            .sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            .take(MAX_ENTRIES)
            .map { file ->
                BrowseFileEntry(
                    name = file.name,
                    path = file.canonicalPath,
                    isDirectory = file.isDirectory,
                )
            }
            .toList()
        val parent = directory.parentFile?.canonicalFile?.takeIf { it.toPath().startsWith(home.toPath()) }?.path
        return BrowseFilesResponse(path = directory.path, parentPath = parent, entries = entries)
    }
}
