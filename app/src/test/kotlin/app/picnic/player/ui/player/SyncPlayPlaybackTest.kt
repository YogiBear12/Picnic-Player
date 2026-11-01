package app.picnic.player.ui.player

import androidx.media3.common.Player
import app.picnic.player.data.playback.ticksToMs
import app.picnic.player.data.syncplay.SyncCommand
import app.picnic.player.data.syncplay.SyncPlayRoomApi
import app.picnic.player.data.syncplay.SyncPlaySession
import java.lang.reflect.Proxy
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupRepeatMode
import org.jellyfin.sdk.model.api.GroupShuffleMode
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.PlayQueueUpdateReason
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayGroupJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayPlayQueueUpdate
import org.jellyfin.sdk.model.api.SyncPlayQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlayPlaybackTest {
    private val seek = SyncCommand(SendCommandType.SEEK, whenServerMs = 1_000, positionTicks = 600_000_000)

    @Test
    fun aNewCommandIsNeverRedundant() {
        assertFalse(isRedundant(seek, last = null, playWhenReady = false, positionMs = 60_000))
        assertFalse(isRedundant(seek, last = seek.copy(whenServerMs = 2_000), playWhenReady = false, positionMs = 60_000))
    }

    @Test
    fun aRepeatThePlayerAlreadyAgreesWithIsDropped() {
        assertTrue(isRedundant(seek, last = seek, playWhenReady = false, positionMs = 60_100))
        val unpause = seek.copy(type = SendCommandType.UNPAUSE)
        assertTrue(isRedundant(unpause, last = unpause, playWhenReady = true, positionMs = 0))
        val pause = seek.copy(type = SendCommandType.PAUSE)
        assertTrue(isRedundant(pause, last = pause, playWhenReady = false, positionMs = 0))
    }

    @Test
    fun aRepeatThePlayerDisagreesWithIsReapplied() {
        assertFalse(isRedundant(seek, last = seek, playWhenReady = false, positionMs = 61_000))
        assertFalse(isRedundant(seek, last = seek, playWhenReady = true, positionMs = 60_000))
        val unpause = seek.copy(type = SendCommandType.UNPAUSE)
        assertFalse(isRedundant(unpause, last = unpause, playWhenReady = false, positionMs = 0))
    }

    @Test
    fun aQuickSkipBurstLandsAtOnceAndSendsTheRoomOneSeek() = room {
        transport.seekTo(10_000)
        advanceTimeBy(100)
        transport.seekTo(20_000)
        transport.seekTo(30_000)
        assertEquals(30_000L, fake.positionMs)
        advanceTimeBy(SETTLE_MS)
        runCurrent()
        assertEquals(listOf("seek 30000"), server.calls)
    }

    @Test
    fun onlyAStallLongerThanThreeSecondsHoldsTheRoom() = room {
        fake.setState(Player.STATE_BUFFERING)
        advanceTimeBy(1_000)
        fake.setState(Player.STATE_READY)
        advanceTimeBy(5_000)
        assertEquals(emptyList<String>(), server.calls)

        fake.positionMs = 7_000
        fake.playWhenReady = true
        fake.setState(Player.STATE_BUFFERING)
        advanceTimeBy(3_100)
        assertFalse(fake.playWhenReady)
        fake.setState(Player.STATE_READY)
        runCurrent()
        assertEquals(listOf("buffering 7000", "ready 7000 false"), server.calls)
    }

    @Test
    fun aSeekCommandReportsReadyOnlyOnceTheSeekHasLanded() = room {
        fake.setState(Player.STATE_BUFFERING)
        session.onCommand(SendCommand(groupId, entryId, LocalDateTime.now().minusSeconds(1), 600_000_000L, SendCommandType.SEEK, LocalDateTime.now()))
        advanceTimeBy(300)
        assertEquals(60_000L, fake.positionMs)
        assertEquals(emptyList<String>(), server.calls)
        fake.setState(Player.STATE_READY)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("ready 60000 false"), server.calls)
    }

    @Test
    fun aReconnectJoinsTheRoomAgainAndReportsReady() = room {
        fake.positionMs = 5_000
        fake.playWhenReady = true
        session.onReconnected()
        runCurrent()
        assertFalse(fake.playWhenReady)
        assertEquals(listOf("join", "ready 5000 false"), server.calls)
    }

    @Test
    fun aRefreshRateHoldKeepsTheRoomWaitingUntilItEnds() = room {
        transport.hold()
        runCurrent()
        session.onCommand(SendCommand(groupId, entryId, LocalDateTime.now().minusSeconds(1), 600_000_000L, SendCommandType.SEEK, LocalDateTime.now()))
        advanceTimeBy(300)
        runCurrent()
        assertEquals(listOf("buffering 0"), server.calls)
        transport.endHold()
        runCurrent()
        assertEquals(listOf("buffering 0", "ready 60000 false"), server.calls)
    }

    @Test
    fun aRoomKeepsItsPlayStateThroughAStreamReload() = room {
        fake.playWhenReady = false
        transport.restore(true)
        assertFalse(fake.playWhenReady)
    }

    @Test
    fun aHoldOutsideARoomResumesOnlyWhatWasPlaying() {
        val fake = FakePlayer()
        val transport = PlayerTransport(fake.player)
        transport.hold()
        transport.endHold()
        assertFalse(fake.playWhenReady)

        fake.playWhenReady = true
        transport.hold()
        transport.hold()
        assertFalse(fake.playWhenReady)
        transport.endHold()
        assertTrue(fake.playWhenReady)

        fake.playbackState = Player.STATE_BUFFERING
        transport.hold()
        transport.endHold()
        assertTrue(fake.playWhenReady)
    }

    private val groupId = UUID.randomUUID()
    private val entryId = UUID.randomUUID()

    private class Room(val fake: FakePlayer, val transport: PlayerTransport, val session: SyncPlaySession, val server: FakeServer, private val test: TestScope) {
        fun advanceTimeBy(ms: Long) = test.advanceTimeBy(ms)

        fun runCurrent() = test.runCurrent()
    }

    // Starts a member in a queued room, then drops the Buffering/Ready pair every member sends on entry.
    private fun room(block: suspend Room.() -> Unit) = runTest {
        val session = SyncPlaySession()
        val now = LocalDateTime.now()
        session.onGroupUpdate(SyncPlayGroupJoinedUpdate(groupId, GroupInfoDto(groupId, "Room", GroupStateType.PLAYING, listOf("me"), now)))
        session.onGroupUpdate(
            SyncPlayPlayQueueUpdate(
                groupId,
                PlayQueueUpdate(
                    PlayQueueUpdateReason.NEW_PLAYLIST,
                    now,
                    listOf(SyncPlayQueueItem(UUID.randomUUID(), entryId)),
                    0,
                    0L,
                    false,
                    GroupShuffleMode.SORTED,
                    GroupRepeatMode.REPEAT_NONE
                )
            )
        )
        val fake = FakePlayer()
        val transport = PlayerTransport(fake.player)
        val server = FakeServer()
        SyncPlayPlayback(groupId, fake.player, transport, backgroundScope, {}, {}, session, server, backgroundScope).start()
        runCurrent()
        assertEquals(listOf("buffering 0", "ready 0 false"), server.calls)
        server.calls.clear()
        block(Room(fake, transport, session, server, this))
    }

    private class FakeServer : SyncPlayRoomApi {
        val calls = mutableListOf<String>()
        override suspend fun join(groupId: UUID) {
            calls += "join"
        }
        override suspend fun leave() {
            calls += "leave"
        }
        override suspend fun pause() {
            calls += "pause"
        }
        override suspend fun unpause() {
            calls += "unpause"
        }
        override suspend fun seek(positionTicks: Long) {
            calls += "seek ${positionTicks.ticksToMs()}"
        }
        override suspend fun buffering(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID) {
            calls += "buffering ${positionTicks.ticksToMs()}"
        }
        override suspend fun ready(positionTicks: Long, isPlaying: Boolean, playlistItemId: UUID) {
            calls += "ready ${positionTicks.ticksToMs()} $isPlaying"
        }
        override suspend fun keepClockInSync(): Nothing = awaitCancellation()
        override suspend fun awaitClockSynced() = Unit
        override fun toLocalMs(serverMs: Long): Long = serverMs
    }

    // A Player with only the state the room logic reads and writes; every other call returns a default.
    private class FakePlayer {
        var playWhenReady = false
        var positionMs = 0L
        var playbackState = Player.STATE_READY
        private val listeners = mutableListOf<Player.Listener>()

        val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, args ->
            when (method.name) {
                "getPlayWhenReady" -> playWhenReady
                "getCurrentPosition" -> positionMs
                "getPlaybackState" -> playbackState
                "isPlaying" -> playWhenReady && playbackState == Player.STATE_READY
                "play" -> playWhenReady = true
                "pause" -> playWhenReady = false
                "seekTo" -> if (args?.size == 1) positionMs = args[0] as Long else Unit
                "addListener" -> listeners += args!![0] as Player.Listener
                "removeListener" -> listeners -= args!![0] as Player.Listener
                else -> null
            }.let { result -> if (method.returnType == Void.TYPE) null else result ?: defaultFor(method.returnType) }
        } as Player

        fun setState(state: Int) {
            playbackState = state
            listeners.toList().forEach { it.onPlaybackStateChanged(state) }
        }

        private fun defaultFor(type: Class<*>): Any? = when (type) {
            java.lang.Boolean.TYPE -> false
            Integer.TYPE -> 0
            java.lang.Long.TYPE -> 0L
            java.lang.Float.TYPE -> 0f
            java.lang.Double.TYPE -> 0.0
            else -> null
        }
    }

    private companion object {
        const val SETTLE_MS = 500L
    }
}
