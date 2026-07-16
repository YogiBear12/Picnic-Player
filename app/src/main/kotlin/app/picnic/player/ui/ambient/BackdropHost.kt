package app.picnic.player.ui.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The backdrop a screen wants behind its content: the focused item's artwork plus the
 * small copy the ambient wash extracts its colours from.
 */
data class BackdropSpec(
    val backdropUrl: String?,
    val ambientUrl: String?
)

/**
 * Single source of truth for the app-level backdrop layer (drawn once, above the theme's
 * ocean wash and below the NavHost — see [app.picnic.player.ui.navigation.PicnicNavHost]).
 *
 * Screens publish the spec they need via [PublishBackdrop]. Because the rendering instance
 * persists across navigation, entering a screen that publishes the *same* URLs (Home →
 * Detail for the focused item) redraws nothing — no image re-fade, no palette
 * re-extraction, no scrim animation.
 *
 * The spec deliberately survives screens that don't publish (player, episodes): they paint
 * their own opaque background, and keeping the layer alive underneath means Back returns
 * to the publisher with zero redraw. Flows that tear the session down ([clear]) reset it.
 */
@Singleton
class BackdropController @Inject constructor() {
    private val _spec = mutableStateOf<BackdropSpec?>(null)
    val spec: BackdropSpec? get() = _spec.value

    fun publish(spec: BackdropSpec?) {
        _spec.value = spec
    }

    /** Drop the backdrop entirely — sign-out / session-expired navigation resets. */
    fun clear() {
        _spec.value = null
    }
}

val LocalBackdropController = staticCompositionLocalOf<BackdropController> {
    error("BackdropController not provided")
}

/**
 * Declares the backdrop this screen wants while it is composed. Pass null to explicitly
 * show none (the global ocean wash). Screens that fully paint their own background
 * (player, episodes) simply never call this — the previous spec stays alive so returning
 * to the publisher redraws nothing.
 */
@Composable
fun PublishBackdrop(spec: BackdropSpec?) {
    val controller = LocalBackdropController.current
    LaunchedEffect(spec) { controller.publish(spec) }
}

/** Renders the published backdrop. Composes nothing when no screen wants one. */
@Composable
fun BackdropHostLayer(modifier: Modifier = Modifier) {
    val spec = LocalBackdropController.current.spec ?: return
    app.picnic.player.ui.browse.BrowseBackdrop(
        backdropUrl = spec.backdropUrl,
        ambientUrl = spec.ambientUrl,
        ambientLoader = LocalAmbientPaletteLoader.current,
        modifier = modifier
    )
}
