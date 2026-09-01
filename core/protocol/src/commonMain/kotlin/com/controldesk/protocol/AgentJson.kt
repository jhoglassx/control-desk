package com.controldesk.protocol

import kotlinx.serialization.json.Json

val AgentJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
