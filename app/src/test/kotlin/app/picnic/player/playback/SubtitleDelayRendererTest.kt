package app.picnic.player.playback

import androidx.media3.common.C
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.RenderersFactory
import io.github.peerless2012.ass.media.AssHandler
import io.github.peerless2012.ass.media.render.AssRenderer
import io.github.peerless2012.ass.media.type.AssRenderType
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SubtitleDelayRendererTest {
    private val delayUs = AtomicLong(100_000L)
    private val baseUs = AtomicLong(250_000L)

    @Test
    fun libassGetsSessionDelayPlusBase() {
        assertEquals(9_650_000L, renderedAt(10_000_000L, SubtitleDelayRenderer(recorder(), delayUs, baseUs)))
    }

    @Test
    fun textGetsSessionDelayOnly() {
        assertEquals(9_900_000L, renderedAt(10_000_000L, SubtitleDelayRenderer(recorder(), delayUs)))
    }

    @Test
    fun negativeBaseShowsSubtitlesEarlier() {
        baseUs.set(-250_000L)
        assertEquals(10_150_000L, renderedAt(10_000_000L, SubtitleDelayRenderer(recorder(), delayUs, baseUs)))
    }

    @Test
    fun factoryGivesLibassTheBaseAndTextTheSessionDelayOnly() {
        val text = recorder(C.TRACK_TYPE_TEXT)
        val audio = recorder(C.TRACK_TYPE_AUDIO)
        val assHandler = AssHandler(AssRenderType.OVERLAY_CANVAS)
        val delegate = RenderersFactory { _, _, _, _, _ -> arrayOf(text, AssRenderer(assHandler), audio) }
        val (wrappedText, wrappedAss, wrappedAudio) = SubtitleDelayRenderersFactory(delegate, delayUs, baseUs)
            .createRenderers(
                android.os.Handler(android.os.Looper.getMainLooper()),
                object : androidx.media3.exoplayer.video.VideoRendererEventListener {},
                object : androidx.media3.exoplayer.audio.AudioRendererEventListener {},
                { },
                { }
            )

        wrappedText.render(10_000_000L, 0L)
        assertEquals(9_900_000L, lastPositionUs)
        wrappedAss.render(10_000_000L, 0L)
        assertEquals(9_650_000L, assHandler.videoTime + AssRendererTimeBiasUs)
        assertSame(audio, wrappedAudio)
    }

    // AssRenderer subtracts this from every position it hands the handler.
    private val AssRendererTimeBiasUs = 1_000_000_000_000L

    private var lastPositionUs = -1L

    private fun recorder(trackType: Int = C.TRACK_TYPE_TEXT): Renderer = Proxy.newProxyInstance(
        Renderer::class.java.classLoader,
        arrayOf(Renderer::class.java)
    ) { _, method, args ->
        when (method.name) {
            "render" -> lastPositionUs = args[0] as Long
            "getTrackType" -> return@newProxyInstance trackType
        }
        null
    } as Renderer

    private fun renderedAt(positionUs: Long, renderer: SubtitleDelayRenderer): Long {
        renderer.render(positionUs, 0L)
        return lastPositionUs
    }
}
