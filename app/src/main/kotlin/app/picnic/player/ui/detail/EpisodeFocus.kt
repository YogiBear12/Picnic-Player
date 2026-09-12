package app.picnic.player.ui.detail

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import app.picnic.player.ui.common.requestFocusWhenVisible
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Stable
class EpisodeFocus(
    val listState: LazyListState,
    private val scope: CoroutineScope
) {
    var hasFocus by mutableStateOf(false)

    var targetIndex by mutableIntStateOf(0)
        private set

    private val requesters = mutableMapOf<Int, FocusRequester>()

    fun requesterFor(index: Int): FocusRequester = requesters.getOrPut(index) { FocusRequester() }

    val targetRequester: FocusRequester get() = requesterFor(targetIndex)

    fun onEpisodeFocused(index: Int) {
        targetIndex = index
    }

    fun focusTarget(count: Int) {
        if (count <= 0) return
        scope.launch { focusWhenLaidOut(targetIndex.coerceIn(0, count - 1)) }
    }

    fun rightConsumed(count: Int): Boolean {
        if (count <= 0) return false
        val index = targetIndex.coerceIn(0, count - 1)
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == index }) return false
        focusTarget(count)
        return true
    }

    suspend fun focusWhenLaidOut(index: Int): Boolean {
        targetIndex = index
        return requesterFor(index).requestFocusWhenVisible(listState, index) { hasFocus }
    }

    suspend fun scrollTo(index: Int) {
        targetIndex = index
        listState.scrollToItem(index)
    }
}

@Composable
fun rememberEpisodeFocus(): EpisodeFocus {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    return remember(listState, scope) { EpisodeFocus(listState, scope) }
}
