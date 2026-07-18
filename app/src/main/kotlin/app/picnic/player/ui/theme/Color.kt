package app.picnic.player.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Picnic brand palette, derived from the launcher banner (`tv_banner.png`):
 * deep teal surfaces with a neutral white accent for TV focus chrome. Dynamic
 * artwork colours (browse cards, ambient) are applied per-component — not here.
 * Screens read static colours via [PicnicTheme]'s [androidx.tv.material3.MaterialTheme.colorScheme].
 */
object PicnicColors {
    /** Default focus ring, border, and primary action accent (until dynamic colour applies). */
    val Accent = Color(0xFFFFFFFF)

    /** Brand cyan from the mascot — reserved for marketing/launcher assets, not default UI chrome. */
    val Cyan = Color(0xFF00B3F5)
    val CyanDim = Color(0xFF0A86B8)

    /** Deep teal banner base — secondary surfaces, gradients. */
    val Teal = Color(0xFF005276)
    val TealDeep = Color(0xFF00374F)

    /** Cinematic near-black with a cool blue undertone. */
    val Background = Color(0xFF060B10)
    val Surface = Color(0xFF0E151B)
    val SurfaceVariant = Color(0xFF18242E)

    /**
     * Dark glass fill shared by dialogs, context menus, notices, and resting action
     * buttons — the app's translucent-panel language. Focus states stay brighter.
     */
    val GlassFill = Color(0xEA181E24)

    /** Deep ocean ambient for grid screens. */
    val OceanDeep = Color(0xFF021824)
    val OceanMid = Color(0xFF04324A)
    val OceanGlow = Color(0xFF0A5C7A)

    /**
     * Static immersive ocean layers — non-hero browse only.
     * Not used by [app.picnic.player.ui.ambient.AmbientPaletteLoader] or dynamic extraction.
     */
    val OceanAbyss = Color(0xFF020610)
    val OceanSurface = Color(0xFF142A3D)
    val OceanTwilight = Color(0xFF3D2068)
    val OceanMidwater = Color(0xFF0B5E6B)
    val OceanKelp = Color(0xFF5C3D28)

    val OnDark = Color(0xFFE8EEF2)
    val OnDarkMuted = Color(0xFF9DAFBC)

    val Error = Color(0xFFFF6B6B)

    /** Request status chips (Settings → Requests). */
    val Success = Color(0xFF6BCB8B)
    val Warning = Color(0xFFE5C06B)
}
