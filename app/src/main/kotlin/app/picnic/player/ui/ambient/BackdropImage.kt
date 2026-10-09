package app.picnic.player.ui.ambient

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import app.picnic.player.ui.theme.TvBrowseMotion
import coil3.compose.AsyncImagePainter
import kotlinx.coroutines.flow.first

/**
 * The one URL and fade owner for the root backdrop, shared by every presentation so a
 * presentation change keeps the loaded image and its alpha. See docs/guides/browse-backdrop.md.
 */
@Stable
internal class BackdropImage {
    var url by mutableStateOf<String?>(null)
        private set
    private var loaded by mutableStateOf(false)
    val alpha = Animatable(0f)

    fun onState(state: AsyncImagePainter.State) {
        if (state is AsyncImagePainter.State.Success && state.result.request.data == url) {
            loaded = true
        }
    }

    suspend fun show(target: String?) {
        if (target == url) return
        if (url != null) {
            alpha.animateTo(0f, tween(TvBrowseMotion.BACKDROP_FADE_OUT_MS, easing = EaseOut))
        }
        loaded = false
        alpha.snapTo(0f)
        url = target
        if (target == null) return
        snapshotFlow { loaded }.first { it }
        alpha.animateTo(1f, tween(TvBrowseMotion.ARTWORK_FADE_IN_MS, easing = EaseOut))
    }
}

@Composable
internal fun rememberBackdropImage(backdropUrl: String?): BackdropImage {
    val image = remember { BackdropImage() }
    LaunchedEffect(backdropUrl) { image.show(backdropUrl) }
    return image
}
