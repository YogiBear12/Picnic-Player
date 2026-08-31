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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text

@Composable
internal fun <T> DetailMediaRow(
    title: String,
    items: List<T>,
    endInset: Dp,
    cardSpacing: Dp,
    horizontalRowSpec: BringIntoViewSpec,
    rowFocus: Modifier,
    modifier: Modifier = Modifier,
    startInset: Dp = DetailContentStartInset,
    headingInset: Dp = startInset,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    titleFontWeight: FontWeight? = null,
    key: ((index: Int, item: T) -> Any)? = null,
    card: @Composable LazyItemScope.(index: Int, item: T) -> Unit
) {
    Column(modifier) {
        Text(
            text = title,
            style = titleStyle,
            fontWeight = titleFontWeight,
            color = Color.White,
            modifier = Modifier.padding(
                start = headingInset,
                end = endInset,
                bottom = 2.dp
            )
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides horizontalRowSpec) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(cardSpacing),
                contentPadding = PaddingValues(
                    start = startInset,
                    end = endInset
                ),
                modifier = rowFocus
            ) {
                itemsIndexed(items, key = key) { index, item -> card(index, item) }
            }
        }
    }
}
