package app.picnic.player.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.flow.first

@Composable
fun rememberRowRevealed(
    listState: LazyListState,
    builtRows: MutableSet<String>,
    rowKey: String
): State<Boolean> {
    val revealed = remember {
        mutableStateOf(
            rowKey in builtRows || Snapshot.withoutReadObservation { !listState.isScrollInProgress }
        )
    }
    LaunchedEffect(Unit) {
        if (!revealed.value) {
            snapshotFlow { listState.isScrollInProgress }.first { !it }
            revealed.value = true
        }
        builtRows += rowKey
    }
    return revealed
}
