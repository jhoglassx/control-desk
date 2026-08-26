package com.controldesk.agent

import javax.jmdns.JmDNS
import javax.jmdns.ServiceInfo
import java.net.InetAddress

class MdnsAnnouncer(
    private val port: Int,
) : AutoCloseable {
    private var jmdns: JmDNS? = null

    fun start() {
        val mdns = JmDNS.create(InetAddress.getLocalHost())
        val info = ServiceInfo.create(
            "_controldesk._tcp.local.",
            "ControlDesk-${InetAddress.getLocalHost().hostName}",
            port,
            "version=1.0.0",
        )
        mdns.registerService(info)
        jmdns = mdns
    }

    override fun close() {
        runCatching { jmdns?.unregisterAllServices() }
        runCatching { jmdns?.close() }
        jmdns = null
    }
}
