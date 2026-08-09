package app.picnic.player.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CueLatchedBars {
    private var track: BlackBarTrack = BlackBarTrack.None

    private val _bars = MutableStateFlow(BlackBars.None)
    val bars: StateFlow<BlackBars> = _bars.asStateFlow()

    fun setTrack(track: BlackBarTrack, positionMs: Long) {
        this.track = track
        _bars.value = track.at(positionMs)
    }

    fun onCueBoundary(positionMs: Long) {
        _bars.value = track.at(positionMs)
    }
}
