package app.picnic.player.ui.ambient

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil3.Image
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val FOCUS_DEBOUNCE_MS = 250L

data class ShownArt(val url: String, val image: Image)

data class Seed(val art: ShownArt?, val palette: AmbientPalette?)

@HiltViewModel
class BackdropViewModel internal constructor(
    private val art: ArtworkSource,
    private val paletteCached: (String) -> AmbientPalette?,
    private val paletteLoad: suspend (String) -> AmbientPalette?
) : ViewModel() {
    @Inject
    constructor(art: BackdropArtworkLoader, palettes: AmbientPaletteLoader) : this(
        art,
        palettes::cached,
        palettes::load
    )

    var shown by mutableStateOf<ShownArt?>(null)
        private set
    var target by mutableStateOf<String?>(null)
        private set
    var ready by mutableStateOf<ShownArt?>(null)
        private set
    var failed by mutableStateOf(false)
        private set
    var palette by mutableStateOf<AmbientPalette?>(null)
        private set
    var paletteTarget by mutableStateOf<String?>(null)
        private set

    private var artJob: Job? = null
    private var paletteJob: Job? = null

    fun requestArt(url: String?) {
        if (url == target) return
        target = url
        ready = null
        failed = false
        artJob?.cancel()
        if (url == null) return
        shown?.takeIf { it.url == url }?.let {
            ready = it
            return
        }
        art.peek(url)?.let {
            ready = ShownArt(url, it)
            return
        }
        artJob = viewModelScope.launch {
            val image = art.load(url)
            if (url == target) {
                ready = image?.let { ShownArt(url, it) }
                failed = image == null
            }
        }
    }

    fun requestPalette(url: String?) {
        if (url == paletteTarget) return
        paletteTarget = url
        paletteJob?.cancel()
        if (url == null) {
            palette = null
            return
        }
        paletteCached(url)?.let {
            palette = it
            return
        }
        paletteJob = viewModelScope.launch {
            delay(FOCUS_DEBOUNCE_MS)
            val loaded = paletteLoad(url)
            if (url == paletteTarget) palette = loaded
        }
    }

    fun commit() {
        if (target == null || failed) {
            shown = null
        } else if (ready?.url == target) {
            shown = ready
        }
    }

    fun seed(backdropUrl: String?, ambientUrl: String?): Seed {
        val seededArt = backdropUrl?.let { url ->
            ready?.takeIf { it.url == url }
                ?: shown?.takeIf { it.url == url }
                ?: art.peek(url)?.let { ShownArt(url, it) }
        }
        val seededPalette = ambientUrl?.let { url ->
            if (paletteTarget == url) palette else paletteCached(url)
        }
        return Seed(seededArt, seededPalette)
    }
}
