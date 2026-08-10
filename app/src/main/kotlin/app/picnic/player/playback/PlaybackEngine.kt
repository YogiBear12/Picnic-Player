@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import androidx.media3.common.Format
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient

class PlaybackEngine(context: Context, httpClient: OkHttpClient) {

    val assHandler: AssHandler
    val player: ExoPlayer

    private var assOverlay: AssSubtitleView? = null

    /** Shared subtitle offset (µs). Read each frame by every text/ASS renderer. */
    private val subtitleDelayUs = AtomicLong(0L)

    val audioEffects = AudioEffects { player.audioSessionId }

    private val _subtitleRenderRange = MutableStateFlow(SubtitleRenderRange.SDR)

    /**
     * Range of the picture cues are currently drawn over. Follows the video format through every
     * change within a session — an autoplayed episode, a quality switch — so cue styling never
     * carries the last item's range into this one.
     */
    val subtitleRenderRange: StateFlow<SubtitleRenderRange> = _subtitleRenderRange.asStateFlow()

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
            jellyfinDataSourceFactory(context, httpClient),
            recoverLateTracks(
                instrumentExtractors(
                    DefaultExtractorsFactory()
                        .setConstantBitrateSeekingEnabled(true)
                        .setConstantBitrateSeekingAlwaysEnabled(true)
                        .withAssMkvSupport(parserFactory, assHandler)
                )
            )
        ).setSubtitleParserFactory(parserFactory)
        player = ExoPlayer.Builder(context)
            .setTrackSelector(DefaultTrackSelector(context))
            .setRenderersFactory(renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        assHandler.init(player)
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onVideoInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                _subtitleRenderRange.value = subtitleRenderRange(format)
            }
        })
    }

    /** Style the text-cue view (SRT/VTT/…). ASS renders in [assOverlayView], not here. */
    fun attachSubtitleView(
        subtitleView: SubtitleView,
        appearance: SubtitleAppearance,
        bottomPaddingFraction: Float,
        range: SubtitleRenderRange
    ) {
        subtitleView.setBackgroundColor(Color.TRANSPARENT)
        appearance.applyTo(subtitleView, bottomPaddingFraction, range)
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

    fun release() {
        audioEffects.release()
        player.release()
    }
}

private fun jellyfinDataSourceFactory(context: Context, httpClient: OkHttpClient): DataSource.Factory {
    val upstream = OkHttpDataSource.Factory(httpClient)
        .setUserAgent("Picnic Player")
    return DefaultDataSource.Factory(context, upstream)
}
