package app.picnic.player.data.settings

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.auth.STORED_SESSIONS
import app.picnic.player.data.auth.StoredSession
import app.picnic.player.data.auth.UserScope
import kotlinx.serialization.json.Json

internal fun settingsKeyMigration(): DataMigration<Preferences> = object : DataMigration<Preferences> {
    override suspend fun shouldMigrate(currentData: Preferences): Boolean = (currentData[VERSION] ?: 0) < CURRENT_VERSION

    override suspend fun migrate(currentData: Preferences): Preferences {
        val prefs = currentData.toMutablePreferences()
        stepsToApply(MIGRATION_STEPS, currentData[VERSION] ?: 0)
            .forEach { step -> step.applyTo(prefs, currentData) }
        prefs[VERSION] = CURRENT_VERSION
        return prefs
    }

    override suspend fun cleanUp() = Unit
}

internal class MigrationStep(val version: Int, val applyTo: (MutablePreferences, Preferences) -> Unit)

internal fun stepsToApply(steps: List<MigrationStep>, alreadyApplied: Int): List<MigrationStep> = steps.filter { it.version > alreadyApplied }.sortedBy { it.version }

private val MIGRATION_STEPS = listOf(
    MigrationStep(version = 2) { prefs, currentData ->
        RENAMED_BOOLEANS.forEach { (old, new) ->
            currentData[booleanPreferencesKey(old)]?.let { value ->
                prefs[booleanPreferencesKey(new)] = value
                prefs -= booleanPreferencesKey(old)
            }
        }
        RENAMED_STRINGS.forEach { (old, new) ->
            currentData[stringPreferencesKey(old)]?.let { value ->
                prefs[stringPreferencesKey(new)] = value
                prefs -= stringPreferencesKey(old)
            }
        }

        val carried = USER_PREFS.mapNotNull { pref -> currentData.read(pref)?.let { pref to it } }
        storedSessions(currentData).forEach { session ->
            val scope = UserScope(session.serverId, session.userId)
            carried.forEach { (pref, value) -> prefs.write(scope.key(pref.newName), value) }
        }
        USER_PREFS.forEach { pref -> prefs.drop(pref.oldName, pref.isBoolean) }

        storedSessions(currentData).forEach { session ->
            prefs.foldSubtitleBackground(UserScope(session.serverId, session.userId))
        }

        prefs -= booleanPreferencesKey(CLICK_TO_PAUSE)
    }
)

private val CURRENT_VERSION = MIGRATION_STEPS.maxOf { it.version }

private fun storedSessions(prefs: Preferences): List<StoredSession> {
    val raw = prefs[stringPreferencesKey(STORED_SESSIONS)] ?: return emptyList()
    return runCatching { MIGRATION_JSON.decodeFromString<List<StoredSession>>(raw) }.getOrDefault(emptyList())
}

private fun Preferences.read(pref: UserPref): Any? = if (pref.isBoolean) {
    this[booleanPreferencesKey(pref.oldName)]
} else {
    this[stringPreferencesKey(pref.oldName)]
}

private fun MutablePreferences.write(name: String, value: Any) {
    when (value) {
        is Boolean -> this[booleanPreferencesKey(name)] = value
        is String -> this[stringPreferencesKey(name)] = value
    }
}

private fun MutablePreferences.foldSubtitleBackground(scope: UserScope) {
    val on = this[booleanPreferencesKey(scope.key(SUBTITLE_BACKGROUND))]
    val shape = this[stringPreferencesKey(scope.key(SUBTITLE_BACKGROUND_STYLE))]
    if (on == null && shape == null) return

    this -= booleanPreferencesKey(scope.key(SUBTITLE_BACKGROUND))
    this -= stringPreferencesKey(scope.key(SUBTITLE_BACKGROUND_STYLE))
    this[stringPreferencesKey(scope.key(SUBTITLE_BACKGROUND))] =
        if (on == true) shape ?: DEFAULT_BACKGROUND_SHAPE else BACKGROUND_OFF
}

private fun MutablePreferences.drop(name: String, isBoolean: Boolean) {
    this -= if (isBoolean) booleanPreferencesKey(name) else stringPreferencesKey(name)
}

private data class UserPref(val oldName: String, val newName: String, val isBoolean: Boolean = false)

private val MIGRATION_JSON = Json { ignoreUnknownKeys = true }

private val VERSION = intPreferencesKey("settings.migrationVersion")

private const val CLICK_TO_PAUSE = "playback.clickToPause"

private const val SUBTITLE_BACKGROUND = "playback.subtitleBackground"
private const val SUBTITLE_BACKGROUND_STYLE = "playback.subtitleBackgroundStyle"
private const val DEFAULT_BACKGROUND_SHAPE = "BOXED"
private const val BACKGROUND_OFF = "OFF"

private val RENAMED_BOOLEANS = listOf(
    "account.autoLoginLastUser" to "experience.autoLoginLastUser",
    "ui.colouredFocus" to "experience.colouredFocus",
    "ui.pulseFocusGlow" to "experience.pulseFocusGlow",
    "ui.ambientBackgrounds" to "experience.ambientBackgrounds",
    "ui.capBadgeCount" to "experience.capBadgeCount",
    "playback.pictureInPicture" to "advanced.pictureInPicture",
    "playback.matchRefreshRate" to "advanced.matchRefreshRate",
    "playback.matchResolution" to "advanced.matchResolution",
    "playback.downmixStereo" to "advanced.downmixStereo",
    "playback.forceDoviProfile7" to "advanced.forceDoviProfile7",
    "playback.forceDirectPlay" to "advanced.forceDirectPlay"
)

private val RENAMED_STRINGS = listOf(
    "ui.themeMusicVolume" to "experience.themeMusicVolume",
    "playback.trailerYouTubePackage" to "advanced.trailerYouTubePackage"
)

private val USER_PREFS = listOf(
    UserPref("playback.defaultVideoQuality", "playback.defaultVideoQuality"),
    UserPref("playback.preferredAudioLanguage", "playback.preferredAudioLanguage"),
    UserPref("playback.preferDefaultAudioTrack", "playback.preferDefaultAudioTrack", isBoolean = true),
    UserPref("playback.preferredSubtitleLanguage", "playback.preferredSubtitleLanguage"),
    UserPref("playback.alwaysDisplaySubtitles", "playback.alwaysDisplaySubtitles", isBoolean = true),
    UserPref("playback.subtitleSize", "playback.subtitleSize"),
    UserPref("playback.subtitleColour", "playback.subtitleColour"),
    UserPref("playback.subtitleBackground", "playback.subtitleBackground", isBoolean = true),
    UserPref("playback.subtitleBackgroundStyle", "playback.subtitleBackgroundFill"),
    UserPref("playback.subtitleBackgroundShape", "playback.subtitleBackgroundStyle")
)
