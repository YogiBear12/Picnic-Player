package app.picnic.player.playback

import app.picnic.player.data.settings.SubtitleArea
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleAnchoringTest {
    private val scopeRect = 16f / 9f / 2.39f

    @Test
    fun fullFrameVideo_fillsTheContainerHeight() {
        assertEquals(1f, videoRectHeightFraction(1920f, 1080f, 1920f, 1080f), 0.001f)
    }

    @Test
    fun letterboxedVideo_leavesBarsAboveAndBelow() {
        assertEquals(scopeRect, videoRectHeightFraction(2390f, 1000f, 1920f, 1080f), 0.001f)
    }

    @Test
    fun pillarboxedVideo_stillFillsTheHeight() {
        assertEquals(1f, videoRectHeightFraction(1440f, 1080f, 1920f, 1080f), 0.001f)
    }

    @Test
    fun unknownVideoSize_readsAsFullFrame() {
        assertEquals(1f, videoRectHeightFraction(0f, 0f, 1920f, 1080f), 0.001f)
    }

    @Test
    fun screenArea_ignoresTheVideoRect() {
        assertEquals(
            0.08f,
            subtitleBottomPaddingFraction(SubtitleArea.SCREEN, 8, scopeRect, BlackBars.None),
            0.001f
        )
    }

    @Test
    fun imageArea_measuresFromTheVideoRect() {
        val letterbox = (1f - scopeRect) / 2f
        assertEquals(
            letterbox + 0.08f * scopeRect,
            subtitleBottomPaddingFraction(SubtitleArea.IMAGE, 8, scopeRect, BlackBars.None),
            0.001f
        )
    }

    @Test
    fun imageArea_ignoresMeasuredBars() {
        assertEquals(
            subtitleBottomPaddingFraction(SubtitleArea.IMAGE, 8, 1f, BlackBars.None),
            subtitleBottomPaddingFraction(SubtitleArea.IMAGE, 8, 1f, BlackBars(0.128f, 0.128f)),
            0.001f
        )
    }

    @Test
    fun automaticArea_clearsBarsBakedIntoAFullFrame() {
        val bars = BlackBars(0.128f, 0.128f)
        assertEquals(
            0.128f + 0.08f * (1f - 0.256f),
            subtitleBottomPaddingFraction(SubtitleArea.AUTOMATIC, 8, 1f, bars),
            0.001f
        )
    }

    @Test
    fun automaticArea_withoutBars_matchesImage() {
        assertEquals(
            subtitleBottomPaddingFraction(SubtitleArea.IMAGE, 8, scopeRect, BlackBars.None),
            subtitleBottomPaddingFraction(SubtitleArea.AUTOMATIC, 8, scopeRect, BlackBars.None),
            0.001f
        )
    }

    @Test
    fun bakedInAndPlayerAddedBars_landTheCueInTheSamePlace() {
        val baked = subtitleBottomPaddingFraction(
            SubtitleArea.AUTOMATIC,
            8,
            1f,
            BlackBars((1f - scopeRect) / 2f, (1f - scopeRect) / 2f)
        )
        val fitted = subtitleBottomPaddingFraction(SubtitleArea.AUTOMATIC, 8, scopeRect, BlackBars.None)
        assertEquals(fitted, baked, 0.001f)
    }

    @Test
    fun zeroInset_sitsOnTheBottomEdgeOfTheArea() {
        assertEquals(
            0f,
            subtitleBottomPaddingFraction(SubtitleArea.SCREEN, 0, scopeRect, BlackBars.None),
            0.001f
        )
        assertEquals(
            (1f - scopeRect) / 2f,
            subtitleBottomPaddingFraction(SubtitleArea.IMAGE, 0, scopeRect, BlackBars.None),
            0.001f
        )
    }

    @Test
    fun topAnchoredCue_landsInsideTheImage() {
        val letterbox = (1f - scopeRect) / 2f
        assertEquals(
            letterbox + 0.08f * scopeRect,
            subtitleAnchoredLine(
                SubtitleLineAnchor.Top(0.08f),
                SubtitleArea.IMAGE,
                8,
                scopeRect,
                BlackBars.None
            ),
            0.001f
        )
    }

    @Test
    fun topAnchoredCue_clearsBarsBakedIntoAFullFrame() {
        val bars = BlackBars(0.128f, 0.128f)
        assertEquals(
            0.128f + 0.08f * (1f - 0.256f),
            subtitleAnchoredLine(
                SubtitleLineAnchor.Top(0.08f),
                SubtitleArea.AUTOMATIC,
                8,
                1f,
                bars
            ),
            0.001f
        )
    }

    @Test
    fun topAnchoredCue_onScreenAreaKeepsTheScreenEdge() {
        assertEquals(
            0.08f,
            subtitleAnchoredLine(
                SubtitleLineAnchor.Top(0.08f),
                SubtitleArea.SCREEN,
                8,
                scopeRect,
                BlackBars.None
            ),
            0.001f
        )
    }

    @Test
    fun bottomAnchoredCue_mirrorsTheBottomPadding() {
        val bars = BlackBars(0.128f, 0.128f)
        assertEquals(
            1f - subtitleBottomPaddingFraction(SubtitleArea.AUTOMATIC, 8, 1f, bars),
            subtitleAnchoredLine(SubtitleLineAnchor.Bottom(0.92f), SubtitleArea.AUTOMATIC, 8, 1f, bars),
            0.001f
        )
    }

    @Test
    fun middleAnchoredCue_mapsIntoTheImage() {
        val letterbox = (1f - scopeRect) / 2f
        assertEquals(
            letterbox + 0.5f * scopeRect,
            subtitleAnchoredLine(
                SubtitleLineAnchor.Middle(0.5f),
                SubtitleArea.IMAGE,
                8,
                scopeRect,
                BlackBars.None
            ),
            0.001f
        )
    }

    @Test
    fun zeroInset_putsTheTopCueOnTheImageEdge() {
        val letterbox = (1f - scopeRect) / 2f
        assertEquals(
            letterbox,
            subtitleAnchoredLine(
                SubtitleLineAnchor.Top(0.08f),
                SubtitleArea.IMAGE,
                0,
                scopeRect,
                BlackBars.None
            ),
            0.001f
        )
    }

    @Test
    fun authoredCue_keepsItsOwnPositionInsideTheImage() {
        val letterbox = (1f - scopeRect) / 2f
        assertEquals(
            letterbox + 0.35f * scopeRect,
            subtitleAnchoredLine(
                SubtitleLineAnchor.Top(0.35f),
                SubtitleArea.IMAGE,
                8,
                scopeRect,
                BlackBars.None
            ),
            0.001f
        )
    }

    @Test
    fun authoredCue_isHeldOutOfTheBars() {
        val letterbox = (1f - scopeRect) / 2f
        assertEquals(
            letterbox + 0.08f * scopeRect,
            subtitleAnchoredLine(
                SubtitleLineAnchor.Top(0.01f),
                SubtitleArea.IMAGE,
                8,
                scopeRect,
                BlackBars.None
            ),
            0.001f
        )
    }
}
