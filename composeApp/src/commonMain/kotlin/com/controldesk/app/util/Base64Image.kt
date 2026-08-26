package com.controldesk.app.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeBase64Image(base64: String): ImageBitmap?

@Composable
fun rememberBase64Image(base64: String?): ImageBitmap? {
    return remember(base64) {
        base64?.takeIf { it.isNotBlank() }?.let(::decodeBase64Image)
    }
}
