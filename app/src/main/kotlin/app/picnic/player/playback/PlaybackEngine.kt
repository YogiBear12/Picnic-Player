@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import android.content.Context
import android.graphics.Color
import android.os.Build
import android.view.ViewGroup
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioCapabilities
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
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
import kotlinx.coroutines.flow.update
import okhttp3.OkHttpClient

class PlaybackEngine(private val context: Context, httpClient: OkHttpClient) {
    val assHandler: AssHandler
    val player: ExoPlayer

    private var assOverlay: AssSubtitleView? = null

    private val subtitleDelayUs = AtomicLong(0L)

    val audioEffects = AudioEffects()

    private var audioRouteSink: AudioRouteSink? = null

    fun setAudioRoute(route: AudioRoute) {
        audioRouteSink?.route = route
    }

    fun currentAudioCanPassThrough(): Boolean {
        val format = player.audioFormat ?: return false
        return AudioCapabilities.getCapabilities(context).isPassthroughPlaybackSupported(format)
    }

    private val _videoOutput = MutableStateFlow<VideoOutput?>(null)

    val videoOutput: StateFlow<VideoOutput?> = _videoOutput.asStateFlow()

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
                InstrumentedRenderersFactory(
                    context = context,
                    onSinkBuilt = { audioRouteSink = it },
                    onVideoDecoderConfigured = { decoder, format ->
                        _videoOutput.value = VideoOutput(format, decoder)
                    }
                )
                    .setEnableDecoderFallback(true)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            ),
            subtitleDelayUs
        )
        val extractors = instrumentExtractors(
            DefaultExtractorsFactory()
                .setConstantBitrateSeekingEnabled(true)
                .setConstantBitrateSeekingAlwaysEnabled(true)
                .withAssMkvSupport(parserFactory, assHandler)
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(
            jellyfinDataSourceFactory(context, httpClient),
            recoverLateTracks(dolbyVisionExtractors(context, extractors))
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
                _videoOutput.update { VideoOutput(format, it?.decoder) }
            }

            override fun onAudioSessionIdChanged(eventTime: AnalyticsListener.EventTime, audioSessionId: Int) {
                audioEffects.onAudioSessionId(audioSessionId)
            }

            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: DecoderReuseEvaluation?
            ) {
                audioEffects.onChannelCount(format.channelCount)
                PlaybackDiagnostics.logAudioDecoderCandidates(context, format)
                PlaybackDiagnostics.logAudioOutputCapabilities(context, format)
            }

            override fun onAudioDecoderInitialized(
                eventTime: AnalyticsListener.EventTime,
                decoderName: String,
                initializedTimestampMs: Long,
                initializationDurationMs: Long
            ) {
                audioEffects.onDecoding(true)
            }

            override fun onAudioDisabled(
                eventTime: AnalyticsListener.EventTime,
                decoderCounters: DecoderCounters
            ) {
                audioEffects.onDecoding(false)
            }
        })
    }

    fun attachSubtitleView(
        subtitleView: SubtitleView,
        appearance: SubtitleAppearance,
        bottomPaddingFraction: Float,
        range: VideoDynamicRange
    ) {
        subtitleView.setBackgroundColor(Color.TRANSPARENT)
        appearance.applyTo(subtitleView, bottomPaddingFraction, range)
    }

    fun assOverlayView(context: Context): AssSubtitleView {
        val view = assOverlay ?: AssSubtitleView(context, assHandler).also { assOverlay = it }
        (view.parent as? ViewGroup)?.removeView(view)
        return view
    }

    /** Positive = later, negative = earlier. Session-only, never persisted. */
    fun setSubtitleDelayMs(ms: Long) {
        subtitleDelayUs.set(ms * 1000)
    }

    fun release() {
        audioEffects.release()
        player.release()
    }
}

private class InstrumentedRenderersFactory(
    context: Context,
    private val onSinkBuilt: (AudioRouteSink) -> Unit,
    private val onVideoDecoderConfigured: (VideoDecoder, Format) -> Unit
) : DefaultRenderersFactory(context) {
    override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
        val delegate = super.getCodecAdapterFactory()
        return MediaCodecAdapter.Factory { configuration ->
            val adapter = delegate.createAdapter(configuration)
            if (MimeTypes.isVideo(configuration.codecInfo.mimeType)) {
                onVideoDecoderConfigured(
                    VideoDecoder(
                        name = configuration.codecInfo.name,
                        mimeType = configuration.codecInfo.mimeType,
                        hardwareAccelerated = configuration.codecInfo.hardwareAccelerated
                    ),
                    configuration.format
                )
            }
            adapter
        }
    }

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean
    ): AudioSink? = super.buildAudioSink(context, enableFloatOutput, enableAudioTrackPlaybackParams)
        ?.let { AudioRouteSink(it).also(onSinkBuilt) }
}

data class VideoOutput(
    val format: Format,
    val decoder: VideoDecoder?
)

data class VideoDecoder(
    val name: String,
    val mimeType: String,
    val hardwareAccelerated: Boolean
)

private fun jellyfinDataSourceFactory(context: Context, httpClient: OkHttpClient): DataSource.Factory {
    val upstream = OkHttpDataSource.Factory(httpClient)
        .setUserAgent("Picnic Player")
    return DefaultDataSource.Factory(context, upstream)
}
