@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package app.picnic.player.playback

import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import io.github.peerless2012.ass.media.render.AssRenderer
import java.util.concurrent.atomic.AtomicLong

/**
 * Decorates a subtitle [Renderer] to apply a presentation-time offset. Only [render]
 * is overridden — every press of the timeline still reaches the delegate, but it is
 * told the clock is [delayUs] earlier/later, which shifts which cues are active.
 *
 * Positive [delayUs] → subtitles appear later; negative → earlier. Works for both the
 * media3 text renderer and libass (whose [AssRenderer] drives rendering off `render`).
 * The optional [baseOffsetUs] applies only to libass, on top of the session delay.
 */
class SubtitleDelayRenderer(
    private val delegate: Renderer,
    private val delayUs: AtomicLong,
    private val baseOffsetUs: AtomicLong? = null
) : Renderer by delegate {
    override fun render(positionUs: Long, elapsedRealtimeUs: Long) {
        delegate.render(positionUs - delayUs.get() - (baseOffsetUs?.get() ?: 0L), elapsedRealtimeUs)
    }
}

/**
 * Wraps a [RenderersFactory] so every text/ASS subtitle renderer it produces is
 * decorated with [SubtitleDelayRenderer], sharing one [delayUs] holder. Only libass
 * also receives [assOffsetUs], so device calibration never shifts text or bitmap cues.
 */
class SubtitleDelayRenderersFactory(
    private val delegate: RenderersFactory,
    private val delayUs: AtomicLong,
    private val assOffsetUs: AtomicLong
) : RenderersFactory by delegate {
    override fun createRenderers(
        eventHandler: android.os.Handler,
        videoRendererEventListener: androidx.media3.exoplayer.video.VideoRendererEventListener,
        audioRendererEventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        textRendererOutput: androidx.media3.exoplayer.text.TextOutput,
        metadataRendererOutput: androidx.media3.exoplayer.metadata.MetadataOutput
    ): Array<Renderer> = delegate.createRenderers(
        eventHandler,
        videoRendererEventListener,
        audioRendererEventListener,
        textRendererOutput,
        metadataRendererOutput
    ).map { renderer ->
        when {
            renderer is AssRenderer -> SubtitleDelayRenderer(renderer, delayUs, assOffsetUs)
            renderer.trackType == androidx.media3.common.C.TRACK_TYPE_TEXT -> SubtitleDelayRenderer(renderer, delayUs)
            else -> renderer
        }
    }.toTypedArray()
}
