@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package app.picnic.player.ui.browse

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.media.HomeRow
import app.picnic.player.ui.ambient.AmbientPaletteLoader
import app.picnic.player.ui.ambient.LocalAmbientPaletteLoader
import app.picnic.player.ui.common.requestFocusWhenAttached
import java.util.UUID
import org.jellyfin.sdk.model.api.BaseItemDto

/**
 * Immersive browse body: hero + row list.
 * Backdrop is full-bleed in [BrowseShellBackground]; focus is hoisted at [BrowseShellHost].
 */
@Composable
internal fun ImmersiveBrowseScaffold(
    rows: List<HomeRow>,
    session: UserSession,
    ambientLoader: AmbientPaletteLoader,
    focusedItem: BaseItemDto?,
    focusedRowIndex: Int,
    focusedItemId: UUID?,
    rowFocusedItemIds: Map<Int, UUID>,
    focusedRowContinueWatching: Boolean,
    seasonCounts: Map<java.util.UUID, Int>,
    heroStreams: Map<java.util.UUID, List<org.jellyfin.sdk.model.api.MediaStream>>,
    focus: ImmersiveBrowseFocus,
    seedContentFocus: Boolean,
    onContentFocusSeeded: () -> Unit,
    onBrowseItemFocused: (rowIndex: Int, item: BaseItemDto) -> Unit,
    onItem: (BaseItemDto, String?, String?) -> Unit,
    horizontalInset: Dp,
    metrics: BrowseLayoutMetrics
) {
    val restoreRow = focusedRowIndex.coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    // Seed focus onto the saved card once rows exist. Returning from a pushed screen
    // recreates the row's LazyListState at scroll 0 (only the card ID survives, in the
    // ViewModel), so a card that was scrolled into view is not composed yet and its
    // pinned requester has no node. Restore the row's scroll to that card first, then
    // request focus attach-aware; if the card is gone entirely, fall back to the row
    // (its focusRestorer lands on the first visible card) so focus never escapes to
    // the nav drawer.
    LaunchedEffect(seedContentFocus, rows.size, focusedRowIndex) {
        if (!seedContentFocus || rows.isEmpty()) return@LaunchedEffect
        val rowIndex = focusedRowIndex.coerceIn(0, rows.lastIndex)
        val savedCardIndex = rowFocusedItemIds[rowIndex]
            ?.let { id -> rows[rowIndex].items.indexOfFirst { it.id == id } }
            ?.takeIf { it >= 0 }
        if (savedCardIndex != null && savedCardIndex > 0) {
            runCatching { focus.rowListStates.getOrNull(rowIndex)?.scrollToItem(savedCardIndex) }
        }
        val landed = focus.rowCardFocus.getOrNull(rowIndex)
            ?.requestFocusWhenAttached(maxFrames = 30) == true
        if (!landed) {
            runCatching { focus.rowFocusRequesters.getOrNull(rowIndex)?.requestFocus() }
        }
        onContentFocusSeeded()
    }

    val spaceAbovePx = with(androidx.compose.ui.platform.LocalDensity.current) {
        metrics.rowTitleHeight.toPx()
    }
    val rowColumnPivot = androidx.compose.runtime.remember(spaceAbovePx) {
        ScrollToTopBringIntoView(spaceAbovePx)
    }

    CompositionLocalProvider(LocalAmbientPaletteLoader provides ambientLoader) {
        Column(
            Modifier
                .fillMaxSize()
                .focusProperties {
                    // Entering the home content lands on the row that was last focused.
                    onEnter = {
                        runCatching {
                            focus.rowCardFocus.getOrElse(restoreRow) {
                                focus.rowFocusRequesters.getOrElse(restoreRow) {
                                    focus.rowFocusRequesters[0]
                                }
                            }.requestFocus()
                        }
                    }
                }
        ) {
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                BrowseHero(
                    item = focusedItem,
                    session = session,
                    logoHeight = metrics.logoHeight,
                    continueWatching = focusedRowContinueWatching,
                    seasonCount = focusedItem?.id?.let { seasonCounts[it] },
                    // streamsOverride is populated via the look-ahead mechanism
                    // for both Movies/Episodes and Series, allowing technical
                    // badges to appear without bloating the home-row cache.
                    streamsOverride = focusedItem?.id?.let { heroStreams[it] },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = horizontalInset,
                            end = horizontalInset,
                            bottom = metrics.heroGap
                        )
                        .width(metrics.heroContentWidth)
                )
            }
            CompositionLocalProvider(LocalBringIntoViewSpec provides rowColumnPivot) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(metrics.rowsRegionHeight.coerceAtLeast(0.dp))
                        .offset(y = metrics.rowsViewportOffset)
                        .focusProperties {
                            enter = {
                                focus.rowCardFocus.getOrElse(restoreRow) {
                                    focus.rowFocusRequesters.getOrElse(restoreRow) {
                                        focus.rowFocusRequesters[0]
                                    }
                                }
                            }
                        },
                    state = focus.listState,
                    contentPadding = PaddingValues(bottom = metrics.bottomInset),
                    verticalArrangement = Arrangement.spacedBy(metrics.rowSpacing)
                ) {
                    itemsIndexed(
                        items = rows,
                        key = { _, row -> homeRowKey(row) }
                    ) { rowIndex, row ->
                        BrowseRowSection(
                            row = row,
                            session = session,
                            hInset = horizontalInset,
                            // Continue Watching keeps wide (in-progress) thumbnails; the
                            // "Recently added" library rows use portrait poster cards.
                            style = if (row.continueWatching) {
                                landscapeCardStyle(metrics.sy)
                            } else {
                                posterCardStyle(metrics.sy)
                            },
                            spacing = metrics.cardSpacing,
                            rowListState = focus.rowListStates[rowIndex],
                            rowFocus = focus.rowFocusRequesters[rowIndex],
                            rowCardFocus = focus.rowCardFocus[rowIndex],
                            focusedItemId = rowFocusedItemIds[rowIndex],
                            rowBringIntoView = focus.defaultRowBringIntoView,
                            rowIndex = rowIndex,
                            onItem = onItem,
                            onFocusItem = { _, item -> onBrowseItemFocused(rowIndex, item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun rememberHomeBrowseFocus(
    rowCount: Int,
    focusedRowIndex: Int,
    scrollEnabled: Boolean = true
): ImmersiveBrowseFocus = rememberImmersiveBrowseFocus(rowCount, focusedRowIndex, scrollEnabled)
