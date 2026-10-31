package app.picnic.player.data.syncplay

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class SyncPlayClockTest {

    @Test
    fun symmetricDelayGivesTheExactOffset() {
        // Server 500 ms ahead, 40 ms each way, 10 ms server processing.
        val sample = ClockSample(sentMs = 1_000, serverReceivedMs = 1_540, serverSentMs = 1_550, receivedMs = 1_090)
        assertEquals(500, sample.offsetMs)
        assertEquals(80, sample.roundTripMs)
    }

    @Test
    fun serverBehindGivesANegativeOffset() {
        val sample = ClockSample(sentMs = 5_000, serverReceivedMs = 3_020, serverSentMs = 3_020, receivedMs = 5_040)
        assertEquals(-2_000, sample.offsetMs)
    }

    @Test
    fun theShortestRoundTripWins() {
        val slowAsymmetric = ClockSample(sentMs = 0, serverReceivedMs = 800, serverSentMs = 800, receivedMs = 400)
        val fast = ClockSample(sentMs = 0, serverReceivedMs = 520, serverSentMs = 520, receivedMs = 40)
        val medium = ClockSample(sentMs = 0, serverReceivedMs = 560, serverSentMs = 560, receivedMs = 120)
        assertEquals(500, bestOffsetMs(listOf(slowAsymmetric, fast, medium)))
    }

    @Test
    fun serverTimeRoundTripsThroughTheDeviceZone() {
        val instant = 1_761_900_000_123L
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(instant), ZoneId.systemDefault())
        assertEquals(instant, local.epochMsNear(instant))
    }

    @Test
    fun bothReadingsOfTheFallBackHourResolveToTheRightInstant() {
        val sydney = ZoneId.of("Australia/Sydney")
        val first = Instant.parse("2026-04-04T15:30:00Z").toEpochMilli()
        val second = Instant.parse("2026-04-04T16:30:00Z").toEpochMilli()
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(second), sydney)
        assertEquals(local, LocalDateTime.ofInstant(Instant.ofEpochMilli(first), sydney))
        assertEquals(first, local.epochMsNear(first + 40, sydney))
        assertEquals(second, local.epochMsNear(second - 40, sydney))
    }
}
