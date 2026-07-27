package app.picnic.player.playback

import app.picnic.player.data.auth.ServerConnection
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.playback.StreamInfo
import app.picnic.player.data.playback.quality.QualityRung
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamLoaderTest {

    @Test
    fun firstPlayLoadsWithoutStoppingAnything() = runBlocking {
        val target = RecordingTarget()
        val negotiator = FakeNegotiator()
        val loader = StreamLoader(target, negotiator)

        val result = loader.load(request(replacing = null, positionTicks = 0))

        assertTrue(result is StreamResult.Loaded)
        assertEquals(listOf("load@0", "resume(true)"), target.calls)
        assertEquals(emptyList<String>(), negotiator.teardownCalls)
    }

    @Test
    fun replacingStopsBeforeReadingThePosition() = runBlocking {
        val target = RecordingTarget(currentPositionMs = 84_000)
        val loader = StreamLoader(target, FakeNegotiator())

        loader.load(request(replacing = existing))

        assertEquals("stop", target.calls.first())
        assertTrue(target.positionReadAfterStop)
    }

    @Test
    fun replacingEndsTheEncoderBeforeNegotiating() = runBlocking {
        val negotiator = FakeNegotiator()
        val loader = StreamLoader(RecordingTarget(), negotiator)

        loader.load(request(replacing = existing))

        assertEquals(listOf("stopEncoding(old-session)", "reportStopped", "resolve", "reportStarted"), negotiator.orderedCalls)
    }

    @Test
    fun theStreamResumesAtThePositionPlaybackReached() = runBlocking {
        val target = RecordingTarget(currentPositionMs = 84_000)
        val loader = StreamLoader(target, FakeNegotiator())

        val result = loader.load(request(replacing = existing, positionTicks = 1_000))

        assertEquals(840_000_000L, (result as StreamResult.Loaded).positionTicks)
        assertEquals(listOf("stop", "load@84000", "resume(true)"), target.calls)
    }

    @Test
    fun firstPlayUsesTheRequestedPosition() = runBlocking {
        val target = RecordingTarget(currentPositionMs = 99_000)
        val loader = StreamLoader(target, FakeNegotiator())

        loader.load(request(replacing = null, positionTicks = 300_000_000L))

        assertEquals(listOf("load@30000", "resume(true)"), target.calls)
    }

    @Test
    fun aFailedNegotiationPutsThePreviousStreamBack() = runBlocking {
        val target = RecordingTarget(currentPositionMs = 84_000)
        val negotiator = FakeNegotiator(failResolve = true)
        val loader = StreamLoader(target, negotiator)

        val result = loader.load(request(replacing = existing))

        assertSame(existing, (result as StreamResult.Failed).restored)
        assertEquals(listOf("stop", "load@84000", "resume(true)"), target.calls)
    }

    @Test
    fun aFailedFirstPlayLoadsNothing() = runBlocking {
        val target = RecordingTarget()
        val loader = StreamLoader(target, FakeNegotiator(failResolve = true))

        val result = loader.load(request(replacing = null))

        assertTrue(result is StreamResult.Failed)
        assertEquals(emptyList<String>(), target.calls)
    }

    @Test
    fun playbackStaysPausedWhenItWasPaused() = runBlocking {
        val target = RecordingTarget()
        val loader = StreamLoader(target, FakeNegotiator())

        loader.load(request(replacing = null, resumePlaying = false))

        assertTrue(target.calls.contains("resume(false)"))
    }

    private val existing = stream("old-session")

    private fun request(
        replacing: StreamInfo?,
        positionTicks: Long = 0,
        resumePlaying: Boolean = true
    ) = StreamRequest(
        session = session,
        itemId = itemId,
        seriesId = null,
        positionTicks = positionTicks,
        mediaSourceId = "source",
        rung = null,
        audioStreamIndex = null,
        subtitleStreamIndex = null,
        resumePlaying = resumePlaying,
        replacing = replacing
    )

    private class FakeNegotiator(private val failResolve: Boolean = false) : StreamNegotiator {
        val orderedCalls = mutableListOf<String>()
        val teardownCalls = mutableListOf<String>()

        override suspend fun resolveStream(
            session: UserSession,
            itemId: UUID,
            startTicks: Long?,
            mediaSourceId: String?,
            rung: QualityRung?,
            audioStreamIndex: Int?,
            subtitleStreamIndex: Int?
        ): StreamInfo {
            orderedCalls += "resolve"
            if (failResolve) error("negotiation failed")
            return stream("new-session")
        }

        override suspend fun stopEncoding(session: UserSession, playSessionId: String?) {
            orderedCalls += "stopEncoding($playSessionId)"
            teardownCalls += "stopEncoding"
        }

        override suspend fun reportStarted(session: UserSession, info: StreamInfo, itemId: UUID, positionTicks: Long) {
            orderedCalls += "reportStarted"
        }

        override suspend fun reportStopped(
            session: UserSession,
            info: StreamInfo,
            itemId: UUID,
            positionTicks: Long,
            seriesId: UUID?
        ) {
            orderedCalls += "reportStopped"
            teardownCalls += "reportStopped"
        }
    }

    private class RecordingTarget(private val currentPositionMs: Long = 0) : StreamTarget {
        val calls = mutableListOf<String>()
        var positionReadAfterStop = false
            private set

        override val positionMs: Long
            get() {
                if (calls.contains("stop")) positionReadAfterStop = true
                return currentPositionMs
            }

        override fun stop() {
            calls += "stop"
        }

        override fun load(stream: StreamInfo, resumeMs: Long) {
            calls += "load@$resumeMs"
        }

        override fun resume(playing: Boolean) {
            calls += "resume($playing)"
        }
    }
}

private val itemId: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

private val session = UserSession(
    server = ServerConnection(id = "s", baseUrl = "https://example.test", name = "test"),
    userId = "00000000-0000-0000-0000-0000000000bb",
    username = "tester",
    accessToken = "token"
)

private fun stream(playSessionId: String) = StreamInfo(
    url = "https://example.test/stream",
    playMethod = app.picnic.player.data.playback.PlayMethodKind.TRANSCODE,
    playSessionId = playSessionId,
    mediaSourceId = "source",
    runTimeTicks = 60_000_000_000L
)
