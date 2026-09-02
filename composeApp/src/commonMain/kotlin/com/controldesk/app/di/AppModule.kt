package com.controldesk.app.di

import com.controldesk.app.viewmodel.DeskViewModel
import com.controldesk.app.viewmodel.PairHostViewModel
import com.controldesk.data.AgentGatewayImpl
import com.controldesk.data.AgentRealtimeClient
import com.controldesk.domain.repository.AgentRealtime
import com.controldesk.data.JsonHostPreferences
import com.controldesk.data.JsonRunningAppIconCache
import com.controldesk.data.PairHostRemoteImpl
import com.controldesk.domain.repository.AgentGateway
import com.controldesk.domain.repository.HostPreferences
import com.controldesk.domain.repository.PairHostRemote
import com.controldesk.domain.repository.RunningAppIconCache
import com.controldesk.domain.usecase.ExecuteGridButtonUseCase
import com.controldesk.domain.usecase.PairHostUseCase
import com.controldesk.protocol.AgentApiClient
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

fun appModule(): Module = module {
    single { AgentApiClient(get()) }
    single<AgentRealtime> { AgentRealtimeClient(get()) }
    single<HostPreferences> { JsonHostPreferences(get()) }
    single<AgentGateway> { AgentGatewayImpl(get()) }
    single<PairHostRemote> { PairHostRemoteImpl(get()) }
    single<RunningAppIconCache> { JsonRunningAppIconCache(get()) }
    single { ExecuteGridButtonUseCase(get()) }
    single { PairHostUseCase(get(), get()) }
    viewModel { DeskViewModel(get(), get(), get(), get(), get()) }
    viewModel { PairHostViewModel(get(), get()) }
}

expect fun platformModule(): Module
