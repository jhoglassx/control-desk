package com.controldesk.app.di

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.controldesk.domain.repository.AgentDiscovery
import com.controldesk.domain.repository.DiscoveredAgent
import java.util.concurrent.atomic.AtomicBoolean

class AndroidAgentDiscovery(context: Context) : AgentDiscovery {
    private val appContext = context.applicationContext

    @Suppress("DEPRECATION")
    override fun start(onChanged: (List<DiscoveredAgent>) -> Unit): AutoCloseable {
        val manager = appContext.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val multicastLock = wifi.createMulticastLock("control-desk-mdns").apply {
            setReferenceCounted(false)
            acquire()
        }
        val active = AtomicBoolean(true)
        val found = linkedMapOf<String, DiscoveredAgent>()
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                if (active.compareAndSet(true, false)) {
                    runCatching { manager.stopServiceDiscovery(this) }
                    if (multicastLock.isHeld) multicastLock.release()
                }
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                if (found.remove(serviceInfo.serviceName) != null) onChanged(found.values.toList())
            }
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (!serviceInfo.serviceType.trimEnd('.').equals(SERVICE_TYPE.trimEnd('.'), ignoreCase = true)) return
                @Suppress("DEPRECATION")
                manager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = Unit
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        if (!active.get()) return
                        val address = info.host?.hostAddress ?: return
                        found[info.serviceName] = DiscoveredAgent(
                            name = info.serviceName.removePrefix("ControlDesk-").ifBlank { info.serviceName },
                            host = address,
                            port = info.port,
                        )
                        onChanged(found.values.toList())
                    }
                })
            }
        }
        manager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        return AutoCloseable {
            if (active.getAndSet(false)) runCatching { manager.stopServiceDiscovery(discoveryListener) }
            if (multicastLock.isHeld) runCatching { multicastLock.release() }
        }
    }

    private companion object {
        const val SERVICE_TYPE = "_controldesk._tcp."
    }
}
