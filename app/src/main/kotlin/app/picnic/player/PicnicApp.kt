package app.picnic.player

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import app.picnic.player.data.socket.WebSocketManager
import app.picnic.player.data.tvprovider.TvChannelReceiver
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class PicnicApp :
    Application(),
    Configuration.Provider,
    SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var webSocketManager: WebSocketManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
        }
        System.setProperty("kotlin-logging-to-android-native", "true")
        super.onCreate()
        webSocketManager.start()
        TvChannelReceiver.enqueueWorker(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder()
                .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                .build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("image_cache").toOkioPath())
                .maxSizeBytes(DISK_CACHE_MAX_BYTES)
                .build()
        }
        .build()

    private companion object {
        /** Share of the app heap for decoded posters — the hot, above-the-fold artwork. */
        const val MEMORY_CACHE_PERCENT = 0.30

        /** Persistent artwork cache; sized to hold a large library's posters across restarts. */
        const val DISK_CACHE_MAX_BYTES = 256L * 1024 * 1024
    }
}
