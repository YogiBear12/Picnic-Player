package app.picnic.player.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioRoutePolicyTest {
    private fun route(
        boost: AudioBoost = AudioBoost.OFF,
        nightMode: NightMode = NightMode.OFF,
        speed: Float = 1.0f
    ): AudioRoute = AudioRoutePolicy.requiredRoute(boost, nightMode, speed)

    @Test
    fun `no effect leaves the native route`() {
        assertEquals(AudioRoute.NATIVE, route())
    }

    @Test
    fun `any boost level forces pcm`() {
        assertEquals(AudioRoute.PCM, route(boost = AudioBoost.LOW))
        assertEquals(AudioRoute.PCM, route(boost = AudioBoost.HIGH))
    }

    @Test
    fun `any night mode level forces pcm`() {
        assertEquals(AudioRoute.PCM, route(nightMode = NightMode.LIGHT))
        assertEquals(AudioRoute.PCM, route(nightMode = NightMode.STRONG))
    }

    @Test
    fun `speed away from one forces pcm in both directions`() {
        assertEquals(AudioRoute.PCM, route(speed = 1.25f))
        assertEquals(AudioRoute.PCM, route(speed = 0.75f))
    }

    @Test
    fun `float noise around one stays native`() {
        assertEquals(AudioRoute.NATIVE, route(speed = 1.0001f))
        assertEquals(AudioRoute.NATIVE, route(speed = 0.9999f))
    }

    @Test
    fun `a track that cannot pass through never reloads`() {
        assertFalse(
            AudioRoutePolicy.reloadNeeded(
                required = AudioRoute.PCM,
                applied = AudioRoute.NATIVE,
                trackCanPassThrough = false
            )
        )
    }

    @Test
    fun `turning the first effect on reloads a passthrough track`() {
        assertTrue(
            AudioRoutePolicy.reloadNeeded(
                required = AudioRoute.PCM,
                applied = AudioRoute.NATIVE,
                trackCanPassThrough = true
            )
        )
    }

    @Test
    fun `clearing the last effect reloads back to native`() {
        assertTrue(
            AudioRoutePolicy.reloadNeeded(
                required = AudioRoute.NATIVE,
                applied = AudioRoute.PCM,
                trackCanPassThrough = true
            )
        )
    }

    @Test
    fun `a boost step within pcm does not reload`() {
        assertFalse(
            AudioRoutePolicy.reloadNeeded(
                required = AudioRoute.PCM,
                applied = AudioRoute.PCM,
                trackCanPassThrough = true
            )
        )
    }
}
