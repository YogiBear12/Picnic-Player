package app.picnic.player.data.seerr

import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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
            assertTrue(it.name, seerrIssueReasons(it, "movie").isNotEmpty())
        }
        assertTrue(seerrIssueReasons(SeerrIssueType.OTHER, "movie").isEmpty())
    }

    @Test
    fun `wrong-item reason names the subject`() {
        assertEquals(
            listOf("Wrong movie", "Wrong show", "Wrong episode"),
            listOf("movie", "show", "episode").map { seerrIssueReasons(SeerrIssueType.VIDEO, it).first() }
        )
    }

    @Test
    fun `message falls back to the type label`() {
        assertEquals("Other", seerrIssueMessage(SeerrIssueType.OTHER, null))
        assertEquals("Other", seerrIssueMessage(SeerrIssueType.OTHER, " "))
        assertEquals("Low quality", seerrIssueMessage(SeerrIssueType.VIDEO, "Low quality"))
    }

    @Test
    fun `extra detail is appended below the reason`() {
        assertEquals(
            "Low quality\n\nOnly on 4K",
            seerrIssueMessage(SeerrIssueType.VIDEO, "Low quality", "Only on 4K")
        )
        assertEquals(
            "Low quality",
            seerrIssueMessage(SeerrIssueType.VIDEO, "Low quality", "  ")
        )
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

    @Test
    fun `relative time counts minutes, hours and days`() {
        val now = Instant.parse("2026-09-02T12:00:00Z")
        val utc = ZoneOffset.UTC
        assertEquals("just now", seerrRelativeTime("2026-09-02T11:59:30Z", now, utc))
        assertEquals("1 minute ago", seerrRelativeTime("2026-09-02T11:59:00Z", now, utc))
        assertEquals("5 minutes ago", seerrRelativeTime("2026-09-02T11:55:00Z", now, utc))
        assertEquals("14 hours ago", seerrRelativeTime("2026-09-01T22:00:00Z", now, utc))
        assertEquals("3 days ago", seerrRelativeTime("2026-08-30T12:00:00Z", now, utc))
        assertEquals("1 Aug 2026", seerrRelativeTime("2026-08-01T12:00:00Z", now, utc))
        assertNull(seerrRelativeTime(null, now, utc))
        assertNull(seerrRelativeTime("not-a-date", now, utc))
    }

    @Test
    fun `type reads as a sentence`() {
        assertEquals("Subtitle issue", seerrIssueTypeSentence(SeerrIssueType.SUBTITLE.id))
        assertEquals("Other issue", seerrIssueTypeSentence(null))
    }

    @Test
    fun `avatar url joins relative paths onto the seerr base`() {
        assertEquals(
            "https://seerr.example.com/avatarproxy/abc",
            seerrAvatarUrl("https://seerr.example.com/", "/avatarproxy/abc")
        )
        assertEquals(
            "https://gravatar.com/avatar/x",
            seerrAvatarUrl("https://seerr.example.com", "https://gravatar.com/avatar/x")
        )
        assertEquals(null, seerrAvatarUrl("https://seerr.example.com", " "))
        assertEquals(null, seerrAvatarUrl(null, "/avatarproxy/abc"))
    }

    @Test
    fun `scope label names the season, the episode, or all seasons`() {
        val tv = SeerrMediaInfo(mediaType = "tv")
        assertEquals(
            "S2 E4",
            seerrIssueScopeLabel(SeerrIssue(id = 1, problemSeason = 2, problemEpisode = 4, media = tv))
        )
        assertEquals(
            "Season 2",
            seerrIssueScopeLabel(SeerrIssue(id = 1, problemSeason = 2, media = tv))
        )
        assertEquals("All seasons", seerrIssueScopeLabel(SeerrIssue(id = 1, media = tv)))
        assertEquals(
            null,
            seerrIssueScopeLabel(SeerrIssue(id = 1, media = SeerrMediaInfo(mediaType = "movie")))
        )
        assertNull(seerrIssueScopeLabel(SeerrIssue(id = 1)))
    }

    @Test
    fun `the first comment is the report body and the rest are replies`() {
        val issue = SeerrIssue(
            id = 1,
            comments = listOf(
                SeerrIssueComment(id = 10, message = "Audio is out of sync"),
                SeerrIssueComment(id = 11, message = "Still broken"),
                SeerrIssueComment(id = 12, message = "Fixed now")
            )
        )
        assertEquals("Audio is out of sync", issue.reportBody)
        assertEquals(listOf(11, 12), issue.replies.map { it.id })
        assertNull(SeerrIssue(id = 1).reportBody)
        assertEquals(emptyList<SeerrIssueComment>(), SeerrIssue(id = 1).replies)
        assertNull(SeerrIssue(id = 1, comments = listOf(SeerrIssueComment(message = " "))).reportBody)
    }

    @Test
    fun `comment author falls back through display name, username and email`() {
        assertEquals("Ada", seerrCommentAuthor(comment(displayName = "Ada", username = "ada99")))
        assertEquals("ada99", seerrCommentAuthor(comment(displayName = " ", username = "ada99")))
        assertEquals("ada", seerrCommentAuthor(comment(email = "ada@example.com")))
        assertEquals("Seerr", seerrCommentAuthor(SeerrIssueComment(id = 1)))
    }

    @Test
    fun `removing an issue focuses the next card, then the previous one`() {
        val rows = listOf(display(1), display(2), display(3))
        assertEquals(2, neighborIssueId(rows, 1))
        assertEquals(3, neighborIssueId(rows, 2))
        assertEquals(2, neighborIssueId(rows, 3))
        assertNull(neighborIssueId(rows, 99))
        assertNull(neighborIssueId(listOf(display(1)), 1))
        assertNull(neighborIssueId(emptyList(), 1))
    }

    @Test
    fun `an issue is open until it is resolved`() {
        assertTrue(SeerrIssue(id = 1).isOpen)
        assertTrue(SeerrIssue(id = 1, status = 1).isOpen)
        assertFalse(SeerrIssue(id = 1, status = SeerrIssueStatus.RESOLVED).isOpen)
    }

    @Test
    fun `reporting needs the create-issues permission, viewing accepts any issue permission`() {
        assertTrue(canCreateSeerrIssue(user(SeerrPermission.CREATE_ISSUES)))
        assertFalse(canCreateSeerrIssue(user(SeerrPermission.VIEW_ISSUES)))
        assertFalse(canCreateSeerrIssue(null))

        assertTrue(canViewSeerrIssues(user(SeerrPermission.VIEW_ISSUES)))
        assertTrue(canViewSeerrIssues(user(SeerrPermission.MANAGE_ISSUES)))
        assertTrue(canViewSeerrIssues(user(SeerrPermission.CREATE_ISSUES)))
        assertFalse(canViewSeerrIssues(user(SeerrPermission.REQUEST)))
        assertFalse(canViewSeerrIssues(null))
    }

    @Test
    fun `the title cache key needs both a tmdb id and a known media type`() {
        assertEquals(
            SeerrTitleCacheKey(SeerrMediaType.TV, 42),
            SeerrIssue(id = 1, media = SeerrMediaInfo(tmdbId = 42, mediaType = "TV")).titleCacheKeyOrNull()
        )
        assertNull(SeerrIssue(id = 1, media = SeerrMediaInfo(mediaType = "tv")).titleCacheKeyOrNull())
        assertNull(SeerrIssue(id = 1, media = SeerrMediaInfo(tmdbId = 42)).titleCacheKeyOrNull())
        assertNull(SeerrIssue(id = 1).titleCacheKeyOrNull())
    }

    private fun comment(
        displayName: String? = null,
        username: String? = null,
        email: String? = null
    ) = SeerrIssueComment(
        id = 1,
        user = SeerrUser(id = 1, displayName = displayName, username = username, email = email)
    )

    private fun user(permission: Long) = SeerrUser(id = 1, permissions = permission)

    private fun display(id: Int) = SeerrIssueDisplay(
        issue = SeerrIssue(id = id),
        title = "Title $id",
        posterPath = null
    )
}
