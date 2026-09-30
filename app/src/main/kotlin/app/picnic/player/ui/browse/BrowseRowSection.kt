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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import app.picnic.player.data.media.PersonalLibrary
import app.picnic.player.ui.ambient.LocalAmbientPrewarmer
import app.picnic.player.ui.common.LocalContextMenuHandler
import app.picnic.player.ui.common.LocalImageUrls
import app.picnic.player.ui.common.PersonalMenuRequest
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
    onPersonalItem: (PersonalLibrary, BaseItemDto) -> Unit,
    onFocusItem: (Int, BaseItemDto) -> Unit
) {
    val slot = focusedItemId?.let { id -> row.items.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    var lastSlot by remember { mutableIntStateOf(0) }
    SideEffect { if (slot != null) lastSlot = slot }
    val focusIndex = slot ?: lastSlot.coerceAtMost(row.items.lastIndex).coerceAtLeast(0)

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
            modifier = Modifier.padding(start = hInset, bottom = RowTitleBottomGap)
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
                    val personal = row.personalLibrary
                    BrowseMediaCard(
                        item = item,
                        style = style,
                        focusRequester = if (index == focusIndex) rowCardFocus else null,
                        onClick = {
                            if (personal != null) {
                                onPersonalItem(personal, item)
                            } else {
                                val nav = images.navImages(item)
                                ambientPrewarmer.warm(nav.ambUrl)
                                onItem(item, nav.bgUrl, nav.ambUrl)
                            }
                        },
                        onFocused = { focusItem(item) },
                        onLongClick = {
                            if (personal != null) {
                                contextMenu.showPersonal(PersonalMenuRequest(item, personal, from = null))
                            } else {
                                contextMenu.show(item, row.continueWatching)
                            }
                        }
                    )
                }
            }
        }
    }
}

internal val RowTitleBottomGap = 2.dp
