package app.picnic.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChromeTest {

    private val chrome = PlayerChrome()

    // --- what is on screen ------------------------------------------------------

    @Test
    fun startsClearOfTheVideo() {
        assertFalse(chrome.osdVisible)
        assertTrue(chrome.videoHasFocus)
    }

    @Test
    fun openingAPanelTakesFocusOffTheVideo() {
        chrome.openPanel(Panel.AUDIO)
        assertFalse(chrome.videoHasFocus)
        assertEquals(Panel.AUDIO, chrome.lastPanel)
    }

    @Test
    fun closingAPanelReopensTheOsd() {
        chrome.openPanel(Panel.SETTINGS)
        chrome.closePanel()
        assertEquals(Panel.NONE, chrome.panel)
        assertTrue(chrome.osdVisible)
    }

    // --- the skip pill ----------------------------------------------------------

    @Test
    fun pillShowsForASegmentEnteredWithTheVideoClear() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertTrue(chrome.skipPillShowing(segmentActive = true))
        assertFalse(chrome.skipInOsd(segmentActive = true))
    }

    @Test
    fun pillStaysDownForASegmentEnteredBySeeking() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = false)
        assertFalse(chrome.skipPillShowing(segmentActive = true))
        assertTrue(chrome.skipInOsd(segmentActive = true))
    }

    @Test
    fun segmentStartingUnderAnOpenOsdKeepsSkipInTheOsd() {
        chrome.reveal()
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertFalse(chrome.skipPillShowing(segmentActive = true))
        assertTrue(chrome.skipInOsd(segmentActive = true))
    }

    @Test
    fun openingTheOsdOverThePillHandsSkipToTheOsdForGood() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        chrome.reveal()
        chrome.hideOsd()
        assertFalse(chrome.skipPillShowing(segmentActive = true))
        assertTrue(chrome.skipInOsd(segmentActive = true))
    }

    @Test
    fun aNewSegmentGetsAFreshPill() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        chrome.dismissSkipPill()
        chrome.onSegmentChanged(segmentActive = false, enteredAtStart = false)
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertTrue(chrome.skipPillShowing(segmentActive = true))
    }

    // --- back ordering ----------------------------------------------------------

    @Test
    fun backClosesThePanelBeforeTheOsd() {
        chrome.reveal()
        chrome.openPanel(Panel.AUDIO)
        assertEquals(BackOutcome.Handled, chrome.onBack(segmentActive = false))
        assertEquals(Panel.NONE, chrome.panel)
        assertTrue(chrome.osdVisible)
    }

    @Test
    fun backThenClosesTheOsd() {
        chrome.reveal()
        assertEquals(BackOutcome.ClosedOsd, chrome.onBack(segmentActive = false))
        assertFalse(chrome.osdVisible)
    }

    @Test
    fun backDismissesThePillRatherThanLeaving() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertEquals(BackOutcome.Handled, chrome.onBack(segmentActive = true))
        assertFalse(chrome.skipPillShowing(segmentActive = true))
        // A second Back now leaves.
        assertEquals(BackOutcome.ExitPlayer, chrome.onBack(segmentActive = true))
    }

    @Test
    fun backLeavesWhenNothingIsOpen() {
        assertEquals(BackOutcome.ExitPlayer, chrome.onBack(segmentActive = false))
    }

    @Test
    fun backReturnsSubtitleAdjustToTheSettingsPanel() {
        chrome.enterSubtitleAdjust()
        assertFalse(chrome.osdVisible)
        assertEquals(BackOutcome.Handled, chrome.onBack(segmentActive = false))
        assertEquals(Panel.SETTINGS, chrome.panel)
        assertFalse(chrome.subtitleAdjust)
    }

    @Test
    fun nextUpOwnsBackAndClearsWhatWasOpen() {
        chrome.reveal()
        chrome.openPanel(Panel.CHAPTERS)
        chrome.onNextUpVisibleChanged(true)
        assertFalse(chrome.osdVisible)
        assertEquals(Panel.NONE, chrome.panel)
        assertEquals(BackOutcome.NextUp, chrome.onBack(segmentActive = true))
    }

    // --- picture-in-picture -----------------------------------------------------

    @Test
    fun nothingOpensOverAPipWindow() {
        chrome.reveal()
        chrome.onPipModeChanged(true)
        assertFalse(chrome.osdVisible)
        chrome.reveal()
        assertFalse(chrome.osdVisible)
        chrome.onPipModeChanged(false)
        chrome.reveal()
        assertTrue(chrome.osdVisible)
    }

    // --- quick-skip burst -------------------------------------------------------

    @Test
    fun quickSkipAccumulatesAndClears() {
        chrome.onQuickSkip(-10_000)
        chrome.onQuickSkip(-10_000)
        assertEquals(-20_000L, chrome.quickSkipMs)
        assertTrue(chrome.quickSkipVisible)
        chrome.endQuickSkip()
        assertEquals(0L, chrome.quickSkipMs)
        assertFalse(chrome.quickSkipVisible)
    }

    @Test
    fun openingTheOsdEndsTheBurst() {
        chrome.onQuickSkip(10_000)
        chrome.reveal()
        assertFalse(chrome.quickSkipVisible)
        assertEquals(0L, chrome.quickSkipMs)
    }
}
