package app.picnic.player.ui.ambient

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelStore
import coil3.Image
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BackdropViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        store.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun peekHitIsSynchronousAndCommitShowsIt() = runTest {
        val source = FakeArtworkSource()
        val image = FakeArtworkImage()
        source.cached["a"] = image
        val model = model(source)

        model.requestArt("a")

        assertEquals("a", model.target)
        assertSame(image, model.ready?.image)
        assertNull(model.shown)
        model.commit()
        assertSame(model.ready, model.shown)
        runCurrent()
        assertTrue(source.loads.isEmpty())
    }

    @Test
    fun missLoadsThenBecomesReadyWithoutObserver() = runTest {
        val source = FakeArtworkSource()
        val image = FakeArtworkImage()
        val model = model(source)
        model.requestArt("a")
        assertNull(model.ready)
        runCurrent()
        assertEquals(listOf("a"), source.loads)

        source.complete("a", image)
        runCurrent()

        assertSame(image, model.ready?.image)
        assertNull(model.shown)
        assertFalse(model.failed)
        model.commit()
        assertSame(image, model.shown?.image)
    }

    @Test
    fun onlyLatestTargetBecomesReadyDespiteLateCanceledLoads() = runTest {
        val source = FakeArtworkSource(ignoreCancellation = true)
        val model = model(source)
        model.requestArt("a")
        runCurrent()
        assertNull(model.ready)
        model.requestArt("b")
        runCurrent()
        assertNull(model.ready)
        model.requestArt("c")
        runCurrent()
        assertNull(model.ready)

        val image = FakeArtworkImage()
        source.complete("c", image)
        runCurrent()
        val ready = model.ready
        assertEquals("c", ready?.url)
        assertSame(image, ready?.image)

        source.complete("a", FakeArtworkImage())
        runCurrent()
        assertSame(ready, model.ready)
        source.complete("b", null)
        runCurrent()
        assertSame(ready, model.ready)
        assertFalse(model.failed)
    }

    @Test
    fun failureSetsFailedAndCommitClearsShown() = runTest {
        val source = FakeArtworkSource()
        source.cached["a"] = FakeArtworkImage()
        val model = model(source)
        model.requestArt("a")
        model.commit()
        val shown = model.shown
        model.requestArt("b")
        runCurrent()
        source.complete("b", null)
        runCurrent()

        assertTrue(model.failed)
        assertNull(model.ready)
        assertSame(shown, model.shown)
        model.commit()
        assertNull(model.shown)
    }

    @Test
    fun nullTargetClearsShownOnlyWhenCommittedAndRejectsLateLoad() = runTest {
        val source = FakeArtworkSource(ignoreCancellation = true)
        source.cached["a"] = FakeArtworkImage()
        val model = model(source)
        model.requestArt("a")
        model.commit()
        val shown = model.shown
        model.requestArt("b")
        runCurrent()

        model.requestArt(null)
        assertNull(model.target)
        assertNull(model.ready)
        assertFalse(model.failed)
        assertSame(shown, model.shown)
        model.commit()
        assertNull(model.shown)
        source.complete("b", FakeArtworkImage())
        runCurrent()
        assertNull(model.ready)
    }

    @Test
    fun paletteCacheHitIsSynchronousAndDoesNotLoad() = runTest {
        val palette = palette(Color.Red)
        val loads = mutableListOf<String>()
        val model = model(
            cached = { palette },
            load = { url ->
                loads.add(url)
                null
            }
        )

        model.requestPalette("ambient")

        assertEquals("ambient", model.paletteTarget)
        assertSame(palette, model.palette)
        advanceTimeBy(FOCUS_DEBOUNCE_MS + 1)
        assertTrue(loads.isEmpty())
    }

    @Test
    fun paletteLoadDebouncesAndStaleResultCannotReplaceCachedPalette() = runTest {
        val first = CompletableDeferred<AmbientPalette?>()
        val cached = palette(Color.Blue)
        val loads = mutableListOf<String>()
        val model = model(
            cached = { url -> cached.takeIf { url == "b" } },
            load = { url ->
                loads.add(url)
                withContext(NonCancellable) { first.await() }
            }
        )
        model.requestPalette("a")
        runCurrent()
        advanceTimeBy(FOCUS_DEBOUNCE_MS - 1)
        assertTrue(loads.isEmpty())
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("a"), loads)

        model.requestPalette("b")
        assertSame(cached, model.palette)
        first.complete(palette(Color.Red))
        runCurrent()

        assertEquals("b", model.paletteTarget)
        assertSame(cached, model.palette)
    }

    @Test
    fun coldPaletteLoadAppliesWithoutArtCommit() = runTest {
        val loaded = palette(Color.Green)
        val model = model(load = { loaded })

        model.requestPalette("ambient")
        advanceTimeBy(FOCUS_DEBOUNCE_MS + 1)

        assertSame(loaded, model.palette)
        assertNull(model.shown)
    }

    @Test
    fun disablingPaletteClearsItAndRejectsLateResult() = runTest {
        val pending = CompletableDeferred<AmbientPalette?>()
        val cached = palette(Color.Blue)
        val model = model(
            cached = { url -> cached.takeIf { url == "a" } },
            load = { withContext(NonCancellable) { pending.await() } }
        )
        model.requestPalette("a")
        model.requestPalette("b")
        advanceTimeBy(FOCUS_DEBOUNCE_MS + 1)

        model.requestPalette(null)
        assertNull(model.paletteTarget)
        assertNull(model.palette)
        pending.complete(palette(Color.Red))
        runCurrent()
        assertNull(model.palette)
    }

    @Test
    fun commitWithPendingTargetKeepsShownUntilReady() = runTest {
        val source = FakeArtworkSource()
        source.cached["a"] = FakeArtworkImage()
        val model = model(source)
        model.requestArt("a")
        model.commit()
        val shown = model.shown
        model.requestArt("b")
        runCurrent()

        model.commit()
        assertSame(shown, model.shown)

        val image = FakeArtworkImage()
        source.complete("b", image)
        runCurrent()
        model.commit()
        assertSame(image, model.shown?.image)
    }

    @Test
    fun commitClearsFailedAndEmptyTargets() = runTest {
        val source = FakeArtworkSource()
        source.cached["a"] = FakeArtworkImage()
        val model = model(source)
        model.requestArt("a")
        model.commit()
        model.requestArt("b")
        runCurrent()
        source.complete("b", null)
        runCurrent()

        model.commit()
        assertNull(model.shown)
        model.requestArt("a")
        model.commit()
        assertFalse(model.failed)
        model.requestArt(null)
        model.commit()
        assertNull(model.shown)
    }

    @Test
    fun seedReadsCacheWithoutStateWritesOrJobs() = runTest {
        val source = FakeArtworkSource()
        val image = FakeArtworkImage()
        source.cached["a"] = image
        val palette = palette(Color.Red)
        var paletteLoads = 0
        val model = model(
            source,
            cached = { palette },
            load = {
                paletteLoads++
                null
            }
        )
        val before = state(model)

        val seed = model.seed("a", "ambient")

        assertSame(image, seed.art?.image)
        assertSame(palette, seed.palette)
        assertEquals(before, state(model))
        model.seed("missing", "ambient")
        model.seed(null, null)
        advanceTimeBy(FOCUS_DEBOUNCE_MS + 1)
        assertEquals(before, state(model))
        assertTrue(source.loads.isEmpty())
        assertEquals(0, paletteLoads)
    }

    @Test
    fun seedPrefersMatchingReadyThenShownOverCache() = runTest {
        val source = FakeArtworkSource()
        val shownImage = FakeArtworkImage()
        val readyImage = FakeArtworkImage()
        source.cached["a"] = shownImage
        val retainedPalette = palette(Color.Blue)
        val palettes = mutableMapOf("ambient" to retainedPalette)
        val model = model(source, cached = palettes::get)
        model.requestArt("a")
        model.requestPalette("ambient")
        model.commit()
        val shown = model.shown
        source.cached["a"] = FakeArtworkImage()
        source.cached["b"] = readyImage
        model.requestArt("b")
        model.requestPalette("ambient")
        val ready = model.ready
        source.cached["b"] = FakeArtworkImage()
        palettes.clear()
        val before = state(model)

        assertSame(ready, model.seed("b", "ambient").art)
        assertSame(shown, model.seed("a", "ambient").art)
        assertSame(retainedPalette, model.seed("b", "ambient").palette)
        assertNull(model.seed(null, null).art)
        assertNull(model.seed(null, null).palette)
        assertEquals(before, state(model))
        runCurrent()
        assertTrue(source.loads.isEmpty())
    }

    @Test
    fun seedAfterFailurePreservesFailedStateAndDoesNotRetry() = runTest {
        val source = FakeArtworkSource()
        val model = model(source)
        model.requestArt("a")
        runCurrent()
        source.complete("a", null)
        runCurrent()
        assertTrue(model.failed)
        val before = state(model)

        assertNull(model.seed("a", null).art)
        runCurrent()

        assertEquals(before, state(model))
        assertEquals(listOf("a"), source.loads)
    }

    @Test
    fun seedWithPendingTargetKeepsAllStateAndJobs() = runTest {
        val source = FakeArtworkSource()
        source.cached["a"] = FakeArtworkImage()
        val pending = CompletableDeferred<AmbientPalette?>()
        var paletteLoads = 0
        val model = model(
            source,
            load = {
                paletteLoads++
                pending.await()
            }
        )
        model.requestArt("a")
        model.commit()
        model.requestArt("b")
        model.requestPalette("ambient")
        advanceTimeBy(FOCUS_DEBOUNCE_MS + 1)
        val before = state(model)

        assertNull(model.seed("b", "ambient").art)
        model.seed("missing", "other")
        runCurrent()
        assertEquals(before, state(model))
        assertEquals(listOf("b"), source.loads)
        assertEquals(1, paletteLoads)

        val image = FakeArtworkImage()
        val palette = palette(Color.Red)
        source.complete("b", image)
        pending.complete(palette)
        runCurrent()
        assertSame(image, model.ready?.image)
        assertSame(palette, model.palette)
    }

    @Test
    fun returningToShownArtReusesSameInstanceAfterCacheClear() = runTest {
        val source = FakeArtworkSource()
        source.cached["a"] = FakeArtworkImage()
        val model = model(source)
        model.requestArt("a")
        model.commit()
        val shown = model.shown
        model.requestArt("b")
        runCurrent()
        source.cached.clear()

        model.requestArt("a")

        assertSame(shown, model.ready)
        assertFalse(model.failed)
        runCurrent()
        assertEquals(listOf("b"), source.loads)
        assertSame(shown, model.seed("a", null).art)
    }

    @Test
    fun equalTargetsAreIdempotentAndArtAndPaletteChangeIndependently() = runTest {
        val source = FakeArtworkSource()
        val palettes = mapOf("ambient-a" to palette(Color.Red), "ambient-b" to palette(Color.Blue))
        val model = model(source, cached = palettes::get)
        model.requestArt("a")
        model.requestPalette("ambient-a")
        runCurrent()
        model.requestArt("a")
        model.requestPalette("ambient-a")
        model.requestPalette("ambient-b")
        runCurrent()

        assertEquals(listOf("a"), source.loads)
        assertSame(palettes["ambient-b"], model.palette)
        val image = FakeArtworkImage()
        source.complete("a", image)
        runCurrent()
        val ready = model.ready
        model.requestPalette(null)
        assertSame(ready, model.ready)
        assertNull(model.palette)
        model.requestPalette("ambient-a")
        model.requestArt("b")
        assertNull(model.ready)
        assertFalse(model.failed)
        assertSame(palettes["ambient-a"], model.palette)
    }

    private fun model(
        art: ArtworkSource = FakeArtworkSource(),
        cached: (String) -> AmbientPalette? = { null },
        load: suspend (String) -> AmbientPalette? = { null }
    ): BackdropViewModel = BackdropViewModel(art, cached, load).also { store.put("backdrop", it) }

    private fun palette(color: Color) = AmbientPalette(color, color, color, color)

    private fun state(model: BackdropViewModel): List<Any?> = listOf(
        model.shown,
        model.target,
        model.ready,
        model.failed,
        model.palette,
        model.paletteTarget
    )

    private class FakeArtworkSource(private val ignoreCancellation: Boolean = false) : ArtworkSource {
        val cached = mutableMapOf<String, Image>()
        val loads = mutableListOf<String>()
        private val pending = mutableMapOf<String, CompletableDeferred<Image?>>()

        override fun peek(url: String): Image? = cached[url]

        override suspend fun load(url: String): Image? {
            loads.add(url)
            val result = pending.getOrPut(url) { CompletableDeferred() }
            return if (ignoreCancellation) {
                withContext(NonCancellable) { result.await() }
            } else {
                result.await()
            }
        }

        fun complete(url: String, image: Image?) {
            checkNotNull(pending[url]).complete(image)
        }
    }
}
