package app.picnic.player.ui.detail

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

@Stable
class EpisodeListFocus(
    val list: EpisodeFocus,
    private val restoreEpisode: MutableState<String?>,
    private val firstEntryDone: MutableState<Boolean>,
    private val lastScrolledSeason: MutableState<String?>
) {
    var claimPending by mutableStateOf(true)
        private set

    val restoreEpisodeId: String? get() = restoreEpisode.value

    fun onRetryPressed() {
        claimPending = true
        lastScrolledSeason.value = null
    }

    fun onEpisodeOpened(episodeId: String) {
        restoreEpisode.value = episodeId
    }

    suspend fun settle(seasonId: String?, restoreIndex: Int?, firstUnwatchedIndex: () -> Int) {
        if (restoreIndex != null) {
            lastScrolledSeason.value = seasonId
            if (list.focusWhenLaidOut(restoreIndex)) {
                restoreEpisode.value = null
                firstEntryDone.value = true
                claimPending = false
            }
            return
        }
        if (seasonId != lastScrolledSeason.value) {
            list.scrollTo(if (firstEntryDone.value) 0 else firstUnwatchedIndex())
            lastScrolledSeason.value = seasonId
        }
        if (claimPending && list.focusWhenLaidOut(list.targetIndex)) {
            claimPending = false
            firstEntryDone.value = true
        }
    }
}

@Composable
fun rememberEpisodeListFocus(list: EpisodeFocus, initialRestoreEpisodeId: String?): EpisodeListFocus {
    val restoreEpisode = rememberSaveable { mutableStateOf(initialRestoreEpisodeId) }
    val firstEntryDone = rememberSaveable { mutableStateOf(false) }
    val lastScrolledSeason = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(list) { EpisodeListFocus(list, restoreEpisode, firstEntryDone, lastScrolledSeason) }
}
