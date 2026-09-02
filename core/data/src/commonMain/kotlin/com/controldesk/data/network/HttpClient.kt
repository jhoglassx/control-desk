package com.controldesk.data.network

import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

fun controlDeskJson(): Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

fun HttpClientConfig<*>.installControlDeskPlugins() {
    expectSuccess = true
    install(ContentNegotiation) {
        json(controlDeskJson())
    }
    install(WebSockets)
    install(HttpTimeout) {
        requestTimeoutMillis = 8_000
        connectTimeoutMillis = 800
        socketTimeoutMillis = 8_000
    }
}
