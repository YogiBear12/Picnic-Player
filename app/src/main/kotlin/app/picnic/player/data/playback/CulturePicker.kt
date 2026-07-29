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

/**
 * One row of the language dialog. [DefaultAudioTrack] is audio-only: it means "start on the
 * file's default audio track" rather than naming a language.
 */
sealed interface LanguagePickerRow {
    /** Stable list key. */
    val key: String
    val label: String

    data object DefaultAudioTrack : LanguagePickerRow {
        override val key: String = "default-audio-track"
        override val label: String = "Original language"
    }

    data class Culture(val option: CulturePickerOption) : LanguagePickerRow {
        override val key: String get() = option.languageCode
        override val label: String get() = option.displayName
    }
}

/** Dialog rows plus where the pinned block ends. */
data class LanguagePickerRows(
    val rows: List<LanguagePickerRow>,
    /** A divider is drawn directly after this row index; -1 = no pinned block, no divider. */
    val separatorAfterIndex: Int
)

/**
 * Builds the dialog rows: optional Default row, then the pinned device language, then A–Z.
 * Everything above the divider is a quick-pick.
 */
fun languagePickerRows(
    base: List<CulturePickerOption>,
    deviceLanguage: String,
    includeDefaultAudioTrack: Boolean
): LanguagePickerRows {
    val pinned = pinnedLanguageOptions(base, deviceLanguage)
    val cultures = pinned.options.map { LanguagePickerRow.Culture(it) }
    if (!includeDefaultAudioTrack) {
        return LanguagePickerRows(cultures, pinned.separatorAfterIndex)
    }
    return LanguagePickerRows(
        rows = listOf(LanguagePickerRow.DefaultAudioTrack) + cultures,
        separatorAfterIndex = pinned.separatorAfterIndex + 1
    )
}

/** The checked row: Default when preferred, else the row naming [languageCode]. */
fun selectedLanguageRow(
    rows: List<LanguagePickerRow>,
    languageCode: String?,
    preferDefaultAudioTrack: Boolean
): LanguagePickerRow? = when {
    preferDefaultAudioTrack -> rows.firstOrNull { it is LanguagePickerRow.DefaultAudioTrack }
    languageCode.isNullOrBlank() -> null
    else -> rows.firstOrNull {
        it is LanguagePickerRow.Culture && languageMatches(it.option.languageCode, languageCode)
    }
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
