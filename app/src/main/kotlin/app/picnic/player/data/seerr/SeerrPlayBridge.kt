package app.picnic.player.data.seerr

import java.util.UUID

/**
 * Hybrid Detail routing gate (#46).
 *
 * Decides whether a Seerr title opens **Jellyfin Detail** (it is in the user's
 * library — play it) or **Seerr Detail** (it is not — request it). The gate is
 * id presence, not [SeerrMediaStatus.AVAILABLE], so partially available TV still
 * lands on Jellyfin Detail and can request more seasons from there.
 *
 * A library copy present **only in 4K** counts as in-library: prefer the SD/HD
 * [SeerrMediaInfo.jellyfinMediaId], else fall back to
 * [SeerrMediaInfo.jellyfinMediaId4k] (CONTEXT "Hybrid Details": never treat 4K
 * library files as non-library). This is the single place the id-gate is
 * decided — nav routing, the Seerr Detail redirect and the action row all read
 * it, so entry points cannot diverge.
 *
 * When **both** ids exist they are un-merged same-TMDB library entries; v1
 * prefers HD. A future multi-version entry picker plugs in here by adding a
 * [DetailTarget] case — call sites read [DetailTarget] and need no change.
 */
sealed interface DetailTarget {
    /** Open Jellyfin Detail for this canonical library item id. */
    data class Jellyfin(val itemId: String) : DetailTarget

    /** Not in library — open Seerr Detail to request. */
    data object Seerr : DetailTarget
}

/**
 * Routes a Seerr title to Jellyfin or Seerr Detail from its library ids.
 * Prefers SD/HD, falls back to 4K. Seerr often stores ids as 32 undashed hex
 * chars; both are normalized to a dashed UUID for the Jellyfin SDK.
 */
fun seerrDetailTarget(
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null
): DetailTarget {
    val id = normalizeJellyfinUuid(jellyfinMediaId)
        ?: normalizeJellyfinUuid(jellyfinMediaId4k)
    return if (id != null) DetailTarget.Jellyfin(id) else DetailTarget.Seerr
}

/**
 * Canonical Jellyfin item UUID when the title is in library (HD preferred, else
 * 4K); otherwise null. Convenience over [seerrDetailTarget] for call sites that
 * only need the id.
 */
fun jellyfinDetailIdOrNull(
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null
): String? = (seerrDetailTarget(jellyfinMediaId, jellyfinMediaId4k) as? DetailTarget.Jellyfin)?.itemId

/** True when the title opens Jellyfin Detail rather than Seerr Detail. */
fun shouldOpenJellyfinDetail(
    jellyfinMediaId: String?,
    jellyfinMediaId4k: String? = null
): Boolean = seerrDetailTarget(jellyfinMediaId, jellyfinMediaId4k) is DetailTarget.Jellyfin

/**
 * Returns a canonical dashed UUID string, or null if [raw] is blank/invalid.
 * Accepts already-dashed UUIDs and 32-hex undashed forms from Seerr.
 */
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
