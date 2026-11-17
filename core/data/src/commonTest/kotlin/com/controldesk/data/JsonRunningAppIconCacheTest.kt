package com.controldesk.data

import com.controldesk.data.storage.MemoryFileStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class JsonRunningAppIconCacheTest {
    @Test
    fun replaceReplacesPreviouslyCachedIconAndPersistsIt() = runTest {
        val store = MemoryFileStore()
        val cache = JsonRunningAppIconCache(store)

        cache.putIfAbsent("chatgpt", "broken-icon")
        cache.replace("chatgpt", "correct-icon")

        assertEquals("correct-icon", cache.peek("chatgpt"))
        assertEquals(mapOf("chatgpt" to "correct-icon"), cache.load())
        assertEquals("correct-icon", JsonRunningAppIconCache(store).load()["chatgpt"])
    }
}
