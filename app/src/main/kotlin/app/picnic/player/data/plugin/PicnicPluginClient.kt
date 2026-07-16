package app.picnic.player.data.plugin

import app.picnic.player.BuildConfig
import app.picnic.player.data.auth.UserSession
import app.picnic.player.data.device.DeviceIdentityStore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Reads the Picnic companion plugin's discovery doc (`GET /picnic/info`) from the
 * Jellyfin server, authenticated with the active session token. Returns null when
 * the plugin isn't installed or anything fails — callers treat absence as "no
 * plugin features", so a plain Jellyfin server behaves exactly as before.
 */
@Singleton
class PicnicPluginClient @Inject constructor(
    private val json: Json,
    private val deviceIdentity: DeviceIdentityStore
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** Blocking network + deserialize — call off the main thread. */
    fun info(session: UserSession): PicnicInfo? = runCatching {
        val base = session.server.baseUrl.trimEnd('/')
        val request = Request.Builder()
            .url("$base/picnic/info")
            .header("Authorization", authHeader(session.accessToken))
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                null
            } else {
                val raw = response.body?.string().orEmpty()
                if (raw.isBlank()) null else json.decodeFromString<PicnicInfo>(raw)
            }
        }
    }.getOrNull()

    /** Jellyfin `MediaBrowser` scheme header; the token resolves the session server-side. */
    private fun authHeader(token: String): String {
        val device = deviceIdentity.deviceName.replace("\"", "")
        return "MediaBrowser Client=\"Picnic Player\", Device=\"$device\", " +
            "DeviceId=\"${deviceIdentity.deviceId}\", Version=\"${BuildConfig.VERSION_NAME}\", " +
            "Token=\"$token\""
    }
}
