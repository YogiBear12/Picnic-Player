package app.picnic.player.ui.browse

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester

/**
 * Focus-ownership model for the browse shell.
 *
 * The side drawer's open/close state is owned by tv-material's NavigationDrawer: it opens
 * while any of its items has focus and closes when focus leaves it. Nothing closes it
 * programmatically, so the drawer and the focus system can never disagree.
 *
 * The host keeps a single pending request describing who should own focus next. Panes
 * fulfil it when their content is actually focusable (data loaded, nodes attached) and
 * acknowledge via `onContentFocusSeeded`, which resolves the request to [None].
 */
internal enum class PaneFocusRequest {
    /**
     * The pane should take focus once ready — unless the user has deliberately moved focus
     * into the nav chrome meanwhile. Set on cold start and when returning from navigation
     * (Detail/Settings), where composition — and with it the focused node — was lost.
     */
    WhenIdle,

    /**
     * The user activated a drawer destination. The pane must take focus as soon as it has
     * a focusable target — focus leaving the drawer is what closes it.
     */
    Commit,

    /** The pane owns focus; nothing pending. */
    None
}

/**
 * The latest [DeclarePaneEntry] wins, and withdrawing it falls back to the one below. Effects run
 * in composition order, so a container declares before its content and the innermost sits on top.
 */
internal class PaneEntryFocus {
    private val targets = mutableListOf<() -> FocusRequester>()

    fun requester(): FocusRequester = targets.lastOrNull()?.invoke() ?: FocusRequester.Default

    fun declare(target: () -> FocusRequester) {
        targets += target
    }

    fun withdraw(target: () -> FocusRequester) {
        targets -= target
    }
}

internal val LocalPaneEntryFocus = compositionLocalOf<PaneEntryFocus?> { null }

/** Only active content sees the holder, so a pane or tab fading out cannot declare over it. */
@Composable
internal fun PaneEntryScope(active: Boolean, content: @Composable () -> Unit) {
    val entry = LocalPaneEntryFocus.current
    CompositionLocalProvider(LocalPaneEntryFocus provides entry.takeIf { active }, content = content)
}

@Composable
internal fun DeclarePaneEntry(target: () -> FocusRequester) {
    val entry = LocalPaneEntryFocus.current
    val current by rememberUpdatedState(target)
    DisposableEffect(entry) {
        val declared = { current() }
        entry?.declare(declared)
        onDispose { entry?.withdraw(declared) }
    }
}
