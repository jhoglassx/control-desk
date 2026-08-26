package com.controldesk.agent

import java.io.File

object AgentPaths {
    val home: File
        get() = File(System.getProperty("user.home"), ".control-desk").apply { mkdirs() }

    val tokens: File
        get() = File(home, "agent_tokens.txt")

    val pin: File
        get() = File(home, "agent_pin.txt")

    val log: File
        get() = File(home, "agent.log")

    val icons: File
        get() = File(home, "icons").apply { mkdirs() }
}
