package app.picnic.player.ui.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import app.picnic.player.ui.common.RowFocusState
import app.picnic.player.ui.common.rememberRowFocusState
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson

internal const val SECTION_BUTTONS = 0
internal const val SECTION_CAST = 1
internal const val SECTION_COLLECTIONS = 2
internal const val SECTION_SIMILAR = 3

private const val ACTION_BUTTON_COUNT = 7
private const val FOCUS_RESTORE_FAILSAFE_MS = 2000L

@Stable
class DetailPageFocus internal constructor(
    val listState: LazyListState,
    val buttons: List<FocusRequester>,
    val cast: RowFocusState,
    val collections: RowFocusState,
    val similar: RowFocusState,
    lastButtonIndexState: MutableState<Int>,
    lastSectionState: MutableState<Int>
) {
    var lastButtonIndex: Int by lastButtonIndexState
        private set

    var lastSection: Int by lastSectionState
        private set

    val playFocus: FocusRequester get() = buttons[0]

    val lastFocusedButton: FocusRequester get() = buttons[lastButtonIndex]

    fun onButtonFocused(index: Int) {
        lastButtonIndex = index
        lastSection = SECTION_BUTTONS
    }

    fun onCastFocused(index: Int) {
        cast.onItemFocused(index)
        lastSection = SECTION_CAST
    }

    fun onCollectionFocused(index: Int, key: String) {
        collections.onItemFocused(index, key)
        lastSection = SECTION_COLLECTIONS
    }

    fun onSimilarFocused(index: Int, key: String) {
        similar.onItemFocused(index, key)
        lastSection = SECTION_SIMILAR
    }

    internal fun fallBackToButtons() {
        lastSection = SECTION_BUTTONS
    }
}

@Composable
internal fun rememberDetailPageFocus(
    people: List<BaseItemPerson>,
    collections: List<BaseItemDto>,
    similarItems: List<BaseItemDto>
): DetailPageFocus {
    val listState = rememberLazyListState()
    val buttons = remember { List(ACTION_BUTTON_COUNT) { FocusRequester() } }
    val castFocus = rememberRowFocusState(people)
    val collectionFocus = rememberRowFocusState(collections)
    val similarFocus = rememberRowFocusState(similarItems)
    val lastButtonIndex = rememberSaveable { mutableStateOf(0) }
    val lastSection = rememberSaveable { mutableStateOf(SECTION_BUTTONS) }

    val focus = remember(castFocus, collectionFocus, similarFocus) {
        DetailPageFocus(listState, buttons, castFocus, collectionFocus, similarFocus, lastButtonIndex, lastSection)
    }

    RestoreDetailFocus(focus, people, collections, similarItems)
    return focus
}

private fun sectionLazyIndex(
    section: Int,
    people: List<BaseItemPerson>,
    collections: List<BaseItemDto>,
    similarItems: List<BaseItemDto>
): Int {
    var index = 0
    if (section == SECTION_CAST) return if (people.isEmpty()) -1 else 1
    index = 1 + (if (people.isEmpty()) 0 else 1)
    if (section == SECTION_COLLECTIONS) return if (collections.isEmpty()) -1 else index
    index += if (collections.isEmpty()) 0 else 1
    if (section == SECTION_SIMILAR) return if (similarItems.isEmpty()) -1 else index
    return 0
}

@Composable
private fun RestoreDetailFocus(
    focus: DetailPageFocus,
    people: List<BaseItemPerson>,
    collections: List<BaseItemDto>,
    similarItems: List<BaseItemDto>
) {
    var focusRestored by remember { mutableStateOf(false) }
    LaunchedEffect(people, collections, similarItems) {
        if (focusRestored) return@LaunchedEffect
        if (focus.lastSection == SECTION_BUTTONS) {
            focusRestored = true
            focus.playFocus.requestFocus()
            return@LaunchedEffect
        }
        val ready = when (focus.lastSection) {
            SECTION_CAST -> people.isNotEmpty()
            SECTION_COLLECTIONS -> collections.isNotEmpty()
            else -> similarItems.isNotEmpty()
        }
        if (!ready) return@LaunchedEffect
        when (focus.lastSection) {
            SECTION_COLLECTIONS -> focus.collections.resolveAgainst(collections) { it.id.toString() }
            SECTION_SIMILAR -> focus.similar.resolveAgainst(similarItems) { it.id.toString() }
            else -> focus.cast.resolveAgainst(people)
        }
        focusRestored = true
        val lazyIndex = sectionLazyIndex(focus.lastSection, people, collections, similarItems)
        if (lazyIndex > 0) {
            runCatching { focus.listState.scrollToItem(lazyIndex) }
            val restored = when (focus.lastSection) {
                SECTION_CAST -> focus.cast.restoreFocus()
                SECTION_COLLECTIONS -> focus.collections.restoreFocus()
                else -> focus.similar.restoreFocus()
            }
            if (restored) {
                return@LaunchedEffect
            }
        }
        focus.fallBackToButtons()
        runCatching { focus.listState.scrollToItem(0) }
        focus.playFocus.requestFocus()
    }
    LaunchedEffect(Unit) {
        delay(FOCUS_RESTORE_FAILSAFE_MS)
        if (!focusRestored) {
            focusRestored = true
            focus.fallBackToButtons()
            runCatching { focus.listState.scrollToItem(0) }
            runCatching { focus.playFocus.requestFocus() }
        }
    }
}
