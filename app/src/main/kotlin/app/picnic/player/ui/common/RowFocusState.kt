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

@Stable
class RowFocusState internal constructor(
    private val requesters: MutableMap<Int, FocusRequester>,
    indexState: MutableState<Int>,
    keyState: MutableState<String?>
) {
    var focusedIndex: Int by indexState
        private set
    var focusedKey: String? by keyState
        private set

    fun requesterAt(index: Int): FocusRequester = requesters.getOrPut(index) { FocusRequester() }

    fun onItemFocused(index: Int, key: String? = null) {
        focusedIndex = index
        focusedKey = key
    }

    fun rowModifier(): Modifier = Modifier
        .focusGroup()
        .focusProperties { enter = { requesterAt(focusedIndex) } }

    fun <T> resolveAgainst(items: List<T>, keyOf: ((T) -> String)? = null): Boolean {
        if (items.isEmpty()) return false
        val saved = focusedKey
        if (saved == null || keyOf == null) {
            focusedIndex = focusedIndex.coerceIn(0, items.lastIndex)
            return true
        }
        val byKey = items.indexOfFirst { keyOf(it) == saved }
        if (byKey < 0) return false
        focusedIndex = byKey
        return true
    }

    suspend fun restoreFocus(maxFrames: Int = 30): Boolean = requesterAt(focusedIndex).requestFocusWhenAttached(maxFrames)
}

@Composable
fun rememberRowFocusState(): RowFocusState {
    val requesters = remember { mutableMapOf<Int, FocusRequester>() }
    val index = rememberSaveable { mutableStateOf(0) }
    val key = rememberSaveable { mutableStateOf<String?>(null) }
    return remember { RowFocusState(requesters, index, key) }
}
