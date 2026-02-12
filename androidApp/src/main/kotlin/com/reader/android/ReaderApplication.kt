package com.reader.android

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import com.reader.android.di.androidModule
import com.reader.shared.di.platformModule
import com.reader.shared.di.sharedModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class ReaderApplication : Application(), SingletonImageLoader.Factory {
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

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                if (Build.VERSION.SDK_INT >= 28) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .build()
    }
}
