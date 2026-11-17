package com.controldesk.app.di

import android.content.Context
import com.controldesk.data.storage.FileStore
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class AndroidFileStore(
    context: Context,
) : FileStore {
    private val dir = context.applicationContext.filesDir

    override fun readText(name: String): String? {
        val file = dir.resolve(name)
        return if (file.exists()) file.readText() else null
    }

    override fun writeText(name: String, content: String) {
        val destination = dir.resolve(name)
        val temporary = dir.resolve("$name.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temporary.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (unsupported: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
