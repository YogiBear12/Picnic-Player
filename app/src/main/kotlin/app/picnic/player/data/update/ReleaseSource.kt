package app.picnic.player.data.update

import app.picnic.player.BuildConfig
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the latest release from the host configured in [BuildConfig.UPDATE_REPO]
 * (a releases-API base URL like `https://api.github.com/repos/<owner>/<repo>`).
 * Gitea deliberately mirrors GitHub's release API shape — `/releases/latest`,
 * `tag_name`, `body`, `assets[].name`, `assets[].browser_download_url` — so this
 * one parser serves both and a Gitea test exercises the production code path.
 *
 * A blank UPDATE_REPO means "no release host configured": [latestRelease] returns
 * null and the updater stays inert.
 */
@Singleton
class ReleaseSource @Inject constructor(
    private val json: Json
) {
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val configured: Boolean = BuildConfig.UPDATE_REPO.isNotBlank()

    /** Blocking network + parse — call off the main thread. Null on any failure. */
    fun latestRelease(): UpdateRelease? = runCatching {
        if (!configured) return null
        val request = Request.Builder()
            .url("${BuildConfig.UPDATE_REPO.trimEnd('/')}/releases/latest")
            .get()
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            parseRelease(response.body?.string() ?: return null)
        }
    }.getOrNull()

    /** Streams [url]; caller owns the response. Throws on HTTP failure. */
    fun download(url: String): okhttp3.Response {
        val response = http.newCall(Request.Builder().url(url).get().build()).execute()
        check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
        return response
    }

    internal fun parseRelease(body: String): UpdateRelease? {
        val root = json.parseToJsonElement(body).jsonObject
        val version = UpdateVersion.parse(root["tag_name"]?.jsonPrimitive?.content)
            ?: return null
        val assets = root["assets"]?.jsonArray.orEmpty()
        val apk = pickApkAsset(
            assets.mapNotNull { asset ->
                val obj = asset.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val url = obj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val size = obj["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L
                ApkAsset(name, url, size)
            }
        ) ?: return null
        val notes = root["body"]?.jsonPrimitive?.content.orEmpty()
        return UpdateRelease(
            version = version,
            apkUrl = apk.url,
            apkSizeBytes = apk.sizeBytes,
            notes = notes
        )
    }

    data class ApkAsset(val name: String, val url: String, val sizeBytes: Long)

    companion object {
        /** CI names assets `Picnic-Player-v<version>-release.apk`; prefer that,
         *  fall back to any .apk so hand-cut releases still work. */
        fun pickApkAsset(assets: List<ApkAsset>): ApkAsset? {
            val preferred = assets.firstOrNull { asset ->
                asset.name.startsWith("Picnic-Player-v") && asset.name.endsWith("-release.apk")
            }
            return preferred ?: assets.firstOrNull { it.name.endsWith(".apk") }
        }
    }
}
