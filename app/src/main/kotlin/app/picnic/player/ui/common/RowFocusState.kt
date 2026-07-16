@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)

package app.picnic.player.ui.common

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties

/**
 * Focus bookkeeping for one horizontal media row on a detail-style page: a
 * pre-created requester per card, the last-focused index (saved across
 * navigation), and an optional stable key so back-navigation can restore the
 * exact card even when the row's server order changed between visits.
 *
 * Pairs with `DetailMediaRow`: pass [rowModifier] to the row so D-pad entry
 * lands on the last-focused card, call [onItemFocused] from each card, and on
 * restore call [resolveAgainst] then [restoreFocus].
 */
@Stable
class RowFocusState internal constructor(
    val requesters: Map<Int, FocusRequester>,
    indexState: MutableState<Int>,
    keyState: MutableState<String?>
) {
    var focusedIndex: Int by indexState
        private set
    var focusedKey: String? by keyState
        private set

    fun requesterAt(index: Int): FocusRequester = requesters[index] ?: FocusRequester.Default

    /** Record card [index] (and its stable [key]) as focused; call from each card's onFocused. */
    fun onItemFocused(index: Int, key: String? = null) {
        focusedIndex = index
        focusedKey = key
    }

    /** Row-level focus wiring: a focus group whose D-pad entry pins to the last-focused card. */
    fun rowModifier(): Modifier = Modifier
        .focusGroup()
        .focusProperties { enter = { requesterAt(focusedIndex) } }

    /**
     * Re-align the saved focus target with [items] before restoring: resolve the saved
     * stable key first (the row's order can change between visits), else clamp the index.
     */
    fun <T> resolveAgainst(items: List<T>, keyOf: ((T) -> String)? = null) {
        if (items.isEmpty()) return
        val byKey = focusedKey?.let { saved ->
            keyOf
                ?.let { of -> items.indexOfFirst { of(it) == saved } }
                ?.takeIf { it >= 0 }
        }
        focusedIndex = byKey ?: focusedIndex.coerceIn(0, items.lastIndex)
    }

    /** Focus the current card once its node attaches. True when focus landed. */
    suspend fun restoreFocus(maxFrames: Int = 30): Boolean = requesters[focusedIndex]?.requestFocusWhenAttached(maxFrames) == true
}

/**
 * Remember a [RowFocusState] for [items]. Requesters are pre-created inside remember
 * (FocusRequester is @RememberInComposition, so it can't be constructed lazily in
 * composition); index and key survive navigation via rememberSaveable.
 */
@Composable
fun <T> rememberRowFocusState(items: List<T>): RowFocusState {
    val requesters = remember(items) { items.indices.associateWith { FocusRequester() } }
    val index = rememberSaveable { mutableStateOf(0) }
    val key = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(requesters) { RowFocusState(requesters, index, key) }
}
