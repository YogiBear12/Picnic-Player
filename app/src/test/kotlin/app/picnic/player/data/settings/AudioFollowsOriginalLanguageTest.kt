package app.picnic.player.data.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFollowsOriginalLanguageTest {

    @Test
    fun noAudioLanguageChosen_followsOriginal() {
        assertTrue(PlaybackSettings().audioFollowsOriginalLanguage)
        assertTrue(PlaybackSettings(preferredAudioLanguage = "").audioFollowsOriginalLanguage)
    }

    @Test
    fun explicitLanguage_doesNotFollowOriginal() {
        assertFalse(PlaybackSettings(preferredAudioLanguage = "en").audioFollowsOriginalLanguage)
    }

    @Test
    fun originalRowChosen_followsOriginalEvenWithAStoredLanguage() {
        assertTrue(PlaybackSettings(preferredAudioLanguage = "en", preferDefaultAudioTrack = true).audioFollowsOriginalLanguage)
    }
}
