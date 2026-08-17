package app.picnic.player.data.playback

import android.content.Context
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TrickplayCache(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val prefetchScope: CoroutineScope
) {
    private val _current = MutableStateFlow<Trickplay?>(null)
    val current: StateFlow<Trickplay?> = _current.asStateFlow()

    private var prefetchJob: Job? = null

    fun frameFor(positionMs: Long): TrickplayFrame? = _current.value?.frameFor(positionMs)

    fun replaceWith(sheets: Trickplay?) {
        evict(_current.value)
        prefetch(sheets?.tileUrls().orEmpty())
        _current.value = sheets
    }

    fun clear() {
        evict(_current.value)
        _current.value = null
    }

    suspend fun awaitPrefetch() {
        prefetchJob?.join()
    }

    private fun evict(sheets: Trickplay?) {
        val urls = sheets?.tileUrls().orEmpty()
        if (urls.isEmpty()) return
        val diskCache = context.imageLoader.diskCache ?: return
        appScope.launch {
            urls.forEach { url -> runCatching { diskCache.remove(trickplaySheetCacheKey(url)) } }
        }
    }

    private fun prefetch(urls: List<String>) {
        prefetchJob?.cancel()
        prefetchJob = null
        if (urls.isEmpty()) return
        prefetchJob = prefetchScope.launch {
            val loader = context.imageLoader
            for (url in urls) {
                if (!isActive) return@launch
                loader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .diskCacheKey(trickplaySheetCacheKey(url))
                        .size(Size.ORIGINAL)
                        .build()
                )
            }
        }
    }
}
