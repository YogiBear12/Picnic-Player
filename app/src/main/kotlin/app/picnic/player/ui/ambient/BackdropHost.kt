package app.picnic.player.ui.ambient

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import app.picnic.player.ui.theme.TvBrowseMotion
import javax.inject.Inject
import javax.inject.Singleton

data class BackdropSpec(
    val backdropUrl: String?,
    val ambientUrl: String?
)

@Singleton
class BackdropController @Inject constructor() {
    private val _spec = mutableStateOf<BackdropSpec?>(null)
    val spec: BackdropSpec? get() = _spec.value

    private val _dimmed = mutableStateOf(false)
    val dimmed: Boolean get() = _dimmed.value
    private var dimOwner: Any? = null

    fun publish(spec: BackdropSpec?) {
        _spec.value = spec
    }

    fun dim(owner: Any, dimmed: Boolean) {
        dimOwner = owner
        _dimmed.value = dimmed
    }

    fun releaseDim(owner: Any) {
        if (dimOwner !== owner) return
        dimOwner = null
        _dimmed.value = false
    }

    fun clear() {
        _spec.value = null
        dimOwner = null
        _dimmed.value = false
    }
}

val LocalBackdropController = staticCompositionLocalOf<BackdropController> {
    error("BackdropController not provided")
}

@Composable
fun PublishBackdrop(spec: BackdropSpec?) {
    val controller = LocalBackdropController.current
    LaunchedEffect(spec) { controller.publish(spec) }
}

@Composable
fun DimBackdrop(dimmed: () -> Boolean) {
    val controller = LocalBackdropController.current
    val current by rememberUpdatedState(dimmed)
    val owner = remember { Any() }
    LaunchedEffect(controller) {
        snapshotFlow { current() }.collect { controller.dim(owner, it) }
    }
    DisposableEffect(controller) {
        onDispose { controller.releaseDim(owner) }
    }
}

@Composable
fun BackdropHostLayer(drawerAlpha: State<Float>, modifier: Modifier = Modifier) {
    val controller = LocalBackdropController.current
    val spec = controller.spec
    val contentAlpha = animateFloatAsState(
        targetValue = if (controller.dimmed) TvBrowseMotion.DIM_ALPHA else 1f,
        animationSpec = tween(TvBrowseMotion.DIM_FADE_MS),
        label = "contentAlpha"
    )
    val alpha = remember(drawerAlpha, contentAlpha) {
        derivedStateOf { drawerAlpha.value * contentAlpha.value }
    }
    if (spec == null) return
    app.picnic.player.ui.browse.BrowseBackdrop(
        backdropUrl = spec.backdropUrl,
        ambientUrl = spec.ambientUrl,
        ambientLoader = LocalAmbientPaletteLoader.current,
        alphaScale = alpha,
        modifier = modifier
    )
}
