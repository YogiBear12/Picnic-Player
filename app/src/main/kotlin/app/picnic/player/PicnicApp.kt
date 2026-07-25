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

    // Session websocket owner (#119). Injected here so it starts observing the active
    // session at process start; constructing it on the main thread lets its foreground
    // observer register with ProcessLifecycleOwner as required.
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
        // The Jellyfin SDK logs via kotlin-logging, whose Android build defaults to
        // an slf4j backend (org.slf4j.LoggerFactory) unless this system property is
        // set. We ship no slf4j, so without this the SDK's first logger init throws
        // NoClassDefFoundError and the whole discovery/auth path dies (surfaced as
        // "...AddressCandidateHelperKt"). This routes its logging to android.util.Log
        // instead — no slf4j dependency needed. Must be set before any SDK call.
        System.setProperty("kotlin-logging-to-android-native", "true")
        super.onCreate()
        webSocketManager.start()
        TvChannelReceiver.enqueueWorker(this)
    }

    /**
     * Tuned singleton Coil loader for a poster-dense 10-foot UI. A larger in-memory cache keeps
     * the visible rows resident across focus/scroll (posters are the hot path, and re-decoding
     * on every D-pad move is what makes them "pop in"), while a sized disk cache survives process
     * death so a warm start paints artwork from disk instead of the network. Backs every
     * AsyncImage and AmbientPaletteLoader's palette decodes. URL shape is untouched — the
     * tag-guarded, fillWidth-sized URLs from JellyfinImages remain the cache keys.
     */
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
