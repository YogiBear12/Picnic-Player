package app.picnic.player.ui.common

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp

@Stable
class LoadingFocusHolder {
    val requester = FocusRequester()

    var focused by mutableStateOf(false)
        internal set

    /**
     * Stays true while the holder has focus after loading ends, so the seed into real content moves
     * focus off it before it leaves; removing a focused node drops focus to an arbitrary fallback.
     */
    fun holds(loading: Boolean): Boolean = loading || focused
}

@Composable
fun rememberLoadingFocusHolder(): LoadingFocusHolder = remember { LoadingFocusHolder() }

@Composable
fun LoadingFocusTarget(holder: LoadingFocusHolder, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(LoadingFocusTargetSize)
            .focusRequester(holder.requester)
            .onFocusChanged { holder.focused = it.isFocused }
            .focusable()
    )
}

private val LoadingFocusTargetSize = 1.dp
