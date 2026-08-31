@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package app.picnic.player.ui.settings

import androidx.compose.foundation.focusGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import app.picnic.player.ui.common.requestFocusWhenAttached

@Stable
internal class AccountRowFocus(
    val rowFocus: FocusRequester,
    val cardFocus: FocusRequester,
    keyState: MutableState<String?>,
    slotState: MutableState<Int>
) {
    var focusedKey: String? by keyState
        private set

    private var lastSlot: Int by slotState

    fun focusIndex(keys: List<String>): Int {
        if (keys.isEmpty()) return 0
        val byKey = focusedKey?.let { keys.indexOf(it) }?.takeIf { it >= 0 }
        return byKey ?: lastSlot.coerceIn(0, keys.lastIndex)
    }

    fun onItemFocused(slot: Int, key: String) {
        lastSlot = slot
        focusedKey = key
    }

    fun rowModifier(): Modifier = Modifier
        .focusRequester(rowFocus)
        .focusRestorer(cardFocus)
        .focusGroup()

    suspend fun restore(keys: List<String>, maxFrames: Int = 20): Boolean {
        if (keys.isEmpty()) return rowFocus.requestFocusWhenAttached(maxFrames)
        focusedKey = keys[focusIndex(keys)]
        return cardFocus.requestFocusWhenAttached(maxFrames)
    }
}

@Composable
internal fun rememberAccountRowFocus(): AccountRowFocus {
    val rowFocus = remember { FocusRequester() }
    val cardFocus = remember { FocusRequester() }
    val key = rememberSaveable { mutableStateOf<String?>(null) }
    val slot = rememberSaveable { mutableIntStateOf(0) }
    return remember { AccountRowFocus(rowFocus, cardFocus, key, slot) }
}
