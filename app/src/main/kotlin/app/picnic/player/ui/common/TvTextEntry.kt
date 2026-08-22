@file:OptIn(ExperimentalLayoutApi::class)

package app.picnic.player.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first

@Stable
class TvTextEntry {
    var editing by mutableStateOf(false)
        private set

    internal var keyboardShowing by mutableStateOf(false)

    var focused by mutableStateOf(false)
        internal set

    val readOnly: Boolean get() = !editing

    val holdsKeys: Boolean get() = editing && keyboardShowing

    fun start() {
        editing = true
    }

    fun stop() {
        editing = false
    }
}

@Composable
fun rememberTvTextEntry(): TvTextEntry {
    val entry = remember { TvTextEntry() }
    val keyboard = LocalSoftwareKeyboardController.current
    val imeVisible = rememberUpdatedState(WindowInsets.isImeVisible)

    LaunchedEffect(entry) {
        snapshotFlow { imeVisible.value }.collect { entry.keyboardShowing = it }
    }

    LaunchedEffect(entry) {
        snapshotFlow { entry.editing }.drop(1).collect { editing ->
            if (editing) keyboard?.show() else keyboard?.hide()
        }
    }

    LaunchedEffect(entry.editing) {
        if (!entry.editing) return@LaunchedEffect
        snapshotFlow { entry.keyboardShowing }
            .distinctUntilChanged()
            .dropWhile { it }
            .dropWhile { !it }
            .first { !it }
        entry.stop()
    }

    DisposableEffect(entry) {
        onDispose { if (entry.editing) keyboard?.hide() }
    }

    BackHandler(enabled = entry.holdsKeys) { entry.stop() }
    return entry
}

fun Modifier.tvTextEntry(entry: TvTextEntry): Modifier = this then TvTextEntryElement(entry)

private data class TvTextEntryElement(val entry: TvTextEntry) : ModifierNodeElement<TvTextEntryNode>() {
    override fun create() = TvTextEntryNode(entry)

    override fun update(node: TvTextEntryNode) {
        node.entry = entry
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "tvTextEntry"
        properties["entry"] = entry
    }
}

private class TvTextEntryNode(var entry: TvTextEntry) :
    Modifier.Node(),
    FocusEventModifierNode,
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {

    override fun onFocusEvent(focusState: FocusState) {
        entry.focused = focusState.isFocused
        if (!focusState.isFocused) entry.stop()
    }

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        if (entry.holdsKeys) return false
        val direction = when (event.key) {
            Key.DirectionLeft -> FocusDirection.Left
            Key.DirectionRight -> FocusDirection.Right
            Key.DirectionUp -> FocusDirection.Up
            Key.DirectionDown -> FocusDirection.Down
            else -> null
        }
        return when {
            direction != null ->
                if (event.type == KeyEventType.KeyDown) {
                    currentValueOf(LocalFocusManager).moveFocus(direction)
                } else {
                    true
                }
            event.key == Key.DirectionCenter || event.key == Key.Enter -> {
                if (event.type == KeyEventType.KeyUp) entry.start()
                true
            }
            else -> false
        }
    }

    override fun onKeyEvent(event: KeyEvent): Boolean = false
}
