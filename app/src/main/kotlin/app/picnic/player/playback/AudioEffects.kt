package app.picnic.player.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build

/**
 * The session audio effects: volume boost and night-mode compression, bound to one player's audio
 * session. Both are built on first use and kept until [release] — an effect cannot outlive the
 * session it attached to, and rebuilding one per change drops audio.
 *
 * [audioSessionId] is read lazily rather than passed in: the player has no session id until it is
 * prepared, which is after the engine that owns these effects is constructed.
 *
 * Every call is best-effort. The audio-effect framework refuses to attach on some devices and for
 * some output routes (notably passthrough), which is a reason for the boost to do nothing, not a
 * reason to fail playback.
 */
class AudioEffects(private val audioSessionId: () -> Int) {

    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    /**
     * Apply a gain in millibels (0 = off). Disabled rather than released at 0 so it can re-enable
     * without rebuilding.
     */
    fun setBoostMillibels(gainMb: Int) {
        runCatching {
            if (gainMb <= 0) {
                loudnessEnhancer?.enabled = false
                return
            }
            val fx = loudnessEnhancer ?: LoudnessEnhancer(audioSessionId()).also { loudnessEnhancer = it }
            fx.setTargetGain(gainMb)
            fx.enabled = true
        }
    }

    /**
     * Enable dynamic-range compression at [strength] (0 = off, 1 = light, 2 = strong). A no-op
     * below API 28. Compresses loud peaks and lifts quiet dialogue so low-volume late-night
     * viewing stays intelligible.
     */
    fun setNightMode(strength: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            if (strength <= 0) {
                dynamicsProcessing?.enabled = false
                return
            }
            val dp = dynamicsProcessing ?: build().also { dynamicsProcessing = it }
            applyStrength(dp, strength)
            dp.enabled = true
        }
    }

    fun release() {
        runCatching { loudnessEnhancer?.release() }
        runCatching { dynamicsProcessing?.release() }
        loudnessEnhancer = null
        dynamicsProcessing = null
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun build(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ false, /* preEqBandCount = */ 0,
            /* mbcInUse = */ true, /* mbcBandCount = */ 1,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ true
        ).build()
        return DynamicsProcessing(0, audioSessionId(), config)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun applyStrength(dp: DynamicsProcessing, strength: Int) {
        // Light vs strong: lower threshold + higher ratio + more makeup gain compresses harder.
        val threshold = if (strength >= 2) -34f else -28f
        val ratio = if (strength >= 2) 6f else 3f
        val postGain = if (strength >= 2) 7f else 3f
        val limiterThreshold = if (strength >= 2) -1f else -2f
        for (ch in 0 until dp.channelCount) {
            runCatching {
                val mbc = dp.getMbcByChannelIndex(ch)
                val band = mbc.getBand(0)
                band.isEnabled = true
                band.attackTime = 5f
                band.releaseTime = 120f
                band.ratio = ratio
                band.threshold = threshold
                band.postGain = postGain
                mbc.setBand(0, band)
                val limiter = dp.getLimiterByChannelIndex(ch)
                limiter.isEnabled = true
                limiter.threshold = limiterThreshold
                dp.setLimiterAllChannelsTo(limiter)
            }
        }
    }
}
