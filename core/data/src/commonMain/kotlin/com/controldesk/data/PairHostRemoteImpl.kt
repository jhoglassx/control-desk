package com.controldesk.data

import com.controldesk.domain.error.ControlDeskError
import com.controldesk.domain.repository.PairHostRemote
import com.controldesk.protocol.AgentApiClient
import com.controldesk.protocol.agentBaseUrl
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import io.ktor.util.network.UnresolvedAddressException

class PairHostRemoteImpl(
    private val api: AgentApiClient,
) : PairHostRemote {
    override suspend fun pair(host: String, port: Int, pin: String): String {
        val baseUrl = agentBaseUrl(host, port)
        try {
            api.health(baseUrl)
            val response = api.pair(baseUrl, pin)
            val token = response.token
            if (!response.ok || token.isNullOrBlank()) {
                throw ControlDeskError.Unauthorized(response.message ?: "PIN inválido")
            }
            return token
        } catch (error: ControlDeskError) {
            throw error
        } catch (error: ResponseException) {
            if (error.response.status == HttpStatusCode.Unauthorized) {
                throw ControlDeskError.Unauthorized("PIN inválido")
            }
            throw ControlDeskError.Generic(error.message ?: "Falha no pareamento")
        } catch (error: Throwable) {
            val offline = error is HttpRequestTimeoutException ||
                error is UnresolvedAddressException ||
                error.message?.contains("Failed to connect", ignoreCase = true) == true ||
                error.message?.contains("Connection refused", ignoreCase = true) == true
            if (offline) throw ControlDeskError.Offline()
            throw ControlDeskError.Generic(error.message ?: "Falha no pareamento")
        }
    }
}
