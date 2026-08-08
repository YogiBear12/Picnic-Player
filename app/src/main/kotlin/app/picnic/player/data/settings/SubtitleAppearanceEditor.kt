package app.picnic.player.data.settings

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

@Singleton
class SubtitleAppearanceEditor @Inject constructor(
    private val store: SettingsStore
) {
    suspend fun cycleSize(forward: Boolean) = store.setSubtitleSize(current().size.step(forward))

    suspend fun cycleColour(forward: Boolean) = store.setSubtitleColour(current().colour.step(forward))

    suspend fun cycleBackground(forward: Boolean) = store.setSubtitleBackground(current().background.step(forward))

    suspend fun cycleBackgroundFill(forward: Boolean) = store.setSubtitleBackgroundFill(current().backgroundFill.step(forward))

    suspend fun cycleArea(forward: Boolean) = store.setSubtitleArea(current().area.step(forward))

    suspend fun stepInset(forward: Boolean) = store.setSubtitleInsetPercent(current().insetPercent + if (forward) 1 else -1)

    private suspend fun current(): SubtitleAppearance = store.settings.first().subtitleAppearance

    private inline fun <reified T : Enum<T>> T.step(forward: Boolean): T = enumValues<T>().let { it[(ordinal + (if (forward) 1 else -1) + it.size) % it.size] }
}
