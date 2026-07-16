package app.picnic.player.ui.browse

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen-proportional layout tokens from the 960×540 design canvas. */
internal data class BrowseLayoutMetrics(
    val hInset: Dp,
    val topInset: Dp,
    val bottomInset: Dp,
    val heroContentWidth: Dp,
    val heroGap: Dp,
    val rowTitleHeight: Dp,
    val rowsRegionHeight: Dp,
    val rowSpacing: Dp,
    val cardSpacing: Dp,
    val logoHeight: Dp,
    val rowsViewportOffset: Dp,
    val sx: (Float) -> Dp,
    val sy: (Float) -> Dp
)

/**
 * Left inset for content rendered beside the persistent nav rail (the rail supplies the
 * visual gutter; this is just breathing room). Shared by the shell panes and the pushed
 * item screens (Detail) so everything keeps one horizontal origin.
 */
internal val BrowsePaneStartInset = 16.dp

/**
 * Resting left inset for detail-style pages (Detail, Person, Seerr Detail). These pages have
 * no nav drawer, so they add the collapsed-drawer gutter to [BrowsePaneStartInset] to keep one
 * horizontal origin with Home. Hero/buttons/text carry it as a plain modifier inset; the rows
 * carry the same value as LazyRow `contentPadding` so their cards scroll under it to the true
 * screen edge instead of being clipped by a column-level start padding.
 */
internal val DetailContentStartInset = DrawerCollapsedWidth + BrowsePaneStartInset

internal fun browseLayoutMetrics(screenWidth: Dp, screenHeight: Dp): BrowseLayoutMetrics {
    val sx: (Float) -> Dp = { d -> screenWidth * (d / 960f) }
    val sy: (Float) -> Dp = { d -> screenHeight * (d / 540f) }
    val rowTitleHeight = sy(28f) + sy(2f)
    // The rows region must fit the tallest row a focus can land on — the portrait poster rows
    // ("Recently added") are taller than the landscape Continue Watching row.
    val rowStyle = posterCardStyle(sy)
    val rowSectionHeight = rowTitleHeight + rowStyle.slotHeight
    return BrowseLayoutMetrics(
        hInset = sx(48f),
        topInset = sy(14f),
        bottomInset = sy(27f),
        heroContentWidth = screenWidth * 0.42f,
        // Tightened so the hero block sits LOWER (rows keep slack below their cards):
        // the info block's extra lines grow downward into this space instead of pushing
        // the logo up.
        heroGap = sy(14f),
        rowTitleHeight = rowTitleHeight,
        rowsRegionHeight = sy(27f) + rowSectionHeight,
        rowSpacing = sy(16f),
        cardSpacing = sx(16f),
        // Trimmed from 96 so the (now taller) poster rows fit without shoving the logo up
        // against the top edge.
        logoHeight = sy(84f),
        rowsViewportOffset = sy(12f),
        sx = sx,
        sy = sy
    )
}
