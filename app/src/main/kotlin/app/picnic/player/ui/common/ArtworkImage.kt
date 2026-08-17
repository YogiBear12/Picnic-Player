package app.picnic.player.ui.common

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
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

const val ArtworkLogTag = "PicnicImage"

private const val MaxRetries = 2
private const val RetryBackoffMs = 400L

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
    holdLastImage: Boolean = false,
    onSettled: (failed: Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    var attempt by remember(url) { mutableIntStateOf(0) }
    var errors by remember(url) { mutableIntStateOf(0) }
    var lastImage by remember { mutableStateOf<Painter?>(null) }

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

    lastImage?.let { painter ->
        Image(
            painter = painter,
            contentDescription = null,
            contentScale = contentScale,
            alignment = alignment,
            modifier = modifier
        )
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
                    is AsyncImagePainter.State.Success -> {
                        if (holdLastImage) lastImage = state.painter
                        onSettled(false)
                    }
                    else -> Unit
                }
            },
            modifier = modifier
        )
    }
}
