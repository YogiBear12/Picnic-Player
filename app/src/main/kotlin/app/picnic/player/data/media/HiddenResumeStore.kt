package app.picnic.player.data.media

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.auth.UserScope
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class HiddenResumeStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val json: Json
) {
    val hidden: Flow<Map<String, Long>> = dataStore.data
        .map { prefs -> scopedKey(prefs)?.let { prefs[it] }?.let(::decode).orEmpty() }
        .distinctUntilChanged()

    suspend fun snapshot(): Map<String, Long> = hidden.first()

    suspend fun hide(itemId: UUID, positionTicks: Long) {
        dataStore.edit { prefs ->
            val key = scopedKey(prefs) ?: return@edit
            val updated = prefs[key]?.let(::decode).orEmpty() + (itemId.toString() to positionTicks)
            prefs[key] = json.encodeToString(updated)
        }
    }

    private fun decode(raw: String): Map<String, Long> = runCatching {
        json.decodeFromString<Map<String, Long>>(raw)
    }.getOrDefault(emptyMap())

    private fun scopedKey(prefs: Preferences): Preferences.Key<String>? {
        val scope = UserScope.decode(prefs[stringPreferencesKey(UserScope.ACTIVE_SESSION)]) ?: return null
        return stringPreferencesKey(scope.key(KEY_NAME))
    }

    private companion object {
        const val KEY_NAME = "home.hiddenResume"
    }
}
