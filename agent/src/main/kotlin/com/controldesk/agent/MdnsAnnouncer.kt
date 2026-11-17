package com.controldesk.agent

import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.net.InetAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.concurrent.TimeUnit

class MdnsAnnouncer(
    private val port: Int,
) : AutoCloseable {
    private var jmdns: JmDNS? = null

    fun start() {
        // On macOS, the computer's local hostname commonly resolves to 127.0.0.1.
        // Binding JmDNS to that address silently prevents phones from discovering it.
        val address = selectAddress()
        val mdns = JmDNS.create(address)
        val info = ServiceInfo.create(
            "_controldesk._tcp.local.",
            "ControlDesk-${machineName()}",
            port,
            "version=${AgentVersion.VALUE}",
        )
        mdns.registerService(info)
        jmdns = mdns
    }

    private fun selectAddress(): InetAddress {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
        val defaultName = defaultRouteInterface()
        val preferred = interfaces.firstOrNull { it.name == defaultName }
        val candidates = (listOfNotNull(preferred) + interfaces.filter { it != preferred })
        return candidates.asSequence()
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && !it.isLinkLocalAddress }
            ?: InetAddress.getLocalHost().takeIf { !it.isLoopbackAddress }
            ?: error("Nenhum endereço IPv4 de rede disponível para anunciar o agente")
    }

    private fun defaultRouteInterface(): String? = runCatching {
        val process = ProcessBuilder("/sbin/route", "-n", "get", "default")
            .redirectErrorStream(true)
            .start()
        if (!process.waitFor(1, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return null
        }
        Regex("(?m)^\\s*interface:\\s*(\\S+)")
            .find(process.inputStream.bufferedReader().use { it.readText() })
            ?.groupValues?.get(1)
    }.getOrNull()

    private fun machineName(): String {
        val computerName = runCatching {
            val process = ProcessBuilder("/usr/sbin/scutil", "--get", "ComputerName")
                .redirectErrorStream(true)
                .start()
            if (!process.waitFor(1, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return@runCatching null
            }
            process.inputStream.bufferedReader().use { it.readText().trim() }
                .takeIf { it.isNotBlank() }
        }.getOrNull()
        return computerName
            ?: runCatching { InetAddress.getLocalHost().hostName.substringBefore('.').takeIf { it.isNotBlank() } }.getOrNull()
            ?: "Mac"
    }

    override fun close() {
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        jmdns = null
    }
}
