package app.picnic.player.util

import java.util.Locale

/** Latin-script language names for audio/subtitle track panels. */
private val DISPLAY_LOCALE: Locale = Locale.US

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

    private fun normalizeTag(raw: String): String {
        val base = raw.replace('_', '-')
        if (base.contains('-') || base.length != 3) return base
        iso3ToIso2(base)?.let { return it }
        return base
    }

    private fun titleCase(text: String): String = text.split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(DISPLAY_LOCALE) else ch.toString()
        }
    }
}

/** ISO 639-2 code to ISO 639-1, accepting the bibliographic form the server stores (`fre`, `ger`, `chi`). */
internal fun iso3ToIso2(code: String): String? = ISO3_TO_ISO2[code.lowercase(Locale.ROOT)]

private val ISO3_TO_ISO2: Map<String, String> by lazy {
    val terminology = Locale.getISOLanguages().groupBy { Locale(it).isO3Language }.mapValues { it.value.first() }
    terminology + BIBLIOGRAPHIC_TO_TERMINOLOGY.mapNotNull { (bibliographic, t) -> terminology[t]?.let { bibliographic to it } }
}

private val BIBLIOGRAPHIC_TO_TERMINOLOGY = mapOf(
    "alb" to "sqi",
    "arm" to "hye",
    "baq" to "eus",
    "bur" to "mya",
    "chi" to "zho",
    "cze" to "ces",
    "dut" to "nld",
    "fre" to "fra",
    "geo" to "kat",
    "ger" to "deu",
    "gre" to "ell",
    "ice" to "isl",
    "mac" to "mkd",
    "mao" to "mri",
    "may" to "msa",
    "per" to "fas",
    "rum" to "ron",
    "slo" to "slk",
    "tib" to "bod",
    "wel" to "cym"
)
