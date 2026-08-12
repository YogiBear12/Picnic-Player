package app.picnic.player.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import androidx.annotation.RequiresApi

class AudioEffects {
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    private var sessionId = NO_SESSION
    private var channelCount = DEFAULT_CHANNEL_COUNT
    private var gainMb = 0
    private var strength = 0
    private var decoding = false

    fun onAudioSessionId(id: Int) {
        if (id == sessionId) return
        releaseEffects()
        sessionId = id
        applyBoost()
        applyNightMode()
    }

    fun onDecoding(decoding: Boolean) {
        if (decoding == this.decoding) return
        this.decoding = decoding
        if (decoding) {
            applyBoost()
            applyNightMode()
        } else {
            releaseEffects()
        }
    }

    fun onChannelCount(count: Int) {
        if (count <= 0 || count == channelCount) return
        channelCount = count
        runCatching { dynamicsProcessing?.release() }
        dynamicsProcessing = null
        applyNightMode()
    }

    fun setBoostMillibels(gainMb: Int) {
        this.gainMb = gainMb
        applyBoost()
    }

    fun setNightMode(strength: Int) {
        this.strength = strength
        applyNightMode()
    }

    fun release() {
        releaseEffects()
        sessionId = NO_SESSION
        decoding = false
    }

    private fun releaseEffects() {
        runCatching { loudnessEnhancer?.release() }
        runCatching { dynamicsProcessing?.release() }
        loudnessEnhancer = null
        dynamicsProcessing = null
    }

    private fun applyBoost() {
        if (gainMb <= 0) {
            runCatching { loudnessEnhancer?.enabled = false }
            return
        }
        if (sessionId == NO_SESSION || !decoding) return
        val fx = loudnessEnhancer
            ?: attach("boost") { LoudnessEnhancer(sessionId) }?.also { loudnessEnhancer = it }
            ?: return
        attach("boost") {
            fx.setTargetGain(gainMb)
            fx.enabled = true
        }
    }

    private fun applyNightMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        if (strength <= 0) {
            runCatching { dynamicsProcessing?.enabled = false }
            return
        }
        if (sessionId == NO_SESSION || !decoding) return
        val fx = dynamicsProcessing
            ?: attach("night mode") { build() }?.also { dynamicsProcessing = it }
            ?: return
        attach("night mode") {
            applyStrength(fx, strength)
            fx.enabled = true
        }
    }

    private fun <T> attach(name: String, block: () -> T): T? = runCatching(block)
        .onFailure { PlaybackDiagnostics.log("audio effect $name failed on session $sessionId: $it") }
        .getOrNull()

    @RequiresApi(Build.VERSION_CODES.P)
    private fun build(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            channelCount,
            /* preEqInUse = */ false, /* preEqBandCount = */ 0,
            /* mbcInUse = */ true, /* mbcBandCount = */ 1,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ true
        ).build()
        return DynamicsProcessing(0, sessionId, config)
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyStrength(dp: DynamicsProcessing, strength: Int) {
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

    private companion object {
        const val NO_SESSION = 0
        const val DEFAULT_CHANNEL_COUNT = 2
    }
}
