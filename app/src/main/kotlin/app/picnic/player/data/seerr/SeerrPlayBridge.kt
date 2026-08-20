package app.picnic.player.data.seerr

import java.util.UUID

sealed interface DetailTarget {
    data class Jellyfin(val itemId: String) : DetailTarget

    data object Seerr : DetailTarget
}

fun seerrDetailTarget(
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null
): DetailTarget {
    val id = normalizeJellyfinUuid(jellyfinMediaId)
        ?: normalizeJellyfinUuid(jellyfinMediaId4k)
    return if (id != null) DetailTarget.Jellyfin(id) else DetailTarget.Seerr
}

fun jellyfinDetailIdOrNull(
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null
): String? = (seerrDetailTarget(jellyfinMediaId, jellyfinMediaId4k) as? DetailTarget.Jellyfin)?.itemId

fun shouldOpenJellyfinDetail(
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null
): Boolean = seerrDetailTarget(jellyfinMediaId, jellyfinMediaId4k) is DetailTarget.Jellyfin

fun normalizeJellyfinUuid(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null

    runCatching { UUID.fromString(trimmed) }.getOrNull()?.let { return it.toString() }

    val hex = trimmed.filter { it != '-' }
    if (hex.length != 32 || !hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
        return null
    }
    val dashed = buildString(36) {
        append(hex, 0, 8)
        append('-')
        append(hex, 8, 12)
        append('-')
        append(hex, 12, 16)
        append('-')
        append(hex, 16, 20)
        append('-')
        append(hex, 20, 32)
    }
    return runCatching { UUID.fromString(dashed).toString() }.getOrNull()
}
