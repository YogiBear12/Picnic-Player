package app.picnic.player.ui.common

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Logcat tag for artwork resolution/load diagnostics: `adb logcat -s PicnicImage`. */
const val ArtworkLogTag = "PicnicImage"

/** Transient artwork failures retry this many times before settling on the caller's placeholder. */
private const val MaxRetries = 2
private const val RetryBackoffMs = 400L

/**
 * Artwork loader that cannot get stuck on a failure.
 *
 * A load error means the bytes cached for this URL are unusable — most often a disk entry that was
 * read while another request was still writing it, which decodes to
 * `ImageDecoder$DecodeException ... 'unimplemented'`. Coil keeps that entry, so without eviction
 * every later load of the same artwork fails identically and the card shows its placeholder for the
 * life of the cache. Each error therefore drops the disk entry before retrying, which also means a
 * card that exhausts its retries starts clean the next time it is composed — scrolling it back into
 * view or leaving and re-entering the screen refetches from the network.
 *
 * Draws nothing until the image loads; callers stack this over their own placeholder underlay.
 *
 * [stableCacheKey] pins the memory-cache entry to something other than the URL, so a URL that
 * changes for the same subject (an artwork tag refresh) keeps painting the old image until the new
 * one arrives. [onSettled] reports whether the load has finished in failure with no retries left,
 * for callers that swap in their own fallback rather than an underlay.
 */
@Composable
fun ArtworkImage(
    url: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    label: String? = null,
    stableCacheKey: String? = null,
    crossfade: Boolean = false,
    onSettled: (failed: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var attempt by remember(url) { mutableIntStateOf(0) }
    var errors by remember(url) { mutableIntStateOf(0) }

    LaunchedEffect(url, errors) {
        if (errors == 0) return@LaunchedEffect
        withContext(Dispatchers.IO) { imageLoader.diskCache?.remove(url) }
        if (attempt < MaxRetries) {
            delay(RetryBackoffMs * (attempt + 1))
            attempt++
        }
    }

    val request = remember(url, stableCacheKey, crossfade) {
        ImageRequest.Builder(context)
            // Hardware bitmap (default) — GPU-backed, cheap to draw while scrolling, and small
            // enough at card width that the allocation limit isn't hit.
            .data(url)
            .apply {
                if (stableCacheKey != null) {
                    memoryCacheKey(stableCacheKey)
                    placeholderMemoryCacheKey(stableCacheKey)
                }
                if (crossfade) crossfade(true)
            }
            .build()
    }

    key(attempt) {
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = contentScale,
            alignment = alignment,
            onState = { state ->
                when (state) {
                    is AsyncImagePainter.State.Error -> {
                        Log.w(
                            ArtworkLogTag,
                            "load failed (attempt ${attempt + 1}/${MaxRetries + 1}): " +
                                "${label.orEmpty()} url=$url cause=${state.result.throwable}"
                        )
                        errors++
                        if (attempt >= MaxRetries) onSettled(true)
                    }
                    is AsyncImagePainter.State.Success -> onSettled(false)
                    else -> Unit
                }
            },
            modifier = modifier
        )
    }
}
