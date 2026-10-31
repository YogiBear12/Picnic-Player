package app.picnic.player.data.syncplay

import app.picnic.player.data.media.SessionApi
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import org.jellyfin.sdk.api.client.extensions.systemApi

data class ClockSample(val sentMs: Long, val serverReceivedMs: Long, val serverSentMs: Long, val receivedMs: Long) {
    val offsetMs: Long get() = ((serverReceivedMs - sentMs) + (serverSentMs - receivedMs)) / 2
    val roundTripMs: Long get() = (receivedMs - sentMs) - (serverSentMs - serverReceivedMs)
}

// The shortest round trip has the least room for asymmetric delay, so its offset is the most trustworthy.
fun bestOffsetMs(samples: List<ClockSample>): Long = samples.minBy { it.roundTripMs }.offsetMs

@Singleton
class SyncPlayClock @Inject constructor(private val api: SessionApi) {
    @Volatile private var offsetMs = 0L

    // shortcut: one sync per call, no periodic resync; a TV clock drifts well under 100 ms over a film. Resync on a timer if rooms run for hours.
    suspend fun sync() {
        val system = api.client().systemApi
        val samples = List(SAMPLES) {
            api.onIo {
                val sent = System.currentTimeMillis()
                val utc = system.getUtcTime().content
                ClockSample(sent, utc.requestReceptionTime.epochMsNear(sent), utc.responseTransmissionTime.epochMsNear(sent), System.currentTimeMillis())
            }
        }
        offsetMs = bestOffsetMs(samples)
    }

    fun serverNowMs(): Long = System.currentTimeMillis() + offsetMs

    fun toLocalMs(serverMs: Long): Long = serverMs - offsetMs

    private companion object {
        const val SAMPLES = 3
    }
}

// The SDK parses server instants into the device's zone and drops the offset, so a time in the DST fall-back hour
// is ambiguous; the reading nearest the local clock is the right one while the two clocks are within 30 min.
fun LocalDateTime.epochMsNear(referenceMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
    val zoned = atZone(zone)
    return listOf(zoned.withEarlierOffsetAtOverlap(), zoned.withLaterOffsetAtOverlap())
        .map { it.toInstant().toEpochMilli() }
        .minBy { abs(it - referenceMs) }
}
