package app.picnic.player.data.jellyfin

private const val API_KEY_PARAM = "ApiKey"
private const val LEGACY_API_KEY_PARAM = "api_key"

internal fun String.withApiKey(token: String): String = this + (if ('?' in this) "&" else "?") + API_KEY_PARAM + "=" + token

internal fun String.carriesApiKey(): Boolean = carriesParam(API_KEY_PARAM) || carriesParam(LEGACY_API_KEY_PARAM)

private fun String.carriesParam(name: String): Boolean = contains("?$name=", ignoreCase = true) || contains("&$name=", ignoreCase = true)
