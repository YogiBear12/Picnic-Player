package app.picnic.player.ui.browse

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
internal data class BrowseCardStyle(
    val width: Dp,
    val height: Dp,
    val landscape: Boolean
) {
    val slotHeight: Dp get() = height * 1.1f + 2.dp + 12.dp // focus scale + border + glow
    val topInset: Dp get() = height * (0.1f * 0.25f) + 1.dp + 6.dp
}

internal fun posterCardStyle(sy: (Float) -> Dp): BrowseCardStyle {
    val hgt = sy(156f)
    return BrowseCardStyle(width = hgt * (2f / 3f), height = hgt, landscape = false)
}

internal fun landscapeCardStyle(sy: (Float) -> Dp): BrowseCardStyle {
    val hgt = sy(96f)
    return BrowseCardStyle(width = hgt * (16f / 9f), height = hgt, landscape = true)
}
