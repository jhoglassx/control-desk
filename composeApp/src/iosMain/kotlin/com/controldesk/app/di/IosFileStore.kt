package com.controldesk.app.di

import com.controldesk.data.storage.FileStore
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUserDomainMask
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile

class IosFileStore : FileStore {
    private val documentsDir: String = NSSearchPathForDirectoriesInDomains(
        NSDocumentDirectory,
        NSUserDomainMask,
        true,
    ).first() as String

    private fun path(name: String): String = "$documentsDir/$name"

    override fun readText(name: String): String? =
        NSString.stringWithContentsOfFile(path(name), encoding = NSUTF8StringEncoding, error = null)

    override fun writeText(name: String, content: String) {
        (content as NSString).writeToFile(
            path(name),
            atomically = true,
            encoding = NSUTF8StringEncoding,
            error = null,
        )
    }
}
