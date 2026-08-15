package app.picnic.player.ui.settings

import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleBackground

enum class SubtitleAppearanceSetting { SIZE, COLOR, BACKGROUND, BACKGROUND_FILL, AREA, INSET }

data class SubtitleAppearanceRow(
    val setting: SubtitleAppearanceSetting,
    val label: String,
    val value: String,
    val enabled: Boolean = true
)

internal fun subtitleAppearanceRows(appearance: SubtitleAppearance): List<SubtitleAppearanceRow> = listOf(
    SubtitleAppearanceRow(SubtitleAppearanceSetting.SIZE, "Size", appearance.size.display()),
    SubtitleAppearanceRow(SubtitleAppearanceSetting.COLOR, "Color", appearance.colour.display()),
    SubtitleAppearanceRow(SubtitleAppearanceSetting.BACKGROUND, "Background", appearance.background.display()),
    // The fill row stays listed while the background is off — removing a focused row disposes
    // the focused node. It only mutes.
    SubtitleAppearanceRow(
        SubtitleAppearanceSetting.BACKGROUND_FILL,
        "Background fill",
        appearance.backgroundFill.display(),
        enabled = appearance.background != SubtitleBackground.OFF
    ),
    SubtitleAppearanceRow(SubtitleAppearanceSetting.AREA, "Subtitle area", appearance.area.display()),
    SubtitleAppearanceRow(SubtitleAppearanceSetting.INSET, "Subtitle offset", appearance.insetDisplay())
)
