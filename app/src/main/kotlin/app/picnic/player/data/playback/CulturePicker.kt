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

/**
 * The language actually in effect: a local app override wins; otherwise the server's
 * configured preference (#149). Blank strings count as "not set" and drop through.
 * null = neither is set, i.e. genuinely Unspecified.
 */
fun effectiveLanguageCode(appPreference: String?, serverPreference: String?): String? = appPreference?.takeIf { it.isNotBlank() } ?: serverPreference?.takeIf { it.isNotBlank() }

/** Picker rows plus where the pinned block ends, once the device language is lifted up. */
data class PinnedLanguageOptions(
    val options: List<CulturePickerOption>,
    /** A divider is drawn directly after this row index (the last pinned row). */
    val separatorAfterIndex: Int
)

/**
 * Reorders [base] (Unspecified + A–Z) so the device language sits directly under
 * Unspecified and is removed from the A–Z body below — the two likeliest picks stay
 * above a divider (#149). When the device language is absent from the cultures, only
 * Unspecified is pinned.
 */
fun pinnedLanguageOptions(
    base: List<CulturePickerOption>,
    deviceLanguage: String
): PinnedLanguageOptions {
    val unspecified = base.firstOrNull { it.languageCode == null }
        ?: return PinnedLanguageOptions(base, separatorAfterIndex = 0)
    val languages = base.filter { it.languageCode != null }
    val system = deviceLanguage.takeIf { it.isNotBlank() }
        ?.let { device -> languages.firstOrNull { languageMatches(it.languageCode, device) } }
        ?: return PinnedLanguageOptions(listOf(unspecified) + languages, separatorAfterIndex = 0)
    return PinnedLanguageOptions(
        options = listOf(unspecified, system) + languages.filter { it != system },
        separatorAfterIndex = 1
    )
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
