package com.controldesk.agent.macos

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MacFileBrowserTest {
    @Test
    fun listsVisibleFoldersBeforeFilesAndHidesHiddenEntries() {
        val root = kotlin.io.path.createTempDirectory("control-desk-browser").toFile()
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", root.absolutePath)
            File(root, "z-file.txt").writeText("file")
            File(root, "A Folder").mkdir()
            File(root, ".hidden").mkdir()

            val result = MacFileBrowser.list(null)

            assertEquals(root.canonicalPath, result.path)
            assertEquals(listOf("A Folder", "z-file.txt"), result.entries.map { it.name })
            assertTrue(result.entries.first().isDirectory)
            assertEquals(null, result.parentPath)
        } finally {
            if (previousHome == null) System.clearProperty("user.home") else System.setProperty("user.home", previousHome)
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesPathsOutsideUserHome() {
        val root = kotlin.io.path.createTempDirectory("control-desk-browser").toFile()
        val outside = kotlin.io.path.createTempDirectory("control-desk-outside").toFile()
        val previousHome = System.getProperty("user.home")
        try {
            System.setProperty("user.home", root.absolutePath)
            assertFailsWith<IllegalArgumentException> { MacFileBrowser.list(outside.absolutePath) }
        } finally {
            if (previousHome == null) System.clearProperty("user.home") else System.setProperty("user.home", previousHome)
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }
}
