package app.picnic.player.data.device

import android.content.Context
import android.os.Build
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable, unique-per-install Jellyfin DeviceId (carries over the
 * session-staleness fix). Jellyfin binds an access token to its device, so the
 * id must be persistent across launches and unique per install. Stored in a
 * small SharedPreferences so it can be read synchronously when the SDK is built.
 */
@Singleton
class DeviceIdentityStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("picnic.device", Context.MODE_PRIVATE)

    val deviceId: String
        get() = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit { putString(KEY_ID, it) }
        }

    /** Human-readable device name shown in Jellyfin's Devices list. */
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()
        .ifBlank { "Picnic TV" }

    private companion object {
        const val KEY_ID = "device_id"
    }
}
