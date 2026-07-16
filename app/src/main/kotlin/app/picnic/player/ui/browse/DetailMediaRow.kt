@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

/**
 * One titled horizontal card row on a detail-style page (Detail, Seerr Detail, Person):
 * heading + LazyRow sharing the shell's horizontal origin. The row is full-bleed to the
 * screen edges and carries its resting inset ([DetailContentStartInset]) as LazyRow
 * `contentPadding`, so cards scroll under it and off the true left edge rather than being
 * clipped by a page-level start padding. Restores the platform
 * [horizontalRowSpec] inside the row so a page-level vertical pin (ScrollToTopBringIntoView)
 * never leaks into the row's own horizontal scrolling.
 *
 * [rowFocus] carries the row's focus wiring — `RowFocusState.rowModifier()` for
 * requester-per-card rows, or `Modifier.focusRestorer(pin).focusGroup()` for
 * single-pin rows. Card chrome and per-card focus wiring stay with the caller.
 */
@Composable
internal fun <T> DetailMediaRow(
    title: String,
    items: List<T>,
    endInset: Dp,
    cardSpacing: Dp,
    horizontalRowSpec: BringIntoViewSpec,
    rowFocus: Modifier,
    modifier: Modifier = Modifier,
    titleFontWeight: FontWeight? = null,
    key: ((index: Int, item: T) -> Any)? = null,
    card: @Composable LazyItemScope.(index: Int, item: T) -> Unit
) {
    Column(modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = titleFontWeight,
            color = Color.White,
            modifier = Modifier.padding(
                start = DetailContentStartInset,
                end = endInset,
                bottom = 2.dp
            )
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalRowSpec) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                contentPadding = PaddingValues(
                    start = DetailContentStartInset,
                    end = endInset
                ),
                modifier = rowFocus
            ) {
                itemsIndexed(items, key = key) { index, item -> card(index, item) }
            }
        }
    }
}
