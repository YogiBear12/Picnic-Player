package app.picnic.player.ui.ambient

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf

/** Ambient palette loader for backdrop washes and artwork-derived focus accents. */
val LocalAmbientPaletteLoader = staticCompositionLocalOf<AmbientPaletteLoader> {
    error("AmbientPaletteLoader not provided")
}

/** When false, card focus chrome is plain white instead of the artwork-derived accent. */
val LocalColouredFocus = compositionLocalOf { true }

/** When false, focused card glow stays static instead of pulsing brightness. */
val LocalPulseFocusGlow = compositionLocalOf { true }

/** When false, the dynamic colour-extracted wash is replaced by the static ocean wash. */
val LocalAmbientBackgrounds = compositionLocalOf { true }

/** Warms the ambient palette cache on card selection so detail opens without a colour flash. */
val LocalAmbientPrewarmer = staticCompositionLocalOf<AmbientPrewarmer> {
    error("AmbientPrewarmer not provided")
}

/** When true, unwatched episode count badges cap at "99+" instead of showing full number. */
val LocalCapBadgeCount = compositionLocalOf { true }
