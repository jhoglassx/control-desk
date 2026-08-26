package com.controldesk.app

import android.app.Application
import com.controldesk.app.di.appModule
import com.controldesk.app.di.platformModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ControlDeskApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@ControlDeskApplication)
            modules(appModule(), platformModule())
        }
    }
}
