package com.reader.android

import android.app.Application
import com.reader.android.di.androidModule
import com.reader.shared.di.platformModule
import com.reader.shared.di.sharedModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ReaderApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        Napier.base(DebugAntilog())
        
        startKoin {
            androidLogger()
            androidContext(this@ReaderApplication)
            modules(
                sharedModule,
                platformModule(),
                androidModule
            )
        }
    }
}
