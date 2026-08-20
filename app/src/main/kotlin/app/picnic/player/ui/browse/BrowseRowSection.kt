@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class, ExperimentalTvMaterial3Api::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.picnic.player.data.media.HomeRow
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

@Composable
internal fun BrowseRowSection(
    row: HomeRow,
    rowIndex: Int,
    hInset: Dp,
    style: BrowseCardStyle,
    spacing: Dp,
    rowListState: LazyListState,
    rowFocus: FocusRequester,
    rowCardFocus: FocusRequester,
    focusedItemId: UUID?,
    rowBringIntoView: BringIntoViewSpec,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    onFocusItem: (Int, BaseItemDto) -> Unit
) {
    val focusIndex = focusedItemId
        ?.let { id -> row.items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
        ?: 0

    val focusItem = remember(rowIndex, onFocusItem) {
        { item: BaseItemDto -> onFocusItem(rowIndex, item) }
    }

    val ambientPrewarmer = LocalAmbientPrewarmer.current
    val contextMenu = LocalContextMenuHandler.current
    val images = LocalImageUrls.current

    Column {
        Text(
            text = row.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = hInset, bottom = 2.dp)
        )
        CompositionLocalProvider(LocalBringIntoViewSpec provides rowBringIntoView) {
            LazyRow(
                state = rowListState,
                contentPadding = PaddingValues(horizontal = hInset),
                horizontalArrangement = Arrangement.spacedBy(spacing),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(rowFocus)
                    .focusRestorer(rowCardFocus)
                    .focusGroup()
            ) {
                itemsIndexed(
                    items = row.items,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "BrowseMediaCard" }
                ) { index, item ->
                    BrowseMediaCard(
                        item = item,
                        style = style,
                        focusRequester = if (index == focusIndex) rowCardFocus else null,
                        onClick = {
                            val nav = images.navImages(item)
                            ambientPrewarmer.warm(nav.ambUrl)
                            onItem(item, nav.bgUrl, nav.ambUrl)
                        },
                        onFocused = { focusItem(item) },
                        onLongClick = { contextMenu.show(item) }
                    )
                }
            }
        }
    }
}
