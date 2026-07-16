package app.picnic.player.ui.theme

/**
 * Shared browse motion timings.
 *
 * Backdrop image and ambient colour washes read as one coordinated transition:
 * artwork fades in over [ARTWORK_FADE_IN_MS] while the colour field keeps
 * expanding through [AMBIENT_COLOR_FADE_MS], so the washes appear to grow out of
 * the incoming backdrop. Hero logos are deliberately not animated. Change these
 * together, here only.
 */
object TvBrowseMotion {
    /** Outgoing backdrop fade before the URL swap (keeps D-pad feel snappy). */
    const val BACKDROP_FADE_OUT_MS = 250

    /** Incoming backdrop image fade. */
    const val ARTWORK_FADE_IN_MS = 800

    /** Ambient palette lerp — intentionally longer than the artwork fade so the
     *  colour settles just after the artwork lands. */
    const val AMBIENT_COLOR_FADE_MS = 1250
}
