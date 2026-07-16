package app.picnic.player.ui.browse

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Resolved card metrics for a row, scaled to the screen. */
@Immutable
internal class BrowseCardStyle(
    val width: Dp,
    val height: Dp,
    val landscape: Boolean
) {
    val slotHeight: Dp get() = height * 1.1f + 2.dp + 12.dp // focus scale + border + glow
    val topInset: Dp get() = height * (0.1f * 0.25f) + 1.dp + 6.dp
}

/** Standard poster sizing: height 156 — grids, search results, detail rails. */
internal fun posterCardStyle(sy: (Float) -> Dp): BrowseCardStyle {
    val hgt = sy(156f)
    return BrowseCardStyle(width = hgt * (2f / 3f), height = hgt, landscape = false)
}

/**
 * Wide 16:9 thumbnail (height 96) — every current home row is a "hero row" using this size.
 * Future row types (non-hero) will introduce their own card sizes rather than reusing this.
 */
internal fun landscapeCardStyle(sy: (Float) -> Dp): BrowseCardStyle {
    val hgt = sy(96f)
    return BrowseCardStyle(width = hgt * (16f / 9f), height = hgt, landscape = true)
}
