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

    suspend fun toggleBackground() = store.setSubtitleBackground(!current().background)

    suspend fun cycleBackgroundFill(forward: Boolean) = store.setSubtitleBackgroundFill(current().backgroundFill.step(forward))

    suspend fun cycleBackgroundStyle(forward: Boolean) = store.setSubtitleBackgroundStyle(current().backgroundStyle.step(forward))

    private suspend fun current(): SubtitleAppearance = store.settings.first().subtitleAppearance

    private inline fun <reified T : Enum<T>> T.step(forward: Boolean): T = enumValues<T>().let { it[(ordinal + (if (forward) 1 else -1) + it.size) % it.size] }
}
