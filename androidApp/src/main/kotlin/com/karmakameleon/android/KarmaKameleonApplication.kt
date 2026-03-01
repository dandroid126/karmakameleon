package com.karmakameleon.android

import android.app.Application
import android.os.Build
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.crossfade
import com.karmakameleon.android.di.androidModule
import com.karmakameleon.android.notifications.NotificationHelper
import com.karmakameleon.shared.data.repository.InboxPoller
import com.karmakameleon.shared.di.platformModule
import com.karmakameleon.shared.di.sharedModule
import io.github.aakira.napier.DebugAntilog
import io.github.aakira.napier.Napier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

class KarmaKameleonApplication : Application(), SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var pollerCollectionJob: Job? = null

    private val foregroundObserver = object : DefaultLifecycleObserver {
        @androidx.annotation.RequiresPermission(android.Manifest.permission.POST_NOTIFICATIONS)
        override fun onStart(owner: LifecycleOwner) {
            val poller = GlobalContext.get().get<InboxPoller>()
            poller.start()
            pollerCollectionJob = applicationScope.launch {
                poller.newMessages.collect { newMessages ->
                    NotificationHelper.showNotifications(this@KarmaKameleonApplication, newMessages)
                }
            }
        }

        override fun onStop(owner: LifecycleOwner) {
            GlobalContext.get().get<InboxPoller>().stop()
            pollerCollectionJob?.cancel()
            pollerCollectionJob = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        
        Napier.base(DebugAntilog())
        
        startKoin {
            androidLogger()
            androidContext(this@KarmaKameleonApplication)
            modules(
                sharedModule,
                platformModule(),
                androidModule
            )
        }

        ProcessLifecycleOwner.get().lifecycle.addObserver(foregroundObserver)
    }

    override fun newImageLoader(context: coil3.PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, percent = 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
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
