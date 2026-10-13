package com.controldesk.app.di

import com.controldesk.data.network.installControlDeskPlugins
import com.controldesk.data.storage.FileStore
import com.controldesk.domain.repository.AgentDiscovery
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformModule(): Module = module {
    single<FileStore> { IosFileStore() }
    single<AgentDiscovery> {
        val discovery = IosAgentDiscovery()
        object : AgentDiscovery {
            override fun start(onChanged: (List<com.controldesk.domain.repository.DiscoveredAgent>) -> Unit): AutoCloseable =
                discovery.start(onChanged)
        }
    }
    single { HttpClient(Darwin) { installControlDeskPlugins() } }
}
