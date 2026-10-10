package app.picnic.player.ui.ambient

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.hilt.navigation.compose.hiltViewModel
import app.picnic.player.ui.theme.TvBrowseMotion
import kotlinx.coroutines.flow.first

data class BackdropSpec(
    val backdropUrl: String?,
    val ambientUrl: String?
)

@Stable
data class BackdropFrame(
    val art: ShownArt?,
    val artAlpha: State<Float>,
    val wash: AmbientWash,
    val ambientOn: Boolean,
    val dim: State<Float>
)

val LocalNavDim = staticCompositionLocalOf<State<Float>> {
    error("Nav dim not provided")
}

@Composable
fun rememberBackdrop(spec: BackdropSpec, rowDimmed: () -> Boolean = { false }): BackdropFrame {
    val vm = hiltViewModel<BackdropViewModel>()
    val ambientOn = LocalAmbientBackgrounds.current
    val ambientUrl = spec.ambientUrl.takeIf { ambientOn }
    // Seeding must remain a pure read because composition can be abandoned.
    val seed = remember { vm.seed(spec.backdropUrl, ambientUrl) }
    var live by remember { mutableStateOf(false) }
    val artAlpha = remember { Animatable(if (seed.art != null || spec.backdropUrl == null) 1f else 0f) }

    LaunchedEffect(spec.backdropUrl) {
        vm.requestArt(spec.backdropUrl)
        if (!live) {
            vm.commit()
            live = true
        }
        val target = spec.backdropUrl
        if (vm.shown?.url == target) {
            artAlpha.animateTo(1f, tween(TvBrowseMotion.ARTWORK_FADE_IN_MS, easing = EaseOut))
            return@LaunchedEffect
        }
        if (vm.shown != null) {
            artAlpha.animateTo(0f, tween(TvBrowseMotion.BACKDROP_FADE_OUT_MS, easing = EaseOut))
        }
        snapshotFlow { Triple(vm.ready, vm.failed, vm.target) }
            .first { (ready, failed, requested) -> requested != target || ready != null || failed || requested == null }
        if (vm.target != target) return@LaunchedEffect
        vm.commit()
        artAlpha.snapTo(0f)
        if (vm.shown != null) {
            artAlpha.animateTo(1f, tween(TvBrowseMotion.ARTWORK_FADE_IN_MS, easing = EaseOut))
        }
    }
    LaunchedEffect(ambientUrl) { vm.requestPalette(ambientUrl) }

    val art = if (live) vm.shown else seed.art
    val palette = if (live) vm.palette else seed.palette
    val wash = rememberAmbientWash(palette, initial = seed.palette)
    val navDim = LocalNavDim.current
    val contentDim = animateFloatAsState(
        targetValue = if (rowDimmed()) TvBrowseMotion.DIM_ALPHA else 1f,
        animationSpec = tween(TvBrowseMotion.DIM_FADE_MS),
        label = "contentDim"
    )
    val dim = remember(navDim, contentDim) {
        derivedStateOf { navDim.value * contentDim.value }
    }
    return BackdropFrame(art, artAlpha.asState(), wash, ambientOn, dim)
}
