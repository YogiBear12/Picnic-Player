package app.picnic.player.data.jellyfin

import org.jellyfin.sdk.api.client.exception.InvalidStatusException

/** True when the server rejected the access token (deleted user, revoked token, etc.). */
fun Throwable.isAuthFailure(): Boolean = generateSequence(this) { it.cause }
    .any { it is InvalidStatusException && (it.status == 401 || it.status == 403) }

/**
 * Card/label text for a non-auth failure reaching a server: an HTTP status means the server
 * answered with an error ("503 Server Error"); no status means it never responded at all
 * (timeout / connection refused / DNS / offline) → "Server Unreachable". Call only after
 * [isAuthFailure] has ruled out 401/403.
 */
fun Throwable.serverErrorMessage(): String {
    val status = generateSequence(this) { it.cause }
        .filterIsInstance<InvalidStatusException>()
        .firstOrNull()
        ?.status
    return if (status != null) "$status Server Error" else "Server Unreachable"
}
