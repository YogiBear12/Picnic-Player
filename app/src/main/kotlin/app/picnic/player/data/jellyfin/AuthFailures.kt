package app.picnic.player.data.jellyfin

import org.jellyfin.sdk.api.client.exception.InvalidStatusException

/** True when the server rejected the access token (deleted user, revoked token, etc.). */
fun Throwable.isAuthFailure(): Boolean = generateSequence(this) { it.cause }
    .any { it is InvalidStatusException && (it.status == 401 || it.status == 403) }
