package com.controldesk.app.di

import android.content.Context
import com.controldesk.data.storage.FileStore

class AndroidFileStore(
    context: Context,
) : FileStore {
    private val dir = context.applicationContext.filesDir

    override fun readText(name: String): String? {
        val file = dir.resolve(name)
        return if (file.exists()) file.readText() else null
    }

    override fun writeText(name: String, content: String) {
        dir.resolve(name).writeText(content)
    }
}
