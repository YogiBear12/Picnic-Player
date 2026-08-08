package app.picnic.player.ui.settings

import app.picnic.player.data.settings.SubtitleAppearance
import app.picnic.player.data.settings.SubtitleArea
import app.picnic.player.data.settings.SubtitleBackgroundFill
import app.picnic.player.data.settings.SubtitleBackgroundStyle
import app.picnic.player.data.settings.SubtitleColour
import app.picnic.player.data.settings.SubtitleSize

internal fun SubtitleSize.display(): String = when (this) {
    SubtitleSize.SMALLER -> "Smaller"
    SubtitleSize.SMALL -> "Small"
    SubtitleSize.STANDARD -> "Standard"
    SubtitleSize.LARGE -> "Large"
    SubtitleSize.LARGER -> "Larger"
}

internal fun SubtitleColour.display(): String = when (this) {
    SubtitleColour.WHITE -> "White"
    SubtitleColour.YELLOW -> "Yellow"
    SubtitleColour.CYAN -> "Cyan"
    SubtitleColour.GREEN -> "Green"
}

internal fun SubtitleBackgroundFill.display(): String = when (this) {
    SubtitleBackgroundFill.TRANSLUCENT -> "Translucent"
    SubtitleBackgroundFill.SOLID -> "Solid"
}

internal fun SubtitleBackgroundStyle.display(): String = when (this) {
    SubtitleBackgroundStyle.BOXED -> "Boxed"
    SubtitleBackgroundStyle.WRAPPED -> "Wrapped"
}

internal fun SubtitleArea.display(): String = when (this) {
    SubtitleArea.SCREEN -> "Screen"
    SubtitleArea.IMAGE -> "Image"
    SubtitleArea.AUTOMATIC -> "Automatic"
}

internal fun SubtitleAppearance.insetDisplay(): String = "$insetPercent%"
