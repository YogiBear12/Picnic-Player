package app.picnic.player.data.jellyfin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinUrlsTest {

    @Test
    fun `a url with no query opens one`() {
        assertEquals(
            "https://host/Videos/1/Trickplay/320/0.jpg?ApiKey=t0ken",
            "https://host/Videos/1/Trickplay/320/0.jpg".withApiKey("t0ken")
        )
    }

    @Test
    fun `a url that already has a query gets another parameter`() {
        assertEquals(
            "https://host/Items/1/Images/Chapter/0?tag=abc&ApiKey=t0ken",
            "https://host/Items/1/Images/Chapter/0?tag=abc".withApiKey("t0ken")
        )
    }

    @Test
    fun `a token is found in either position`() {
        assertTrue("https://host/s?ApiKey=t0ken".carriesApiKey())
        assertTrue("https://host/s?x=1&ApiKey=t0ken".carriesApiKey())
    }

    @Test
    fun `the legacy spelling a 10 11 server sends is still recognized`() {
        assertTrue("https://host/s?api_key=t0ken".carriesApiKey())
        assertTrue("https://host/s?x=1&api_key=t0ken".carriesApiKey())
    }

    @Test
    fun `case does not matter`() {
        assertTrue("https://host/s?APIKEY=t0ken".carriesApiKey())
        assertTrue("https://host/s?API_KEY=t0ken".carriesApiKey())
    }

    @Test
    fun `a parameter merely ending in the name is not a token`() {
        assertFalse("https://host/s?XApiKey=t0ken".carriesApiKey())
        assertFalse("https://host/s?x=1&myapi_key=t0ken".carriesApiKey())
    }

    @Test
    fun `a url with no token is reported as carrying none`() {
        assertFalse("https://host/Subtitles/2/0/Stream.srt".carriesApiKey())
        assertFalse("https://host/Subtitles/2/0/Stream.srt?format=srt".carriesApiKey())
    }
}
