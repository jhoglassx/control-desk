package com.controldesk.agent

import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class AgentAuthStore {
    private val lock = ReentrantLock()
    private val random = SecureRandom()

    init {
        if (currentPin() == null) {
            activatePin()
        }
    }

    fun currentPin(): String? = lock.withLock {
        AgentPaths.pin.takeIf { it.isFile && !isPinExpired() }
            ?.readText()?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun activatePin(): String = lock.withLock {
        val pin = (100000 + random.nextInt(900000)).toString()
        AgentPaths.pin.writeText(pin)
        pin
    }

    fun tokens(): Set<String> = lock.withLock {
        if (!AgentPaths.tokens.isFile) emptySet()
        else AgentPaths.tokens.readLines().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun isValidToken(token: String): Boolean {
        val trimmed = token.trim()
        return trimmed.isNotEmpty() && trimmed in tokens()
    }

    fun pair(pin: String): String? = lock.withLock {
        val active = currentPin()
        if (active.isNullOrEmpty() || active != pin.trim()) return@withLock null
        val token = UUID.randomUUID().toString().replace("-", "")
        AgentPaths.tokens.appendText("$token\n")
        token
    }

    fun revokeAllTokens(): Int = lock.withLock {
        val count = tokens().size
        AgentPaths.tokens.writeText("")
        count
    }

    private fun isPinExpired(): Boolean {
        val ageMillis = System.currentTimeMillis() - AgentPaths.pin.lastModified()
        return ageMillis < 0 || ageMillis >= PIN_TTL_MS
    }

    companion object {
        const val PIN_TTL_MS = 10 * 60 * 1000L
    }
}
