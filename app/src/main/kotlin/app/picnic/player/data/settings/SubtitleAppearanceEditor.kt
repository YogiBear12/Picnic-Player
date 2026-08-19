package app.picnic.player.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

enum class SubtitleAppearanceSetting { SIZE, COLOR, BACKGROUND, BACKGROUND_FILL, AREA, INSET }

@Singleton
class SubtitleAppearanceEditor @Inject constructor(
    private val store: SettingsStore
) {
    suspend fun step(setting: SubtitleAppearanceSetting, forward: Boolean) {
        val current = store.settings.first().subtitleAppearance
        when (setting) {
            SubtitleAppearanceSetting.SIZE -> store.setSubtitleSize(current.size.step(forward))
            SubtitleAppearanceSetting.COLOR -> store.setSubtitleColour(current.colour.step(forward))
            SubtitleAppearanceSetting.BACKGROUND -> store.setSubtitleBackground(current.background.step(forward))
            SubtitleAppearanceSetting.BACKGROUND_FILL -> store.setSubtitleBackgroundFill(current.backgroundFill.step(forward))
            SubtitleAppearanceSetting.AREA -> store.setSubtitleArea(current.area.step(forward))
            SubtitleAppearanceSetting.INSET -> store.setSubtitleInsetPercent(current.insetPercent + if (forward) 1 else -1)
        }
    }

    private inline fun <reified T : Enum<T>> T.step(forward: Boolean): T = enumValues<T>().let { it[(ordinal + (if (forward) 1 else -1) + it.size) % it.size] }
}
