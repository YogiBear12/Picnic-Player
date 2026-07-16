package app.picnic.player.data.jellyfin

import android.content.Context
import android.net.wifi.WifiManager
import app.picnic.player.BuildConfig
import app.picnic.player.data.device.DeviceIdentityStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.discovery.DiscoveryService
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo

/**
 * Owns the single [org.jellyfin.sdk.Jellyfin] instance and mints [ApiClient]s.
 * Client + device info (incl. the persistent unique DeviceId) are
 * baked in once so every request is correctly identified to the server.
 */
@Singleton
class JellyfinFactory @Inject constructor(
    @ApplicationContext private val appContext: Context,
    deviceIdentity: DeviceIdentityStore
) {
    private val jellyfin = createJellyfin {
        clientInfo = ClientInfo(name = "Picnic Player", version = BuildConfig.VERSION_NAME)
        deviceInfo = DeviceInfo(id = deviceIdentity.deviceId, name = deviceIdentity.deviceName)
        context = appContext
        // The SDK's default factory builds a KtorClient, but the Android SDK ships
        // the OkHttp engine (jellyfin-api-okhttp) — Ktor isn't on the classpath, so
        // the default throws NoClassDefFoundError on every request. Wire OkHttp explicitly.
        apiClientFactory = OkHttpFactory()
        socketConnectionFactory = OkHttpFactory()
    }

    /** An API client for [baseUrl] (null for not-yet-resolved) with optional token. */
    fun api(baseUrl: String? = null, accessToken: String? = null): ApiClient = jellyfin.createApi(baseUrl = baseUrl, accessToken = accessToken)

    /** Server discovery (LAN broadcast + address-candidate resolution). */
    val discovery: DiscoveryService get() = jellyfin.discovery

    /**
     * LAN-discovered servers, emitted as UDP replies arrive (not buffered until
     * the scan ends). Multicast replies need a [WifiManager.MulticastLock] for the
     * whole collection — without it Android drops them and discovery finds nothing.
     */
    fun discoverLocalServers(): Flow<ServerDiscoveryInfo> = flow {
        val wifi = appContext.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("picnic-discovery")?.apply {
            setReferenceCounted(false)
            runCatching { acquire() }
        }
        try {
            jellyfin.discovery.discoverLocalServers().collect { emit(it) }
        } finally {
            lock?.let { if (it.isHeld) runCatching { it.release() } }
        }
    }
}
