@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.content.Context
import android.graphics.Color
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.os.Build
import android.view.ViewGroup
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.ui.SubtitleView
import app.picnic.player.data.settings.SubtitleAppearance
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.AssHandlerConfig
import io.github.peerless2012.ass.media.factory.AssRenderersFactory
import io.github.peerless2012.ass.media.kt.withAssMkvSupport
import io.github.peerless2012.ass.media.parser.AssSubtitleParserFactory
import io.github.peerless2012.ass.media.type.AssRenderType
import io.github.peerless2012.ass.media.widget.AssSubtitleView
import java.util.concurrent.atomic.AtomicLong
import okhttp3.OkHttpClient

/**
 * ExoPlayer + libass wiring. Owns renderer/track-selector setup and
 * binds once to a [PlayerView] so video surface + ASS overlay share one host.
 */
class PlaybackEngine(context: Context) {

    val assHandler: AssHandler
    val player: ExoPlayer

    private var assOverlay: AssSubtitleView? = null

    /** Shared subtitle offset (µs). Read each frame by every text/ASS renderer. */
    private val subtitleDelayUs = AtomicLong(0L)

    // Session audio effects, bound to the player's audio session. Built lazily; survive for the
    // engine's life and are released with it.
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    init {
        val renderType =
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                AssRenderType.OVERLAY_CANVAS
            } else {
                AssRenderType.OVERLAY_OPEN_GL
            }
        assHandler = AssHandler(renderType, AssHandlerConfig(maxRenderPixels = 0))
        val parserFactory = AssSubtitleParserFactory(assHandler)
        val renderersFactory = SubtitleDelayRenderersFactory(
            AssRenderersFactory(
                assHandler,
                DefaultRenderersFactory(context)
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            ),
            subtitleDelayUs
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(
            jellyfinDataSourceFactory(context),
            DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true)
                .withAssMkvSupport(parserFactory, assHandler)
        ).setSubtitleParserFactory(parserFactory)
        player = ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context))
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        assHandler.init(player)
    }

    /** Style the text-cue view (SRT/VTT/…). ASS renders in [assOverlayView], not here. */
    fun attachSubtitleView(subtitleView: SubtitleView, appearance: SubtitleAppearance) {
        subtitleView.setBackgroundColor(Color.TRANSPARENT)
        appearance.applyTo(subtitleView)
    }

    /**
     * The libass overlay. The host must size it to exactly the video display rect: libass maps
     * its frame coordinates onto this view's bounds.
     */
    fun assOverlayView(context: Context): AssSubtitleView {
        val view = assOverlay ?: AssSubtitleView(context, assHandler).also { assOverlay = it }
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    /** Set the subtitle offset in ms (positive = later, negative = earlier). Session-only. */
    fun setSubtitleDelayMs(ms: Long) {
        subtitleDelayUs.set(ms * 1000)
    }

    /**
     * Apply an audio-boost gain in millibels (0 = off) via [LoudnessEnhancer] on the player's
     * audio session. Built lazily; disabled (not released) when gain is 0 so it can re-enable.
     */
    fun setAudioBoostMillibels(gainMb: Int) {
        runCatching {
            if (gainMb <= 0) {
                loudnessEnhancer?.enabled = false
                return
            }
            val fx = loudnessEnhancer ?: LoudnessEnhancer(player.audioSessionId).also { loudnessEnhancer = it }
            fx.setTargetGain(gainMb)
            fx.enabled = true
        }
    }

    /**
     * Enable night-mode dynamic-range compression at [strength] (0 = off, 1 = light, 2 = strong).
     * Uses [DynamicsProcessing] (API 28+); a no-op on older devices. Compresses loud peaks and
     * lifts quiet dialogue so low-volume late-night viewing stays intelligible.
     */
    fun setNightMode(strength: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        runCatching {
            if (strength <= 0) {
                dynamicsProcessing?.enabled = false
                return
            }
            val dp = dynamicsProcessing ?: buildDynamicsProcessing().also { dynamicsProcessing = it }
            applyNightStrength(dp, strength)
            dp.enabled = true
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun buildDynamicsProcessing(): DynamicsProcessing {
        val config = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            /* channelCount = */ 2,
            /* preEqInUse = */ false, /* preEqBandCount = */ 0,
            /* mbcInUse = */ true, /* mbcBandCount = */ 1,
            /* postEqInUse = */ false, /* postEqBandCount = */ 0,
            /* limiterInUse = */ true
        ).build()
        return DynamicsProcessing(0, player.audioSessionId, config)
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun applyNightStrength(dp: DynamicsProcessing, strength: Int) {
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

    fun release() {
        runCatching { loudnessEnhancer?.release() }
        runCatching { dynamicsProcessing?.release() }
        player.release()
    }

    companion object {
        fun jellyfinDataSourceFactory(context: Context): DataSource.Factory {
            val upstream = OkHttpDataSource.Factory(OkHttpClient.Builder().build())
                .setUserAgent("Picnic Player")
            return DefaultDataSource.Factory(context, upstream)
        }
    }
}
