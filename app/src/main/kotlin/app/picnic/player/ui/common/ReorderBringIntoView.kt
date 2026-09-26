package app.picnic.player.ui.common

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/** A reordered item keeps focus, so no focus change scrolls it; follow its index instead. */
@Composable
fun rememberReorderBringIntoView(reordering: Boolean, index: Int): BringIntoViewRequester {
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(reordering, index) {
        if (reordering) requester.bringIntoView()
    }
    return requester
}
