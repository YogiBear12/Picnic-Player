package app.picnic.player.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerChromeTest {

    private val chrome = PlayerChrome()

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

    @Test
    fun pillShowsForASegmentEnteredWithTheVideoClear() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertTrue(chrome.skipPillShowing)
        assertFalse(chrome.skipInOsd)
    }

    @Test
    fun pillStaysDownForASegmentEnteredBySeeking() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = false)
        assertFalse(chrome.skipPillShowing)
        assertTrue(chrome.skipInOsd)
    }

    @Test
    fun segmentStartingUnderAnOpenOsdKeepsSkipInTheOsd() {
        chrome.reveal()
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertFalse(chrome.skipPillShowing)
        assertTrue(chrome.skipInOsd)
    }

    @Test
    fun openingTheOsdOverThePillHandsSkipToTheOsdForGood() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        chrome.reveal()
        chrome.hideOsd()
        assertFalse(chrome.skipPillShowing)
        assertTrue(chrome.skipInOsd)
    }

    @Test
    fun aNewSegmentGetsAFreshPill() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        chrome.dismissSkipPill()
        chrome.onSegmentChanged(segmentActive = false, enteredAtStart = false)
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertTrue(chrome.skipPillShowing)
    }

    @Test
    fun theVideoIsNotFocusedWhileThePillIsUp() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertFalse(chrome.videoHasFocus)
        chrome.dismissSkipPill()
        assertTrue(chrome.videoHasFocus)
    }

    @Test
    fun transportKeysKeepWorkingUnderThePill() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertFalse(chrome.videoHasFocus)
        assertTrue(chrome.videoKeysActive)
        chrome.reveal()
        assertFalse(chrome.videoKeysActive)
    }

    @Test
    fun backClosesThePanelBeforeTheOsd() {
        chrome.reveal()
        chrome.openPanel(Panel.AUDIO)
        assertEquals(BackOutcome.Handled, chrome.onBack())
        assertEquals(Panel.NONE, chrome.panel)
        assertTrue(chrome.osdVisible)
    }

    @Test
    fun backThenClosesTheOsd() {
        chrome.reveal()
        assertEquals(BackOutcome.ClosedOsd, chrome.onBack())
        assertFalse(chrome.osdVisible)
    }

    @Test
    fun backDismissesThePillRatherThanLeaving() {
        chrome.onSegmentChanged(segmentActive = true, enteredAtStart = true)
        assertEquals(BackOutcome.Handled, chrome.onBack())
        assertFalse(chrome.skipPillShowing)
        assertEquals(BackOutcome.ExitPlayer, chrome.onBack())
    }

    @Test
    fun backLeavesWhenNothingIsOpen() {
        assertEquals(BackOutcome.ExitPlayer, chrome.onBack())
    }

    @Test
    fun backReturnsSubtitleAdjustToTheSettingsPanel() {
        chrome.enterModal(PlayerModal.SUBTITLE_ADJUST)
        assertFalse(chrome.osdVisible)
        assertEquals(BackOutcome.Handled, chrome.onBack())
        assertEquals(Panel.SETTINGS, chrome.panel)
        assertNull(chrome.modal)
        assertTrue(chrome.returningFromSubtitleAdjust)
    }

    @Test
    fun backReturnsTheReportPanelToSettingsButFinishingDoesNot() {
        chrome.enterModal(PlayerModal.REPORT_ISSUE)
        assertEquals(BackOutcome.Handled, chrome.onBack())
        assertEquals(Panel.SETTINGS, chrome.panel)
        assertFalse(chrome.returningFromSubtitleAdjust)

        chrome.closePanel()
        chrome.enterModal(PlayerModal.REPORT_ISSUE)
        chrome.closeModal()
        assertNull(chrome.modal)
        assertEquals(Panel.NONE, chrome.panel)
    }

    @Test
    fun nextUpOwnsBackAndClearsWhatWasOpen() {
        chrome.reveal()
        chrome.openPanel(Panel.CHAPTERS)
        chrome.onNextUpVisibleChanged(true)
        assertFalse(chrome.osdVisible)
        assertEquals(Panel.NONE, chrome.panel)
        assertEquals(BackOutcome.NextUp, chrome.onBack())
    }

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

    @Test
    fun quickSkipAccumulatesAndRestarts() {
        chrome.onQuickSkip(-10_000)
        chrome.onQuickSkip(-10_000)
        assertEquals(-20_000L, chrome.quickSkipMs)
        assertTrue(chrome.quickSkipVisible)
        chrome.endQuickSkip()
        assertEquals(-20_000L, chrome.quickSkipMs)
        assertFalse(chrome.quickSkipVisible)
        chrome.onQuickSkip(-10_000)
        assertEquals(-10_000L, chrome.quickSkipMs)
    }

    @Test
    fun openingTheOsdEndsTheBurst() {
        chrome.onQuickSkip(10_000)
        chrome.reveal()
        assertFalse(chrome.quickSkipVisible)
        assertEquals(10_000L, chrome.quickSkipMs)
    }
}
