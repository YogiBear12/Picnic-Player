package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.CultureDto

/** One row in the Settings language picker (Unspecified or a Jellyfin culture). */
data class CulturePickerOption(
    /** Two-letter ISO when possible; null = Unspecified. */
    val languageCode: String?,
    /** Primary UI label — never a raw ISO code. */
    val displayName: String
)

/**
 * Builds the Languages dialog list: Unspecified first, then cultures sorted by
 * [CultureDto.displayName] (A–Z). Persisted code prefers two-letter ISO.
 */
fun culturePickerOptions(cultures: List<CultureDto>): List<CulturePickerOption> {
    val sorted = cultures
        .mapNotNull { culture ->
            val code = culture.twoLetterIsoLanguageName.takeIf { it.isNotBlank() }
                ?: culture.threeLetterIsoLanguageName?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val label = culture.displayName.takeIf { it.isNotBlank() }
                ?: culture.name.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            CulturePickerOption(languageCode = code.lowercase(), displayName = label)
        }
        .distinctBy { it.languageCode }
        .sortedBy { it.displayName.lowercase() }
    return listOf(CulturePickerOption(languageCode = null, displayName = "Unspecified")) + sorted
}

/** Label for a stored preference code using the culture list; falls back to [fallback]. */
fun cultureDisplayName(
    languageCode: String?,
    options: List<CulturePickerOption>,
    fallback: (String) -> String
): String {
    if (languageCode.isNullOrBlank()) return "Unspecified"
    options.firstOrNull { option ->
        option.languageCode != null && languageMatches(option.languageCode, languageCode)
    }?.let { return it.displayName }
    return fallback(languageCode)
}
