package com.controldesk.app.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@OptIn(ExperimentalEncodingApi::class)
actual fun decodeBase64Image(base64: String): ImageBitmap? {
    return runCatching {
        val cleaned = base64.substringAfter("base64,").replace("\\s".toRegex(), "")
        val bytes = Base64.decode(cleaned)
        Image.makeFromEncoded(bytes).toComposeImageBitmap()
    }.getOrNull()
}
