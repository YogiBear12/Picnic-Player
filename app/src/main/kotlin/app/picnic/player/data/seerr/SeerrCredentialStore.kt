package app.picnic.player.data.seerr

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.picnic.player.data.security.SecureStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** Provenance of a stored Seerr URL: typed by the user, or prefilled from the companion plugin. */
enum class SeerrUrlSource { USER, PLUGIN }

sealed interface SeerrCredentials {
    val password: String

    data class Jellyfin(override val password: String) : SeerrCredentials

    data class Local(val email: String, override val password: String) : SeerrCredentials
}

data class SeerrLastLogin(val method: SeerrAuthMethod = SeerrAuthMethod.JELLYFIN, val email: String? = null)

/** A missing method is a link made before local login existed, which was always Jellyfin. */
internal fun parseSeerrAuthMethod(raw: String?): SeerrAuthMethod = SeerrAuthMethod.entries.find { it.name == raw } ?: SeerrAuthMethod.JELLYFIN

internal fun seerrCredentials(last: SeerrLastLogin, password: String?): SeerrCredentials? {
    if (password.isNullOrBlank()) return null
    return when (last.method) {
        SeerrAuthMethod.JELLYFIN, SeerrAuthMethod.JELLYFIN_TOKEN -> SeerrCredentials.Jellyfin(password)
        SeerrAuthMethod.LOCAL -> last.email?.takeIf { it.isNotBlank() }?.let { SeerrCredentials.Local(it, password) }
    }
}

/**
 * A Jellyfin link keeps its password at rest only until Seerr supports Jellyfin token auth
 * ([SeerrAuthMethod.JELLYFIN_TOKEN]). A local link always needs it for silent renew.
 */
@Singleton
class SeerrCredentialStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val secure: SecureStore
) {
    fun showDiscoverFlow(serverId: String, userId: String): Flow<Boolean> = dataStore.data.map { it[booleanPreferencesKey(showDiscoverKey(serverId, userId))] ?: true }

    suspend fun showDiscover(serverId: String, userId: String): Boolean = dataStore.data.map { it[booleanPreferencesKey(showDiscoverKey(serverId, userId))] }.first()
        ?: true

    suspend fun setShowDiscover(serverId: String, userId: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(showDiscoverKey(serverId, userId))] = value }
    }

    suspend fun serverUrl(serverId: String): String? = read(urlKey(serverId))

    suspend fun setServerUrl(serverId: String, url: String) {
        write(urlKey(serverId), normalizeBaseUrl(url))
    }

    /**
     * Provenance of the stored URL. Absent (legacy, or none stored) reads as [SeerrUrlSource.USER]
     * so an existing manually-set URL is never overwritten by plugin prefill.
     */
    suspend fun serverUrlSource(serverId: String): SeerrUrlSource = if (read(urlSourceKey(serverId)) == PLUGIN_SOURCE) SeerrUrlSource.PLUGIN else SeerrUrlSource.USER

    suspend fun setServerUrlSource(serverId: String, source: SeerrUrlSource) {
        write(urlSourceKey(serverId), if (source == SeerrUrlSource.PLUGIN) PLUGIN_SOURCE else USER_SOURCE)
    }

    suspend fun clearServerUrl(serverId: String) {
        remove(urlKey(serverId))
        remove(urlSourceKey(serverId))
    }

    suspend fun sessionCookie(serverId: String, userId: String): String? = read(cookieKey(serverId, userId))?.let { secure.decrypt(it) }

    suspend fun setSessionCookie(serverId: String, userId: String, cookie: String) {
        write(cookieKey(serverId, userId), secure.encrypt(cookie))
    }

    suspend fun clearSessionCookie(serverId: String, userId: String) = remove(cookieKey(serverId, userId))

    suspend fun credentials(serverId: String, userId: String): SeerrCredentials? {
        val prefs = dataStore.data.first()
        return seerrCredentials(
            last = lastLogin(prefs, serverId, userId),
            password = prefs[stringPreferencesKey(passwordKey(serverId, userId))]?.let { secure.decrypt(it) }
        )
    }

    suspend fun lastLogin(serverId: String, userId: String): SeerrLastLogin = lastLogin(dataStore.data.first(), serverId, userId)

    private fun lastLogin(prefs: Preferences, serverId: String, userId: String) = SeerrLastLogin(
        method = parseSeerrAuthMethod(prefs[stringPreferencesKey(authMethodKey(serverId, userId))]),
        email = prefs[stringPreferencesKey(emailKey(serverId, userId))]?.let { secure.decrypt(it) }
    )

    suspend fun setCredentials(serverId: String, userId: String, credentials: SeerrCredentials) {
        val password = secure.encrypt(credentials.password)
        val local = credentials as? SeerrCredentials.Local
        val email = local?.let { secure.encrypt(it.email) }
        val method = if (local != null) SeerrAuthMethod.LOCAL else SeerrAuthMethod.JELLYFIN
        dataStore.edit {
            it[stringPreferencesKey(passwordKey(serverId, userId))] = password
            it[stringPreferencesKey(authMethodKey(serverId, userId))] = method.name
            if (email != null) it[stringPreferencesKey(emailKey(serverId, userId))] = email
        }
    }

    suspend fun seerrUserId(serverId: String, userId: String): Int? = read(seerrUserKey(serverId, userId))?.toIntOrNull()

    suspend fun setSeerrUserId(serverId: String, userId: String, seerrUserId: Int) {
        write(seerrUserKey(serverId, userId), seerrUserId.toString())
    }

    suspend fun clearSeerrUserId(serverId: String, userId: String) = remove(seerrUserKey(serverId, userId))

    /** True when this User Session has a stored Seerr cookie or credentials for renew. */
    suspend fun hasLink(serverId: String, userId: String): Boolean = sessionCookie(serverId, userId) != null || credentials(serverId, userId) != null

    /** Keeps the auth method and email so the next Connect seeds focus and prefill from them. */
    suspend fun disconnectUser(serverId: String, userId: String) {
        clearSessionCookie(serverId, userId)
        remove(passwordKey(serverId, userId))
        clearSeerrUserId(serverId, userId)
    }

    /** Forget user: wipe all Seerr secrets for that profile. */
    suspend fun forgetUser(serverId: String, userId: String) {
        disconnectUser(serverId, userId)
        remove(authMethodKey(serverId, userId))
        remove(emailKey(serverId, userId))
        dataStore.edit { it.remove(booleanPreferencesKey(showDiscoverKey(serverId, userId))) }
    }

    /** Forget Jellyfin server: wipe URL and every linked user secret. */
    suspend fun forgetServer(serverId: String, userIds: List<String>) {
        clearServerUrl(serverId)
        userIds.forEach { forgetUser(serverId, it) }
    }

    private suspend fun read(key: String): String? = dataStore.data.map { it[stringPreferencesKey(key)] }.first()

    private suspend fun write(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    private suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    private fun urlKey(serverId: String) = "seerr.url.$serverId"
    private fun urlSourceKey(serverId: String) = "seerr.url_source.$serverId"
    private fun cookieKey(serverId: String, userId: String) = "seerr.cookie.$serverId.$userId"
    private fun passwordKey(serverId: String, userId: String) = "seerr.pw.$serverId.$userId"
    private fun authMethodKey(serverId: String, userId: String) = "seerr.auth_method.$serverId.$userId"
    private fun emailKey(serverId: String, userId: String) = "seerr.email.$serverId.$userId"
    private fun seerrUserKey(serverId: String, userId: String) = "seerr.uid.$serverId.$userId"
    private fun showDiscoverKey(serverId: String, userId: String) = "seerr.show_discover.$serverId.$userId"

    companion object {
        private const val PLUGIN_SOURCE = "plugin"
        private const val USER_SOURCE = "user"

        /** Strip trailing slash and optional `/api/v1` suffix; store user-facing base. */
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            url = url.trimEnd('/')
            if (url.endsWith("/api/v1", ignoreCase = true)) {
                url = url.dropLast("/api/v1".length).trimEnd('/')
            }
            return "$url/"
        }

        fun apiBaseUrl(storedBase: String): String {
            val base = storedBase.trimEnd('/')
            return "$base/api/v1"
        }
    }
}
