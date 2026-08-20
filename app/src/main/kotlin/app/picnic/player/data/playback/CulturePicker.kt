package app.picnic.player.data.playback

import org.jellyfin.sdk.model.api.CultureDto

data class CulturePickerOption(
    val languageCode: String,
    val displayName: String
)

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

fun resolveLanguageCode(appPreference: String?, serverPreference: String?, deviceLanguage: String): String = appPreference?.takeIf { it.isNotBlank() }
    ?: serverPreference?.takeIf { it.isNotBlank() }
    ?: deviceLanguage

data class PinnedLanguageOptions(
    val options: List<CulturePickerOption>,
    val separatorAfterIndex: Int
)

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

sealed interface LanguagePickerRow {
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

data class LanguagePickerRows(
    val rows: List<LanguagePickerRow>,
    val separatorAfterIndex: Int
)

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

fun cultureDisplayName(
    languageCode: String,
    options: List<CulturePickerOption>,
    fallback: (String) -> String
): String {
    options.firstOrNull { option -> languageMatches(option.languageCode, languageCode) }
        ?.let { return it.displayName }
    return fallback(languageCode)
}
