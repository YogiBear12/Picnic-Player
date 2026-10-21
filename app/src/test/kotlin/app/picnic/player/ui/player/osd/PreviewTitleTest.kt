package app.picnic.player.ui.player.osd

import app.picnic.player.data.playback.MediaSegment
import app.picnic.player.data.playback.SegmentKind
import app.picnic.player.ui.player.ChapterMark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviewTitleTest {

    @Test
    fun chapterWinsOverSegmentAtSeekTarget() {
        val chapters = listOf(
            ChapterMark(0, "Opening", 0, null),
            ChapterMark(1, "Main story", 10_000, null)
        )
        val segments = listOf(MediaSegment("intro", SegmentKind.INTRO, 0, 20_000))

        assertEquals("Opening", previewTitle(chapters, segments, 9_999))
        assertEquals("Main story", previewTitle(chapters, segments, 10_000))
    }

    @Test
    fun targetBeforeFirstChapterUsesFirstChapter() {
        val chapters = listOf(ChapterMark(0, "Opening", 5_000, null))

        assertEquals("Opening", previewTitle(chapters, emptyList(), 0))
    }

    @Test
    fun segmentNamesApplyWhenThereAreNoChapters() {
        val names = mapOf(
            SegmentKind.INTRO to "Intro",
            SegmentKind.RECAP to "Recap",
            SegmentKind.OUTRO to "Outro",
            SegmentKind.PREVIEW to "Preview",
            SegmentKind.COMMERCIAL to "Ad"
        )

        for ((kind, name) in names) {
            val segments = listOf(MediaSegment("segment", kind, 1_000, 10_000))
            assertEquals(name, previewTitle(emptyList(), segments, 1_000))
            assertEquals(name, previewTitle(emptyList(), segments, 5_000))
        }
    }

    @Test
    fun segmentEndIsExclusive() {
        val segments = listOf(MediaSegment("intro", SegmentKind.INTRO, 1_000, 10_000))

        assertEquals("Intro", previewTitle(emptyList(), segments, 9_999))
        assertNull(previewTitle(emptyList(), segments, 10_000))
    }

    @Test
    fun noTitleOutsideChaptersAndSegments() {
        val segments = listOf(MediaSegment("intro", SegmentKind.INTRO, 1_000, 10_000))

        assertNull(previewTitle(emptyList(), segments, 0))
        assertNull(previewTitle(emptyList(), segments, 20_000))
        assertNull(previewTitle(emptyList(), emptyList(), 5_000))
    }
}
