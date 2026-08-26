package com.controldesk.agent

import java.net.NetworkInterface

object NetworkInfo {
    fun connectionHints(port: Int): List<String> {
        val lan = lanAddresses()
        return buildList {
            add("Emulador Android no Mac: 10.0.2.2:$port")
            lan.forEach { add("Celular na Wi-Fi: $it:$port") }
            add("Local: 127.0.0.1:$port")
        }
    }

    fun lanAddresses(): List<String> =
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { nic -> nic.inetAddresses.toList() }
            .mapNotNull { addr ->
                val host = addr.hostAddress ?: return@mapNotNull null
                if (addr.isLoopbackAddress || host.contains(':')) null else host.substringBefore('%')
            }
            .distinct()
}
