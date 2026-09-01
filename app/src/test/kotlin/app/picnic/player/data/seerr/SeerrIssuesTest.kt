package app.picnic.player.data.seerr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeerrIssuesTest {
    @Test
    fun `issue type ids match seerr`() {
        assertEquals(1, SeerrIssueType.VIDEO.id)
        assertEquals(2, SeerrIssueType.AUDIO.id)
        assertEquals(3, SeerrIssueType.SUBTITLE.id)
        assertEquals(4, SeerrIssueType.OTHER.id)
    }

    @Test
    fun `every type but other offers preset reasons`() {
        SeerrIssueType.entries.filter { it != SeerrIssueType.OTHER }.forEach {
            assertTrue(it.name, seerrIssueReasons(it, SeerrIssueSubject.MOVIE).isNotEmpty())
        }
        assertTrue(seerrIssueReasons(SeerrIssueType.OTHER, SeerrIssueSubject.MOVIE).isEmpty())
    }

    @Test
    fun `wrong-item reason names the subject`() {
        assertEquals(
            listOf("Wrong movie", "Wrong show", "Wrong episode"),
            SeerrIssueSubject.entries.map { seerrIssueReasons(SeerrIssueType.VIDEO, it).first() }
        )
    }

    @Test
    fun `message falls back to the type label`() {
        assertEquals("Other", seerrIssueMessage(SeerrIssueType.OTHER, null))
        assertEquals("Other", seerrIssueMessage(SeerrIssueType.OTHER, " "))
        assertEquals("Low quality", seerrIssueMessage(SeerrIssueType.VIDEO, "Low quality"))
    }

    @Test
    fun `season list drops specials, empty seasons and duplicate episodes`() {
        val episodes = mapOf(
            0 to listOf(1, 2, 3),
            2 to listOf(3, 1, 2),
            1 to listOf(2, 1, 1, 0),
            3 to emptyList()
        )
        assertEquals(
            listOf(
                SeerrIssueSeason(1, listOf(1, 2)),
                SeerrIssueSeason(2, listOf(1, 2, 3))
            ),
            seerrIssueSeasons(episodes)
        )
    }
}
