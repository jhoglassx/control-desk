package com.controldesk.domain.usecase

import com.controldesk.domain.model.GridButton
import com.controldesk.domain.model.OsType
import com.controldesk.domain.model.PairedHost
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.repository.PairHostRemote

class PairHostUseCase(
    private val remote: PairHostRemote,
    private val preferences: HostPreferences,
) {
    suspend operator fun invoke(
        name: String,
        host: String,
        port: String,
        pin: String,
        osType: OsType,
    ): Result<PairedHost> {
        val trimmedName = name.trim()
        val trimmedHost = host.trim()
        val trimmedPin = pin.trim()
        if (trimmedName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Nome é obrigatório"))
        }
        if (trimmedHost.isEmpty()) {
            return Result.failure(IllegalArgumentException("IP / hostname é obrigatório"))
        }
        val portInt = port.trim().toIntOrNull()
            ?: return Result.failure(IllegalArgumentException("Porta inválida"))
        if (trimmedPin.length < 4) {
            return Result.failure(IllegalArgumentException("PIN deve ter pelo menos 4 caracteres"))
        }
        return runCatching {
            val token = remote.pair(trimmedHost, portInt, trimmedPin)
            val hosts = preferences.getHosts()
            val id = "$trimmedHost:$portInt"
            val existing = hosts.find { it.id == id }
            val paired = PairedHost(
                id = id,
                name = trimmedName,
                host = trimmedHost,
                port = portInt,
                osType = osType,
                sortOrder = existing?.sortOrder ?: hosts.size,
                token = token,
                buttons = existing?.buttons ?: DefaultButtonTemplates.forNewHost(),
            )
            preferences.saveHost(paired)
            paired
        }
    }
}

object DefaultButtonTemplates {
    fun forNewHost(): List<GridButton> = listOf(
        GridButton(
            id = "tpl-vol-up",
            label = "VOL+",
            actionType = com.controldesk.domain.model.ActionType.REMOTE_VOLUME,
            payload = mapOf("delta" to "5"),
        ),
        GridButton(
            id = "tpl-mic",
            label = "MIC",
            actionType = com.controldesk.domain.model.ActionType.REMOTE_MIC_MUTE,
            payload = mapOf("toggle" to "true"),
        ),
    )
}
