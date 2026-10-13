package com.controldesk.app.di

import com.controldesk.domain.repository.DiscoveredAgent
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSNetServiceBrowserDelegateProtocol
import platform.darwin.NSObject
import platform.Foundation.NSNetService
import platform.Foundation.NSNetServiceBrowser
import platform.Foundation.NSNetServiceDelegateProtocol

@OptIn(ExperimentalForeignApi::class)
class IosAgentDiscovery : NSObject(), NSNetServiceBrowserDelegateProtocol,
    NSNetServiceDelegateProtocol {
    private var browser: NSNetServiceBrowser? = null
    private var onChanged: ((List<DiscoveredAgent>) -> Unit)? = null
    private val services = mutableMapOf<String, NSNetService>()
    private val resolved = linkedMapOf<String, DiscoveredAgent>()

    fun start(onChanged: (List<DiscoveredAgent>) -> Unit): AutoCloseable {
        this.onChanged = onChanged
        services.clear()
        resolved.clear()
        val serviceBrowser = NSNetServiceBrowser()
        browser = serviceBrowser
        serviceBrowser.delegate = this
        serviceBrowser.searchForServicesOfType("_controldesk._tcp.", inDomain = "local.")
        return AutoCloseable {
            serviceBrowser.stop()
            serviceBrowser.delegate = null
            services.values.forEach { it.stop() }
            services.clear()
            resolved.clear()
            this.onChanged = null
            if (browser === serviceBrowser) browser = null
        }
    }

    override fun netServiceBrowser(browser: NSNetServiceBrowser, didFindService: NSNetService, moreComing: Boolean) {
        val key = didFindService.name
        services[key] = didFindService
        didFindService.delegate = this
        didFindService.resolveWithTimeout(5.0)
    }

    override fun netServiceDidResolveAddress(sender: NSNetService) {
        val host = sender.hostName?.trimEnd('.')?.takeIf { it.isNotBlank() } ?: return
        resolved[sender.name] = DiscoveredAgent(
            name = sender.name.removePrefix("ControlDesk-").ifBlank { sender.name },
            host = host,
            port = sender.port.toInt(),
        )
        onChanged?.invoke(resolved.values.toList())
    }

    override fun netService(sender: NSNetService, didNotResolve: Map<Any?, *>) {
        services.remove(sender.name)
    }
}
