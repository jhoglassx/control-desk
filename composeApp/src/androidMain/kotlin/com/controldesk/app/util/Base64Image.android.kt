package com.controldesk.app.util

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeBase64Image(base64: String): ImageBitmap? {
    return runCatching {
        val cleaned = base64.substringAfter("base64,").replace("\\s".toRegex(), "")
        val bytes = Base64.decode(cleaned, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }.getOrNull()
}
