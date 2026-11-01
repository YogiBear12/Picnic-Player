package app.picnic.player.data.syncplay

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupRepeatMode
import org.jellyfin.sdk.model.api.GroupShuffleMode
import org.jellyfin.sdk.model.api.GroupStateType
import org.jellyfin.sdk.model.api.GroupStateUpdate
import org.jellyfin.sdk.model.api.PlayQueueUpdate
import org.jellyfin.sdk.model.api.PlayQueueUpdateReason
import org.jellyfin.sdk.model.api.PlaybackRequestType
import org.jellyfin.sdk.model.api.SendCommand
import org.jellyfin.sdk.model.api.SendCommandType
import org.jellyfin.sdk.model.api.SyncPlayGroupDoesNotExistUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayGroupLeftUpdate
import org.jellyfin.sdk.model.api.SyncPlayLibraryAccessDeniedUpdate
import org.jellyfin.sdk.model.api.SyncPlayNotInGroupUpdate
import org.jellyfin.sdk.model.api.SyncPlayPlayQueueUpdate
import org.jellyfin.sdk.model.api.SyncPlayQueueItem
import org.jellyfin.sdk.model.api.SyncPlayStateUpdate
import org.jellyfin.sdk.model.api.SyncPlayUserJoinedUpdate
import org.jellyfin.sdk.model.api.SyncPlayUserLeftUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SyncPlaySessionTest {

    private val groupId = UUID.randomUUID()
    private val movieId = UUID.randomUUID()
    private val entryId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-31T10:00:00Z")

    private fun joined(participants: List<String> = listOf("knuckles")) = reduceGroup(
        null,
        SyncPlayGroupJoinedUpdate(groupId, GroupInfoDto(groupId, "Movie night", GroupStateType.IDLE, participants, local(now)))
    )!!

    private fun queued(entry: UUID = entryId, index: Int = 0) = SyncPlayPlayQueueUpdate(
        groupId,
        PlayQueueUpdate(
            PlayQueueUpdateReason.NEW_PLAYLIST,
            local(now),
            listOf(SyncPlayQueueItem(movieId, entry)),
            index,
            1_200_000_000L,
            false,
            GroupShuffleMode.SORTED,
            GroupRepeatMode.REPEAT_NONE
        )
    )

    private fun command(entry: UUID, type: SendCommandType = SendCommandType.UNPAUSE, group: UUID = groupId) = SendCommand(group, entry, local(now.plusSeconds(1)), 3_000L, type, local(now))

    private fun local(instant: Instant) = LocalDateTime.ofInstant(instant, ZoneId.systemDefault())

    @Test
    fun joinThenQueueThenStateFoldsIntoOneGroup() {
        val group = listOf(queued(), SyncPlayStateUpdate(groupId, GroupStateUpdate(GroupStateType.PAUSED, PlaybackRequestType.PAUSE)))
            .fold(joined()) { g, update -> reduceGroup(g, update)!! }
        assertEquals(SyncGroup(groupId, "Movie night", GroupStateType.PAUSED, listOf("knuckles"), SyncQueue(movieId, entryId, 1_200_000_000L)), group)
    }

    @Test
    fun membersJoinOnceAndLeave() {
        val group = listOf(
            SyncPlayUserJoinedUpdate(groupId, "dale"),
            SyncPlayUserJoinedUpdate(groupId, "dale"),
            SyncPlayUserLeftUpdate(groupId, "knuckles")
        ).fold(joined()) { g, update -> reduceGroup(g, update)!! }
        assertEquals(listOf("dale"), group.participants)
    }

    @Test
    fun anEmptyQueueClearsTheQueuedItem() {
        val group = reduceGroup(reduceGroup(joined(), queued()), queued(index = -1))!!
        assertNull(group.queue)
    }

    @Test
    fun leavingOrNotBeingInAGroupClearsIt() {
        assertNull(reduceGroup(joined(), SyncPlayGroupLeftUpdate(groupId, "")))
        assertNull(reduceGroup(joined(), SyncPlayNotInGroupUpdate(UUID(0, 0), "")))
    }

    @Test
    fun anUpdateForAnotherGroupIsIgnored() {
        val group = joined()
        assertEquals(group, reduceGroup(group, SyncPlayGroupLeftUpdate(UUID.randomUUID(), "")))
        assertNull(reduceGroup(null, queued()))
    }

    @Test
    fun aCommandForTheQueuedEntryIsKeptWithServerTimes() {
        val group = reduceGroup(joined(), queued())
        assertEquals(
            SyncCommand(SendCommandType.UNPAUSE, now.plusSeconds(1).toEpochMilli(), 3_000L),
            acceptCommand(group, command(entryId), now.toEpochMilli())
        )
    }

    @Test
    fun theZeroEntryStaleEntryOtherGroupAndNoQueueAreDropped() {
        val group = reduceGroup(joined(), queued())
        assertNull(acceptCommand(group, command(UUID(0, 0), SendCommandType.STOP), now.toEpochMilli()))
        assertNull(acceptCommand(group, command(UUID.randomUUID()), now.toEpochMilli()))
        assertNull(acceptCommand(group, command(entryId, group = UUID.randomUUID()), now.toEpochMilli()))
        assertNull(acceptCommand(joined(), command(entryId), now.toEpochMilli()))
    }

    @Test
    fun groupInfoRefreshesStateAndMembersOfTheJoinedGroupOnly() {
        val group = joined()
        val info = GroupInfoDto(groupId, "Movie night", GroupStateType.PAUSED, listOf("knuckles", "dale"), local(now))
        val refreshed = applyGroupInfo(group, info)!!
        assertEquals(GroupStateType.PAUSED, refreshed.state)
        assertEquals(listOf("knuckles", "dale"), refreshed.participants)
        assertEquals(group, applyGroupInfo(group, info.copy(groupId = UUID.randomUUID())))
        assertNull(applyGroupInfo(null, info))
    }

    @Test
    fun errorRepliesWithAnEmptyGroupIdEndTheGroup() {
        assertNull(reduceGroup(joined(), SyncPlayGroupDoesNotExistUpdate(UUID(0, 0), "")))
        assertNull(reduceGroup(joined(), SyncPlayLibraryAccessDeniedUpdate(UUID(0, 0), "")))
    }
}
