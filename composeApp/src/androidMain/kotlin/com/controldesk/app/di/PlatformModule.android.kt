package com.controldesk.app.di

import com.controldesk.data.network.installControlDeskPlugins
import com.controldesk.data.storage.FileStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

actual fun platformModule(): Module = module {
    single<FileStore> { AndroidFileStore(androidContext()) }
    single {
        HttpClient(OkHttp) {
            installControlDeskPlugins()
            engine {
                config {
                    retryOnConnectionFailure(false)
                    connectTimeout(800, TimeUnit.MILLISECONDS)
                    connectionPool(ConnectionPool(8, 2, TimeUnit.MINUTES))
                    dispatcher(
                        Dispatcher().apply {
                            maxRequests = 32
                            maxRequestsPerHost = 16
                        },
                    )
                }
            }
        }
    }
}
