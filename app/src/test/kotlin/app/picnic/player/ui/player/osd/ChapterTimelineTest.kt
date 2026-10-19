package app.picnic.player.ui.player.osd

import app.picnic.player.ui.player.ChapterMark
import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterTimelineTest {

    private val chapters = listOf(
        ChapterMark(0, "Opening", 0, null),
        ChapterMark(1, "Practice", 10_000, null),
        ChapterMark(2, "Ending", 30_000, null)
    )

    @Test
    fun chapterStartsBecomeProportionalTrackBoundaries() {
        assertEquals(listOf(0f, 0.25f, 0.75f, 1f), chapterBoundaries(chapters, 40_000))
    }

    @Test
    fun duplicateAndInvalidStartsDoNotCreateEmptyOrReversedSegments() {
        val starts = listOf(30_000L, -1L, 10_000L, 10_000L, 0L, 40_000L, 50_000L)
        val metadata = starts.mapIndexed { index, start -> ChapterMark(index, "Chapter $index", start, null) }

        assertEquals(listOf(0f, 0.25f, 0.75f, 1f), chapterBoundaries(metadata, 40_000))
    }

    @Test
    fun unknownDurationAndMissingChaptersLeaveOneContinuousSegment() {
        assertEquals(listOf(0f, 1f), chapterBoundaries(chapters, 0))
        assertEquals(listOf(0f, 1f), chapterBoundaries(chapters, -1))
        assertEquals(listOf(0f, 1f), chapterBoundaries(emptyList(), 40_000))
    }

    @Test
    fun roundingNearTheEndDoesNotDuplicateTheFinalBoundary() {
        val duration = Long.MAX_VALUE
        val metadata = listOf(ChapterMark(0, "End", duration - 1, null))

        assertEquals(listOf(0f, 1f), chapterBoundaries(metadata, duration))
    }
}
