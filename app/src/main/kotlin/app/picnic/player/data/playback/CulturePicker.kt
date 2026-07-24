package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.CultureDto

/** One row in the Settings language picker: a Jellyfin culture. */
data class CulturePickerOption(
    /** Two-letter ISO when possible. */
    val languageCode: String,
    /** Primary UI label — never a raw ISO code. */
    val displayName: String
)

/**
 * Builds the Languages dialog list: cultures sorted by [CultureDto.displayName] (A–Z).
 * Persisted code prefers two-letter ISO.
 */
fun culturePickerOptions(cultures: List<CultureDto>): List<CulturePickerOption> = cultures
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

/**
 * The language actually in effect, resolving the precedence chain shared by the Settings summary
 * and playback track selection: local app override > server (Jellyfin) preference > device system
 * language. Blank strings count as "not set" and drop through. Always concrete — there is no
 * "no preference / file default" state.
 */
fun resolveLanguageCode(appPreference: String?, serverPreference: String?, deviceLanguage: String): String = appPreference?.takeIf { it.isNotBlank() }
    ?: serverPreference?.takeIf { it.isNotBlank() }
    ?: deviceLanguage

/** Picker rows plus where the pinned block ends, once the device language is lifted up. */
data class PinnedLanguageOptions(
    val options: List<CulturePickerOption>,
    /** A divider is drawn directly after this row index; -1 = no pinned block, no divider. */
    val separatorAfterIndex: Int
)

/**
 * Reorders [base] (A–Z) so the device language sits pinned at the top, above a divider, and is
 * removed from the A–Z body below — the likeliest pick stays in reach (#149). When the device
 * language is absent from the cultures, nothing is pinned and no divider is drawn.
 */
fun pinnedLanguageOptions(
    base: List<CulturePickerOption>,
    deviceLanguage: String
): PinnedLanguageOptions {
    val system = deviceLanguage.takeIf { it.isNotBlank() }
        ?.let { device -> base.firstOrNull { languageMatches(it.languageCode, device) } }
        ?: return PinnedLanguageOptions(base, separatorAfterIndex = -1)
    return PinnedLanguageOptions(
        options = listOf(system) + base.filter { it != system },
        separatorAfterIndex = 0
    )
}

/** Label for a resolved language code using the culture list; falls back to [fallback]. */
fun cultureDisplayName(
    languageCode: String,
    options: List<CulturePickerOption>,
    fallback: (String) -> String
): String {
    options.firstOrNull { option -> languageMatches(option.languageCode, languageCode) }
        ?.let { return it.displayName }
    return fallback(languageCode)
}
