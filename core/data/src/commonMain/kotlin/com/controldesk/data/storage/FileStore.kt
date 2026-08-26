package com.controldesk.data.storage

interface FileStore {
    fun readText(name: String): String?
    fun writeText(name: String, content: String)
}

class MemoryFileStore : FileStore {
    private val files = mutableMapOf<String, String>()

    override fun readText(name: String): String? = files[name]

    override fun writeText(name: String, content: String) {
        files[name] = content
    }
}
