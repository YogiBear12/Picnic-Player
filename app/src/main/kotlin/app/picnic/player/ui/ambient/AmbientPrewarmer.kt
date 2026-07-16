package app.picnic.player.ui.ambient

import app.picnic.player.data.settings.SettingsStore
import app.picnic.player.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Warms the [AmbientPaletteLoader] cache for a backdrop URL the instant a card is
 * selected, so the detail screen's wash is a cache hit by the time it composes (no
 * ocean-then-colour flash). The warm runs on an **application-scoped** coroutine so the
 * extraction survives the grid/row leaving composition during the navigation transition —
 * a [androidx.compose.runtime.rememberCoroutineScope] would cancel it mid-flight.
 *
 * No-op when ambient backgrounds are off (nothing to warm a cache we won't read), and the
 * loader dedupes via its own cache + mutex, so calling this on every selection is cheap.
 */
@Singleton
class AmbientPrewarmer @Inject constructor(
    @ApplicationScope private val scope: CoroutineScope,
    private val loader: AmbientPaletteLoader,
    private val settingsStore: SettingsStore
) {
    fun warm(url: String?) {
        if (url == null) return
        scope.launch {
            if (settingsStore.settings.first().ambientBackgrounds) loader.load(url)
        }
    }
}
