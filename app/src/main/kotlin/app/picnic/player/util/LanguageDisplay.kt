package app.picnic.player.util

import java.util.Locale

/** Latin-script language names for audio/subtitle track panels. */
private val DISPLAY_LOCALE: Locale = Locale.US

/**
 * Human-readable audio/subtitle language labels with regional variants
 * (e.g. `es-419` → "Spanish (Latin America)").
 */
object LanguageDisplay {

    fun name(languageTag: String?): String {
        if (languageTag.isNullOrBlank()) return "Unknown"
        val tag = normalizeTag(languageTag.trim())
        val locale = Locale.forLanguageTag(tag)
        val display = locale.getDisplayName(DISPLAY_LOCALE)
        if (display.isNotBlank() && !display.equals(tag, ignoreCase = true)) {
            return titleCase(display)
        }
        return titleCase(tag)
    }

    /** Big line for track panel; [title] is the smaller secondary line when present. */
    fun trackLines(languageLine: String, title: String?): Pair<String, String?> {
        val trimmedTitle = title?.trim()?.takeIf { it.isNotEmpty() }
        return languageLine to trimmedTitle
    }

    private fun normalizeTag(raw: String): String {
        val base = raw.replace('_', '-')
        if (base.contains('-') || base.length != 3) return base
        iso3ToIso2(base)?.let { return it }
        return base
    }

    private fun iso3ToIso2(code: String): String? {
        val lower = code.lowercase(Locale.ROOT)
        for (lang in Locale.getISOLanguages()) {
            val locale = Locale(lang)
            if (locale.isO3Language.equals(lower, ignoreCase = true)) return lang
        }
        return null
    }

    private fun titleCase(text: String): String = text.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(DISPLAY_LOCALE) else ch.toString()
        }
    }
}
