package app.picnic.player.playback

import kotlin.math.abs

enum class AudioRoute { NATIVE, PCM }

object AudioRoutePolicy {
    fun requiredRoute(boost: AudioBoost, nightMode: NightMode, speed: Float): AudioRoute {
        val effectOn = boost != AudioBoost.OFF ||
            nightMode != NightMode.OFF ||
            abs(speed - 1f) > SPEED_EPSILON
        return if (effectOn) AudioRoute.PCM else AudioRoute.NATIVE
    }

    fun reloadNeeded(
        required: AudioRoute,
        applied: AudioRoute,
        trackCanPassThrough: Boolean
    ): Boolean = required != applied && trackCanPassThrough

    private const val SPEED_EPSILON = 0.001f
}
