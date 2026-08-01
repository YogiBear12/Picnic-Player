package app.picnic.player.data.settings

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.auth.STORED_SESSIONS
import app.picnic.player.data.auth.StoredSession
import app.picnic.player.data.auth.UserScope
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsKeyMigrationTest {

    private val migration = settingsKeyMigration()

    private fun migrate(vararg entries: Preferences.Pair<*>): Preferences = runBlocking {
        migration.migrate(mutablePreferencesOf(*entries))
    }

    private fun sessions(vararg stored: StoredSession): Preferences.Pair<String> = stringPreferencesKey(STORED_SESSIONS) to Json.encodeToString(stored.toList())

    private val profileA = StoredSession(serverId = "s1", userId = "u1", username = "Profile A")
    private val profileB = StoredSession(serverId = "s1", userId = "u2", username = "Profile B")

    @Test
    fun `renames app-wide keys and drops the originals`() {
        val result = migrate(
            booleanPreferencesKey("ui.ambientBackgrounds") to false,
            booleanPreferencesKey("account.autoLoginLastUser") to false,
            stringPreferencesKey("ui.themeMusicVolume") to "LOUD",
            booleanPreferencesKey("playback.matchRefreshRate") to true,
            stringPreferencesKey("playback.trailerYouTubePackage") to "com.example.tube"
        )

        assertEquals(false, result[booleanPreferencesKey("experience.ambientBackgrounds")])
        assertEquals(false, result[booleanPreferencesKey("experience.autoLoginLastUser")])
        assertEquals("LOUD", result[stringPreferencesKey("experience.themeMusicVolume")])
        assertEquals(true, result[booleanPreferencesKey("advanced.matchRefreshRate")])
        assertEquals("com.example.tube", result[stringPreferencesKey("advanced.trailerYouTubePackage")])

        assertNull(result[booleanPreferencesKey("ui.ambientBackgrounds")])
        assertNull(result[booleanPreferencesKey("account.autoLoginLastUser")])
        assertNull(result[stringPreferencesKey("ui.themeMusicVolume")])
        assertNull(result[booleanPreferencesKey("playback.matchRefreshRate")])
        assertNull(result[stringPreferencesKey("playback.trailerYouTubePackage")])
    }

    @Test
    fun `moves picture-in-picture to the advanced namespace`() {
        val result = migrate(booleanPreferencesKey("playback.pictureInPicture") to true)

        assertEquals(true, result[booleanPreferencesKey("advanced.pictureInPicture")])
        assertNull(result[booleanPreferencesKey("playback.pictureInPicture")])
    }

    @Test
    fun `copies per-user settings to every stored profile`() {
        val result = migrate(
            sessions(profileA, profileB),
            stringPreferencesKey("playback.preferredAudioLanguage") to "jpn",
            booleanPreferencesKey("playback.alwaysDisplaySubtitles") to true,
            stringPreferencesKey("playback.defaultVideoQuality") to "Q_1080P"
        )

        listOf(profileA, profileB).forEach { session ->
            val scope = UserScope(session.serverId, session.userId)
            assertEquals("jpn", result[stringPreferencesKey(scope.key("playback.preferredAudioLanguage"))])
            assertEquals(true, result[booleanPreferencesKey(scope.key("playback.alwaysDisplaySubtitles"))])
            assertEquals("Q_1080P", result[stringPreferencesKey(scope.key("playback.defaultVideoQuality"))])
        }

        // The unscoped originals are gone — nothing reads them after the migration.
        assertNull(result[stringPreferencesKey("playback.preferredAudioLanguage")])
        assertNull(result[booleanPreferencesKey("playback.alwaysDisplaySubtitles")])
        assertNull(result[stringPreferencesKey("playback.defaultVideoQuality")])
    }

    @Test
    fun `gives the subtitle background keys the names they describe`() {
        val result = migrate(
            sessions(profileA),
            // The fill was stored under the "…Style" name and the style under "…Shape".
            stringPreferencesKey("playback.subtitleBackgroundStyle") to "SOLID",
            stringPreferencesKey("playback.subtitleBackgroundShape") to "WRAPPED"
        )

        val scope = UserScope(profileA.serverId, profileA.userId)
        assertEquals("SOLID", result[stringPreferencesKey(scope.key("playback.subtitleBackgroundFill"))])
        assertEquals("WRAPPED", result[stringPreferencesKey(scope.key("playback.subtitleBackgroundStyle"))])
    }

    @Test
    fun `drops the dead click-to-pause key`() {
        val result = migrate(booleanPreferencesKey("playback.clickToPause") to false)

        assertNull(result[booleanPreferencesKey("playback.clickToPause")])
    }

    @Test
    fun `leaves app-wide playback settings alone`() {
        val result = migrate(
            intPreferencesKey("playback.skipForwardSeconds") to 45,
            stringPreferencesKey("playback.introAction") to "DO_NOT_SKIP"
        )

        assertEquals(45, result[intPreferencesKey("playback.skipForwardSeconds")])
        assertEquals("DO_NOT_SKIP", result[stringPreferencesKey("playback.introAction")])
    }

    @Test
    fun `runs once`() = runBlocking {
        assertTrue(migration.shouldMigrate(mutablePreferencesOf()))
        assertFalse(migration.shouldMigrate(migrate()))
    }

    @Test
    fun `with no stored profiles there is nothing to copy`() {
        val result = migrate(stringPreferencesKey("playback.preferredAudioLanguage") to "jpn")

        assertNull(result[stringPreferencesKey("playback.preferredAudioLanguage")])
        assertTrue(result.asMap().keys.none { it.name.startsWith("user.") })
    }
}
