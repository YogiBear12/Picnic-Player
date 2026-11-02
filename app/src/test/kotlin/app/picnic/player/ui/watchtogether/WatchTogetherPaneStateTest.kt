package app.picnic.player.ui.watchtogether

import app.picnic.player.ui.watchtogether.WatchTogetherPaneViewModel.UiState
import java.time.LocalDateTime
import java.util.UUID
import org.jellyfin.sdk.model.api.GroupInfoDto
import org.jellyfin.sdk.model.api.GroupStateType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchTogetherPaneStateTest {
    private fun room(name: String) = GroupInfoDto(UUID.randomUUID(), name, GroupStateType.IDLE, listOf("dale"), LocalDateTime.now())

    private val a = room("A")
    private val b = room("B")
    private val loaded = UiState(loading = false, rooms = listOf(a, b))

    @Test
    fun pollReplacesListAndClearsInitialLoadError() {
        assertEquals(listOf(b), loaded.withRooms(listOf(b)).rooms)
        val failed = UiState(loading = false, error = "Couldn't load watch parties")
        val recovered = failed.withRooms(listOf(a))
        assertNull(recovered.error)
        assertEquals(listOf(a), recovered.rooms)
    }

    @Test
    fun pollNeverReplacesAJoinInFlightOrAFailedJoin() {
        val joining = loaded.copy(joining = a.groupId)
        val failedJoin = loaded.copy(failedJoin = a, error = "Couldn't join A")
        assertFalse(joining.pollable)
        assertFalse(failedJoin.pollable)
        assertEquals(joining, joining.withRooms(emptyList()))
        assertEquals(failedJoin, failedJoin.withRooms(emptyList()))
    }

    @Test
    fun failedPollKeepsWhatIsShownButAWaitedFetchShowsTheError() {
        assertEquals(loaded, loaded.withLoadFailure())
        val empty = UiState(loading = false)
        assertEquals(empty, empty.withLoadFailure())
        assertEquals("Couldn't load watch parties", UiState().withLoadFailure().error)
    }

    @Test
    fun swapDropsFocusOnlyWhenTheFocusedNodeLeaves() {
        assertFalse(loaded.dropsFocus(loaded.copy(rooms = listOf(b, a)), a.groupId))
        assertTrue(loaded.dropsFocus(loaded.copy(rooms = listOf(b)), a.groupId))
        assertTrue(loaded.dropsFocus(loaded.copy(rooms = emptyList()), a.groupId))
        val empty = UiState(loading = false)
        assertTrue(empty.dropsFocus(loaded, null))
        assertFalse(empty.dropsFocus(empty.copy(), null))
        assertTrue(empty.dropsFocus(empty.copy(loading = true), null))
    }
}
