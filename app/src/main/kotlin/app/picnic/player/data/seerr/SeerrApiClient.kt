package app.picnic.player.data.seerr

import dagger.Lazy
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class SeerrHttpException(
    val code: Int,
    message: String
) : Exception(message)

@Singleton
class SeerrApiClient @Inject constructor(
    private val json: Json,
    private val httpClient: Lazy<OkHttpClient>
) {
    private val cookieJar = SeerrCookieJar()
    private val client: OkHttpClient by lazy {
        httpClient.get().newBuilder()
            .cookieJar(cookieJar)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var apiBase: String = ""

    fun setBaseUrl(storedBaseUrl: String?) {
        apiBase = storedBaseUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { SeerrCredentialStore.apiBaseUrl(it) }
            .orEmpty()
        if (apiBase.isBlank()) cookieJar.clear()
    }

    fun clearCookies() = cookieJar.clear()

    fun loadCookie(storedBaseUrl: String, cookieHeaderValue: String) {
        val url = SeerrCredentialStore.apiBaseUrl(storedBaseUrl).toHttpUrlOrNull() ?: return
        cookieJar.setConnectSid(url, cookieHeaderValue)
    }

    fun exportConnectSid(storedBaseUrl: String): String? {
        val url = SeerrCredentialStore.apiBaseUrl(storedBaseUrl).toHttpUrlOrNull() ?: return null
        return cookieJar.connectSid(url)
    }

    fun status(): SeerrStatusResponse = get("/status")

    fun authMe(): SeerrUser = get("/auth/me")

    fun authJellyfin(username: String, password: String): SeerrUser {
        val body = json.encodeToString(
            SeerrJellyfinAuthBody(
                username = username,
                password = password,
                email = username
            )
        )
        return postRaw("/auth/jellyfin", body)
    }

    fun authLocal(email: String, password: String): SeerrUser = postRaw("/auth/local", json.encodeToString(SeerrLocalAuthBody(email = email, password = password)))

    fun publicSettings(): SeerrPublicSettings = get("/settings/public")

    fun search(query: String, page: Int = 1): SeerrSearchPage = get("/search?query=${query.encodeUrl()}&page=$page")

    fun discoverTrending(page: Int = 1): SeerrSearchPage = get("/discover/trending?page=$page")

    fun discoverMovies(page: Int = 1): SeerrSearchPage = get("/discover/movies?page=$page")

    fun discoverTv(page: Int = 1): SeerrSearchPage = get("/discover/tv?page=$page")

    fun discoverMoviesUpcoming(page: Int = 1): SeerrSearchPage = get("/discover/movies/upcoming?page=$page")

    fun discoverTvUpcoming(page: Int = 1): SeerrSearchPage = get("/discover/tv/upcoming?page=$page")

    fun movie(id: Int): SeerrMovieDetails = get("/movie/$id")

    fun tv(id: Int): SeerrTvDetails = get("/tv/$id")

    fun movieRecommendations(id: Int): SeerrSearchPage = get("/movie/$id/recommendations")

    fun tvRecommendations(id: Int): SeerrSearchPage = get("/tv/$id/recommendations")

    fun person(id: Int): SeerrPersonDetails = get("/person/$id")

    fun personCombinedCredits(id: Int): SeerrPersonCombinedCredits = get("/person/$id/combined_credits")

    fun movieGenres(): List<SeerrGenre> = get("/genres/movie")

    fun tvGenres(): List<SeerrGenre> = get("/genres/tv")

    fun createRequest(body: SeerrCreateRequestBody): SeerrMediaRequest = postRaw("/request", json.encodeToString(body))

    fun updateRequest(requestId: Int, body: SeerrUpdateRequestBody): SeerrMediaRequest = putRaw("/request/$requestId", json.encodeToString(body))

    fun createIssue(body: SeerrCreateIssueBody): SeerrIssue = postRaw("/issue", json.encodeToString(body))

    fun issues(take: Int, skip: Int): SeerrIssueListPage = get("/issue?take=$take&skip=$skip&filter=all&sort=modified")

    fun addIssueComment(issueId: Int, message: String): SeerrIssue = postRaw("/issue/$issueId/comment", json.encodeToString(SeerrIssueCommentBody(message)))

    fun issue(issueId: Int): SeerrIssue = get("/issue/$issueId")

    fun setIssueStatus(issueId: Int, resolved: Boolean): SeerrIssue = postRaw("/issue/$issueId/${if (resolved) "resolved" else "open"}", "{}")

    fun deleteIssue(issueId: Int) = delete("/issue/$issueId")

    fun deleteRequest(requestId: Int) = delete("/request/$requestId")

    private fun delete(path: String) {
        val request = Request.Builder().url(url(path)).delete().build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw SeerrHttpException(
                    response.code,
                    response.message.ifBlank { "HTTP ${response.code}" }
                )
            }
        }
    }

    fun listRequests(
        take: Int = 50,
        skip: Int = 0,
        filter: String = "all"
    ): SeerrRequestListPage = get("/request?take=$take&skip=$skip&filter=$filter&sort=added")

    private inline fun <reified T> get(path: String): T {
        val request = Request.Builder().url(url(path)).get().build()
        return execute(request)
    }

    private inline fun <reified T> postRaw(path: String, jsonBody: String): T {
        val request = Request.Builder()
            .url(url(path))
            .post(jsonBody.toRequestBody(jsonMedia))
            .build()
        return execute(request)
    }

    private inline fun <reified T> putRaw(path: String, jsonBody: String): T {
        val request = Request.Builder()
            .url(url(path))
            .put(jsonBody.toRequestBody(jsonMedia))
            .build()
        return execute(request)
    }

    private inline fun <reified T> execute(request: Request): T {
        require(apiBase.isNotBlank()) { "Seerr base URL not set" }
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SeerrHttpException(
                    response.code,
                    raw.ifBlank { response.message }.ifBlank { "HTTP ${response.code}" }
                )
            }
            return json.decodeFromString(raw)
        }
    }

    private fun url(path: String): String {
        val base = apiBase.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return "$base$p"
    }

    private fun String.encodeUrl(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())
}

private class SeerrCookieJar : CookieJar {
    private val lock = Any()
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(lock) {
            val host = url.host
            val existing = store.getOrPut(host) { mutableListOf() }
            for (cookie in cookies) {
                existing.removeAll { it.name == cookie.name }
                existing += cookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(lock) {
        store[url.host].orEmpty().filter { it.matches(url) }
    }

    fun clear() = synchronized(lock) { store.clear() }

    fun setConnectSid(url: HttpUrl, value: String) {
        val cookie = Cookie.Builder()
            .name("connect.sid")
            .value(value)
            .domain(url.host)
            .path("/")
            .build()
        synchronized(lock) {
            store[url.host] = mutableListOf(cookie)
        }
    }

    fun connectSid(url: HttpUrl): String? = synchronized(lock) {
        store[url.host]?.firstOrNull { it.name == "connect.sid" }?.value
    }
}
