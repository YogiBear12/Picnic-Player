package app.picnic.player.ui.browse

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
