package app.picnic.player.ui.common

import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.Modifier

/**
 * Scrolls overflowing text while the row is focused. The modifier is applied unconditionally and
 * gated on [focused] through `iterations`, because attaching `basicMarquee` only while focused
 * re-measures the text and shifts the row by a pixel as focus arrives.
 */
fun Modifier.marqueeWhenFocused(focused: Boolean): Modifier = basicMarquee(iterations = if (focused) MarqueeIterations else 0)

private const val MarqueeIterations = 3
