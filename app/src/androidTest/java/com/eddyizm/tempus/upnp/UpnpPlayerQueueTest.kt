package com.eddyizm.tempus.upnp

import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eddyizm.tempus.util.Constants
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** SimpleBasePlayer needs a real Looper, hence instrumented. No renderer, an HTTP client fakes the SOAP. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class UpnpPlayerQueueTest {

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Fake Renderer</friendlyName>
            <UDN>uuid:11111111-2222-3333-4444-555555555555</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/AVTransport/control.xml</controlURL>
                <SCPDURL>/AVTransport/scpd.xml</SCPDURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    private fun okBody(action: String, state: String) =
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
           <s:Body><u:${action}Response xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
           <CurrentTransportState>${state}</CurrentTransportState><RelTime>${relTime()}</RelTime>
           <TrackDuration>${reportedDuration}</TrackDuration>
           <TrackURI>${reportedTrackUri.orEmpty().replace("&", "&amp;")}</TrackURI>
           </u:${action}Response></s:Body></s:Envelope>"""

    private fun faultBody(code: Int, text: String) =
        """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
           <s:Body><s:Fault><detail><UPnPError><errorCode>$code</errorCode>
           <errorDescription>$text</errorDescription></UPnPError></detail>
           </s:Fault></s:Body></s:Envelope>"""

    private fun controlPoint(): UpnpControlPoint = UpnpControlPoint(
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val action = chain.request().header("SOAPAction").orEmpty().substringAfterLast('#').trim('"')
                actions += action
                if (action == "SetAVTransportURI") {
                    stoppedPolls.set(stoppedPollsAfterHandover)
                    val body = Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
                    handovers += body.substringAfter("<CurrentURI>").substringBefore("</CurrentURI>")
                        .replace("&amp;", "&")
                    holdHandovers?.await(5, TimeUnit.SECONDS)
                }
                if (action == "SetNextAVTransportURI") SystemClock.sleep(delayNextUriMs)
                if (action == "GetPositionInfo") SystemClock.sleep(delayPositionInfoMs)
                if (action == "GetPositionInfo") {
                    holdPolls?.let { pollHeld.countDown(); it.await(5, TimeUnit.SECONDS) }
                }
                val state = if (action == "GetTransportInfo" && stoppedPolls.get() > 0) {
                    stoppedPolls.decrementAndGet()
                    "STOPPED"
                } else if (action == "GetTransportInfo" && pausedPolls.get() > 0) {
                    pausedPolls.decrementAndGet()
                    "PAUSED_PLAYBACK"
                } else {
                    reportedState
                }
                val fault = when {
                    refuseCode != null -> refuseCode!! to "Refused"
                    refuseSeekCode != null && action == "Seek" -> refuseSeekCode!! to "Seek refused"
                    refusePauseCode != null && action == "Pause" -> refusePauseCode!! to "Pause refused"
                    refuseNextUriCode != null && action == "SetNextAVTransportURI" -> refuseNextUriCode!! to "Next refused"
                    else -> null
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (fault != null) 500 else 200)
                    .message(if (fault != null) "Internal Server Error" else "OK")
                    .body(
                        (fault?.let { faultBody(it.first, it.second) } ?: okBody(action, state))
                            .toResponseBody("text/xml".toMediaType())
                    )
                    .build()
            })
            .build()
    )

    private lateinit var device: UpnpDevice
    private lateinit var player: UpnpPlayer

    @Volatile private var refuseCode: Int? = null
    @Volatile private var refuseSeekCode: Int? = null
    @Volatile private var refusePauseCode: Int? = null
    @Volatile private var refuseNextUriCode: Int? = null
    @Volatile private var reportedTrackUri: String? = null
    @Volatile private var delayNextUriMs = 0L

    @Volatile private var relTimeCountingFrom: Long? = null
    @Volatile private var relTimeLagMs = 0L

    @Volatile private var reportedDuration = "00:03:00"

    @Volatile private var delayPositionInfoMs = 0L

    private fun relTime(): String {
        val from = relTimeCountingFrom ?: return reportedRelTime
        val ms = (SystemClock.elapsedRealtime() - from - relTimeLagMs).coerceAtLeast(0)
        val seconds = ms / 1000
        return "00:%02d:%02d".format(seconds / 60, seconds % 60)
    }
    @Volatile private var reportedRelTime = "00:00:05"
    @Volatile private var reportedState = "PLAYING"
    @Volatile private var stoppedPollsAfterHandover = 0
    private val stoppedPolls = AtomicInteger()
    private val pausedPolls = AtomicInteger()
    @Volatile private var holdHandovers: CountDownLatch? = null
    @Volatile private var holdPolls: CountDownLatch? = null
    @Volatile private var pollHeld = CountDownLatch(1)
    private val actions = CopyOnWriteArrayList<String>()
    private val handovers = CopyOnWriteArrayList<String>()

    private fun urlAt(n: Int) = "http://192.0.2.50:4533/rest/stream?id=$n"

    private fun itemAt(n: Int, seconds: Int = 180): MediaItem = MediaItem.Builder()
        .setMediaId("track-$n")
        .setUri(urlAt(n))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setExtras(Bundle().apply { putInt("duration", seconds) })
                .build()
        )
        .build()

    private fun itemWith(
        n: Int,
        suffix: String,
        type: String = Constants.MEDIA_TYPE_MUSIC,
        path: String? = null
    ): MediaItem = MediaItem.Builder()
        .setMediaId("track-$n")
        .setUri(urlAt(n))
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setExtras(Bundle().apply {
                    putInt("duration", 180)
                    putString("type", type)
                    putString("suffix", suffix)
                    path?.let { putString("path", it) }
                })
                .build()
        )
        .build()

    @Before
    fun setUp() {
        refuseCode = null
        refuseSeekCode = null
        refusePauseCode = null
        refuseNextUriCode = null
        reportedTrackUri = null
        reportedState = "PLAYING"
        reportedRelTime = "00:00:05"
        reportedDuration = "00:03:00"
        relTimeCountingFrom = null
        relTimeLagMs = 0L
        delayPositionInfoMs = 0L
        delayNextUriMs = 0L
        stoppedPollsAfterHandover = 0
        stoppedPolls.set(0)
        holdHandovers = null
        holdPolls = null
        pollHeld = CountDownLatch(1)
        actions.clear()
        handovers.clear()
        device = UpnpDevice.parseDescription(description, "http://192.0.2.10:1549/").single()
        onMain { player = UpnpPlayer(controlPoint(), device, Looper.getMainLooper()) }
    }

    @After
    fun tearDown() {
        if (::player.isInitialized) onMain { player.release() }
    }

    @Test
    fun replacingAQueueItemDoesNotBringDownTheApp() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)))
            player.replaceMediaItem(1, itemAt(22))
        }

        assertEquals(3, onMainGet { player.mediaItemCount })
        assertEquals("track-22", onMainGet { player.getMediaItemAt(1).mediaId })
    }

    @Test
    fun addingAndRemovingAheadOfTheCurrentTrackKeepsUsOnIt() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 1, 0L)
            player.addMediaItems(3, listOf(itemAt(4)))
            player.removeMediaItem(3)
        }

        assertEquals("track-2", onMainGet { player.currentMediaItem?.mediaId })
        assertEquals(1, onMainGet { player.currentMediaItemIndex })
    }

    @Test
    fun removingTheCurrentTrackMovesToTheOneThatTookItsPlace() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 1, 0L)
            player.removeMediaItem(1)
        }

        assertEquals(2, onMainGet { player.mediaItemCount })
        assertEquals("track-3", onMainGet { player.currentMediaItem?.mediaId })
    }

    @Test
    fun aRendererThatHasNotStartedYetIsNotATrackThatEnded() {
        stoppedPollsAfterHandover = 2
        onMain {
            player.setMediaItems((1..6).map { itemAt(it) }, 0, 0L)
            player.playWhenReady = true
        }
        SystemClock.sleep(6000)

        assertEquals("the queue walked itself: $handovers", listOf(urlAt(1)), handovers)
        assertEquals(0, onMainGet { player.currentMediaItemIndex })
        assertEquals("track-1", onMainGet { player.currentMediaItem?.mediaId })
        assertNull(onMainGet { player.playerError })
    }

    @Test
    fun aRendererThatNeverStartsIsGivenUpOnInsteadOfWalkingTheQueue() {
        stoppedPollsAfterHandover = 100
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }

        val error = awaitError(timeoutMillis = 25_000)

        assertNotNull("a renderer that never started was never given up on", error)
        val message = error!!.message.orEmpty()
        assertTrue("message did not name the host: $message", message.contains("192.0.2.50"))
        assertTrue("message did not give a reason: $message", message.contains("fetches the stream"))
        assertEquals("the queue advanced past a track that never played", 0, onMainGet { player.currentMediaItemIndex })
        assertEquals("more than the first track was handed over: $handovers", listOf(urlAt(1)), handovers)
    }

    @Test
    fun theLastOfTwoSeeksToTheSameTrackDecidesTheReportedPosition() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        awaitState(Player.STATE_READY)

        val gate = CountDownLatch(1)
        holdPolls = gate
        assertTrue("no poll arrived to hold", pollHeld.await(10, TimeUnit.SECONDS))
        holdPolls = null

        onMain { player.seekTo(30_000L) }
        // Long enough for the poll interval to put a read between the two seeks.
        SystemClock.sleep(2500)
        onMain { player.seekTo(90_000L) }

        gate.countDown()
        SystemClock.sleep(500)

        // The renderer's own report is a fixed five seconds, which is what the poll would write.
        val reported = onMainGet { player.currentPosition }
        assertTrue("the bar sat on the first target or on the poll's position: $reported", reported >= 90_000L)
        assertTrue("the position ran past the second target: $reported", reported < 95_000L)
    }

    @Test
    fun aSeekOvertakenByATrackChangeDoesNotWriteItsPosition() {
        val gate = CountDownLatch(1)
        holdHandovers = gate
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2)), 0, 0L)
            player.seekTo(60_000L)
            player.seekToNextMediaItem()
        }
        holdHandovers = null
        gate.countDown()
        awaitState(Player.STATE_READY)
        SystemClock.sleep(1000)

        assertEquals("track-2", onMainGet { player.currentMediaItem?.mediaId })
        assertEquals("the second track was never handed over: $handovers", urlAt(2), handovers.last())
        val reported = onMainGet { player.currentPosition }
        assertTrue("the overtaken seek's target survived the load: $reported", reported < 5_000L)
    }

    @Test
    fun aPauseTheRendererRefusedDoesNotArmASecondHandover() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        awaitState(Player.STATE_READY)

        refusePauseCode = 501
        onMain { player.playWhenReady = false }
        awaitState(Player.STATE_IDLE)
        // Its state is not asserted, since the poll puts it back to ready within one interval.
        assertTrue("the refused pause never reached the catch: $actions", actions.contains("Pause"))
        refusePauseCode = null
        handovers.clear()
        actions.clear()

        onMain {
            player.prepare()
            player.playWhenReady = true
        }
        SystemClock.sleep(1500)

        assertTrue("the open track was handed over again: $handovers", handovers.isEmpty())
        assertTrue("no play reached the renderer: $actions", actions.contains("Play"))
    }

    @Test
    fun aRendererThatCannotFetchTheStreamSaysWhy() {
        refuseCode = 716
        onMain { player.setMediaItems(listOf(itemAt(1))) }

        val error = awaitError()
        assertNotNull("the failure never reached the player", error)
        val message = error!!.message.orEmpty()
        assertTrue("message did not name the host: $message", message.contains("192.0.2.50"))
        assertTrue("message did not give a reason: $message", message.contains("fetches the stream"))
    }

    @Test
    fun aRendererThatTakesTheUrlReportsNoError() {
        onMain { player.setMediaItems(listOf(itemAt(1))) }
        SystemClock.sleep(1500)

        assertNull(onMainGet { player.playerError })
    }

    @Test
    fun theQueuePositionFollowsWhatTheRendererIsActuallyPlaying() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        reportedTrackUri = urlAt(2)

        val moved = awaitIndex(1)

        assertEquals("the app never followed the renderer", 1, moved)
        assertEquals("track-2", onMainGet { player.currentMediaItem?.mediaId })
    }

    @Test
    fun aRefusedSeekTargetDoesNotAbandonTheTrack() = seekRefusedWith(711)

    @Test
    fun anUnsupportedSeekModeDoesNotAbandonTheTrack() = seekRefusedWith(710)

    @Test
    fun aSeekThatFailsOutrightDoesNotAbandonTheTrack() = seekRefusedWith(501)

    @Test
    fun aPositionWhilePausedWaitsForThePlay() {
        onMain { player.setMediaItems(listOf(itemAt(1)), 0, 30_000L) }
        awaitState(Player.STATE_READY)
        assertFalse("a seek went to a paused renderer: $actions", actions.contains("Seek"))

        onMain { player.playWhenReady = true }
        SystemClock.sleep(1000)

        val play = actions.indexOf("Play")
        val seek = actions.indexOf("Seek")
        assertTrue("no play went out: $actions", play >= 0)
        assertTrue("the position was not applied after the play: $actions", seek > play)
    }

    @Test
    fun aPositionWaitsForTheRendererToReportPlaying() {
        stoppedPollsAfterHandover = 3
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 60_000L)
            player.playWhenReady = true
        }
        SystemClock.sleep(2500)

        val sent = actions.toList()
        val seek = sent.indexOf("Seek")
        assertTrue("no seek went out: $sent", seek >= 0)
        val readsBefore = sent.subList(0, seek).count { it == "GetTransportInfo" }
        assertTrue("the seek went out before the renderer reported playing: $sent", readsBefore > 3)
    }

    @Test
    fun aPrepareAfterTheTrackIsOpenTouchesNothing() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        awaitState(Player.STATE_READY)
        handovers.clear()
        actions.clear()

        onMain { player.prepare() }
        SystemClock.sleep(500)

        assertTrue("the open track was handed over again: $handovers", handovers.isEmpty())
        assertFalse("the open track was restarted: $actions", actions.contains("Seek"))
    }

    private fun seekRefusedWith(code: Int) {
        refuseSeekCode = code
        // Playing, because a seek is only sent while playing and a paused load sends none.
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 30_000L)
            player.playWhenReady = true
        }
        SystemClock.sleep(2500)
        assertTrue("no seek went out, so nothing was refused: $actions", actions.contains("Seek"))

        assertEquals("the load was abandoned on a refused position", Player.STATE_READY, onMainGet { player.playbackState })
        assertNull("a refused position was reported as a failed track", onMainGet { player.playerError })
        assertEquals("track-1", onMainGet { player.currentMediaItem?.mediaId })
    }

    @Test
    fun aStoppedRenderersLastUrlIsNotTreatedAsOpen() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(2500)
        reportedTrackUri = urlAt(1)
        reportedState = "STOPPED"
        assertTrue("the stop was never read", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })
        handovers.clear()
        actions.clear()

        onMain { player.prepare() }
        SystemClock.sleep(1000)

        assertEquals("a stopped renderer's track was not handed over: $actions", listOf(urlAt(1)), handovers)
        assertFalse("a stopped renderer's track was restarted in place: $actions", actions.contains("Seek"))
    }

    @Test
    fun theTrackAlreadyOpenIsRestartedInsteadOfHandedOverAgain() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        awaitState(Player.STATE_READY)
        onMain { player.seekToNextMediaItem() }
        awaitIndex(1)
        SystemClock.sleep(500)

        assertEquals(listOf(urlAt(1)), handovers)
        assertTrue("the open track was not restarted: $actions", actions.contains("Seek"))
        assertNull(onMainGet { player.playerError })
    }

    @Test
    fun aBurstOfNextTapsLoadsOnce() {
        val gate = CountDownLatch(1)
        holdHandovers = gate
        onMain {
            player.setMediaItems((1..6).map { itemAt(it) }, 0, 0L)
            player.playWhenReady = true
            repeat(5) { player.seekToNextMediaItem() }
        }
        holdHandovers = null
        gate.countDown()
        awaitIndex(5)
        SystemClock.sleep(1000)

        // One when the taps land before the worker reaches the first load, two when they do not.
        assertTrue("one load per tap went out: $handovers", handovers.size <= 2)
        assertEquals(urlAt(6), handovers.last())
        // An overtaken load restarts the open track, so a seek here is a load that should have gone.
        assertFalse("an overtaken load still ran: $actions", actions.contains("Seek"))
        assertEquals("track-6", onMainGet { player.currentMediaItem?.mediaId })
    }

    @Test
    fun aRefusedLoadStopsTheRenderer() {
        refuseCode = 716
        onMain { player.setMediaItems(listOf(itemAt(1))) }
        awaitError()

        assertTrue("the renderer was left holding the refused track: $actions", actions.contains("Stop"))
    }

    @Test
    fun aRefusalThatIsNotAboutTheStreamDoesNotBlameTheServer() {
        refuseCode = 501
        onMain { player.setMediaItems(listOf(itemAt(1))) }

        val message = awaitError()?.message.orEmpty()
        assertTrue("the code was not named: $message", message.contains("501"))
        assertFalse("blamed the server address for a 501: $message", message.contains("fetches the stream"))
    }

    @Test
    fun aPollInFlightDuringATapDoesNotUndoIt() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 1, 0L)
            player.playWhenReady = true
        }
        awaitState(Player.STATE_READY)
        reportedTrackUri = urlAt(2)
        val gate = CountDownLatch(1)
        holdPolls = gate
        assertTrue("no poll arrived to hold", pollHeld.await(10, TimeUnit.SECONDS))
        holdPolls = null

        onMain { player.seekToPreviousMediaItem() }
        gate.countDown()
        SystemClock.sleep(1000)

        // The fake still reports the old track, so what is pinned is that the tap's load went out.
        assertEquals("the previous track was never handed over: $handovers", urlAt(1), handovers.last())
    }

    @Test
    fun theRendererIsHandedTheBuiltUrlNotTheItemsOwn() {
        val live = { item: MediaItem -> "http://192.0.2.60:4533/rest/stream?id=${item.mediaId}&live=1" }
        onMain {
            player.release()
            player = UpnpPlayer(controlPoint(), device, Looper.getMainLooper(), streamUrl = live)
            player.setMediaItems(listOf(itemAt(1), itemAt(2)), 0, 0L)
            player.playWhenReady = true
        }
        awaitState(Player.STATE_READY)
        reportedTrackUri = live(itemAt(2))
        val moved = awaitIndex(1)

        assertEquals(listOf(live(itemAt(1))), handovers)
        assertEquals("the renderer's report was not matched against the built URL", 1, moved)
    }

    @Test
    fun aStatePublishedWithNoNewReadingDoesNotStepThePositionBack() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        val gate = CountDownLatch(1)
        holdPolls = gate
        assertTrue("no poll arrived to hold", pollHeld.await(10, TimeUnit.SECONDS))
        holdPolls = null

        SystemClock.sleep(1200)
        val before = onMainGet { player.currentPosition }
        onMain { player.addMediaItem(itemAt(9)) }
        val after = onMainGet { player.currentPosition }
        gate.countDown()

        assertTrue(
            "publishing the state stepped the position back from $before to $after",
            after >= before - 250
        )
    }

    @Test
    fun aSlowHandoverDoesNotAddItsOwnDelayToTheReportedPosition() {
        val trackChanges = CopyOnWriteArrayList<Long>()
        onMain {
            player.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (oldPosition.mediaItemIndex != newPosition.mediaItemIndex) {
                        trackChanges += newPosition.positionMs
                    }
                }
            })
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        delayNextUriMs = 1500L
        reportedTrackUri = urlAt(2)
        assertEquals("the app never followed the renderer", 1, awaitIndex(1))

        // The fake answers five seconds for every position it is asked for.
        assertEquals("no track change was reported", 1, trackChanges.size)
        assertTrue(
            "the handover's delay was added to the position: ${trackChanges.first()}",
            trackChanges.first() < 6000
        )
    }

    @Test
    fun aSlowLoadDoesNotAddItsOwnDelayToTheReportedPosition() {
        val readyAt = CopyOnWriteArrayList<Long>()
        onMain {
            player.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) readyAt += player.currentPosition
                }
            })
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        val gate = CountDownLatch(1)
        holdHandovers = gate
        readyAt.clear()
        onMain { player.seekToNextMediaItem() }
        SystemClock.sleep(1500)
        gate.countDown()
        holdHandovers = null

        // The index moves when the tap is handled, before the load runs, so wait on ready instead.
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (readyAt.isEmpty() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("the load never reported ready", readyAt.isNotEmpty())

        assertTrue(
            "the load's delay was added to the position: $readyAt",
            readyAt.first() < 1000
        )
    }

    @Test
    fun aReadingASecondBehindTheBarDoesNotDragItBack() {
        val jumps = CopyOnWriteArrayList<String>()
        relTimeCountingFrom = SystemClock.elapsedRealtime()
        onMain {
            player.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                        jumps += "reason=$reason ${oldPosition.positionMs} to ${newPosition.positionMs}"
                    }
                }
            })
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(5000)

        jumps.clear()
        relTimeLagMs = 1000L
        SystemClock.sleep(6000)

        assertTrue("the reading dragged the bar: $jumps", jumps.isEmpty())
    }

    @Test
    fun aPositionAtTheEndOfTheTrackIsNotBelievedWhilePlaying() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedRelTime = "00:03:00"
        SystemClock.sleep(5000)

        val reported = onMainGet { player.currentPosition }
        assertTrue("the bar was thrown to the end of the track: $reported", reported < 60_000)
    }

    @Test
    fun aReadingThatTookTooLongToArriveIsThrownAway() {
        val jumps = CopyOnWriteArrayList<String>()
        relTimeCountingFrom = SystemClock.elapsedRealtime()
        onMain {
            player.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex) {
                        jumps += "reason=$reason ${oldPosition.positionMs} to ${newPosition.positionMs}"
                    }
                }
            })
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(5000)

        jumps.clear()
        relTimeLagMs = 5000L
        delayPositionInfoMs = 5000L
        SystemClock.sleep(7000)

        assertTrue("a reading five seconds old dragged the bar back: $jumps", jumps.isEmpty())
    }

    @Test
    fun aPositionAtTheEndOfTheTrackIsNotBelievedWhileTransitioning() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedState = "TRANSITIONING"
        assertTrue("never went buffering", awaitState(Player.STATE_BUFFERING))
        reportedRelTime = "00:03:00"
        SystemClock.sleep(5000)

        val reported = onMainGet { player.currentPosition }
        assertTrue("the bar was thrown to the end of the track: $reported", reported < 60_000)
    }

    @Test
    fun aZeroTrackLengthDoesNotSilenceEveryReading() {
        relTimeCountingFrom = SystemClock.elapsedRealtime()
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(4000)

        reportedDuration = "00:00:00"
        relTimeCountingFrom = SystemClock.elapsedRealtime() - 90_000
        SystemClock.sleep(6000)

        // On the reading, not past a floor, since the bar extrapolates past any floor on its own.
        val reported = onMainGet { player.currentPosition }
        assertTrue("the bar did not land on the reading: $reported against about 90000", abs(reported - 96_000) < 6_000)
    }

    @Test
    fun goingToBufferingDoesNotStepTheBarBack() {
        val steps = CopyOnWriteArrayList<String>()
        relTimeCountingFrom = SystemClock.elapsedRealtime()
        onMain {
            player.addListener(object : Player.Listener {
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    if (oldPosition.mediaItemIndex == newPosition.mediaItemIndex &&
                        newPosition.positionMs < oldPosition.positionMs - 500
                    ) {
                        steps += "${oldPosition.positionMs} to ${newPosition.positionMs}"
                    }
                }
            })
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(4000)

        steps.clear()
        // A reading the rules refuse, or the poll writes the bar into positionMs on its way past.
        relTimeCountingFrom = null
        reportedRelTime = "00:03:00"
        reportedState = "TRANSITIONING"
        assertTrue("never went buffering", awaitState(Player.STATE_BUFFERING))
        SystemClock.sleep(1500)

        assertTrue("the bar stepped back on the way into buffering: $steps", steps.isEmpty())
    }

    @Test
    fun theLookaheadsDelayDoesNotLeaveTheBarRunningBehind() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        delayNextUriMs = 1500L
        val loadedAt = SystemClock.elapsedRealtime()
        relTimeCountingFrom = loadedAt
        onMain { player.seekToNextMediaItem() }
        SystemClock.sleep(9000)

        val expected = SystemClock.elapsedRealtime() - loadedAt
        val reported = onMainGet { player.currentPosition }
        assertTrue(
            "the bar runs behind the renderer by the lookahead: $reported against $expected",
            expected - reported < 1000
        )
    }

    @Test
    fun theBarDoesNotRunPastTheEndOfTheTrack() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        // Near the end already, or the clamp has nothing to do and this passes whatever the code does.
        onMain { player.seekTo(178_000) }
        reportedRelTime = "00:03:00"
        SystemClock.sleep(6000)

        val reported = onMainGet { player.currentPosition }
        assertTrue("the bar ran past the track length: $reported", reported <= 180_000)
    }

    @Test
    fun theRendererMovingOnByItselfReportsAsATrackFinishing() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarPastTheScrobbleRule()

        reportedTrackUri = urlAt(2)
        assertEquals("the app never followed the renderer", 1, awaitIndex(1))
        SystemClock.sleep(1500)

        assertEquals("no track change was reported", 1, reasons.size)
        assertEquals(
            "a finished track reported as something other than a track ending: $reasons",
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            reasons.first()
        )
        assertEquals("the wrong track was reported as the one that ended", listOf("track-1"), finished.toList())
    }

    @Test
    fun aRendererStoppingAtTheEndOfATrackReportsAsATrackFinishing() {
        refuseNextUriCode = 602
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedState = "STOPPED"
        SystemClock.sleep(3000)
        assertTrue("a renderer that refused the next track was waited on: $handovers", handovers.contains(urlAt(2)))
        assertEquals("the app never advanced off the stopped renderer", 1, awaitIndex(1))
        // Playing again, or the reads after the advance count toward a track that never started.
        reportedState = "PLAYING"
        SystemClock.sleep(1500)

        assertEquals("no track change was reported", 1, reasons.size)
        assertEquals(
            "a finished track reported as something other than a track ending: $reasons",
            Player.DISCONTINUITY_REASON_AUTO_TRANSITION,
            reasons.first()
        )
        assertEquals("the wrong track was reported as the one that ended", listOf("track-1"), finished.toList())
        assertTrue("the stop did not hand the next track over: $handovers", handovers.contains(urlAt(2)))
    }

    @Test
    fun aRendererStoppedEarlyDoesNotReportATrackFinishing() {
        // Refused, so an advance would load the next track at once instead of waiting it out.
        refuseNextUriCode = 602
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(2500)

        reportedState = "STOPPED"
        assertTrue("the stop was taken as the track ending", awaitState(Player.STATE_IDLE))
        SystemClock.sleep(2500)

        assertEquals("the queue moved off the stopped track", 0, onMainGet { player.currentMediaItemIndex })
        assertEquals("the next track was handed over: $handovers", listOf(urlAt(1)), handovers)
        assertEquals("a track stopped five seconds in was reported: $reasons $finished", emptyList<Int>(), reasons.toList())
        assertNull("the stop ended in an error", onMainGet { player.playerError })
    }

    @Test
    fun theRendererBeingSkippedOnDoesNotReportATrackFinishing() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedTrackUri = urlAt(2)
        assertEquals("the app never followed the renderer", 1, awaitIndex(1))
        SystemClock.sleep(1500)

        assertEquals("the track change was never reported at all: $reasons", 1, reasons.size)
        assertEquals(
            "a track skipped five seconds in was reported as finished: $reasons $finished",
            emptyList<String>(),
            finished.toList()
        )
    }

    @Test
    fun aRendererStoppedEarlyOnTheLastTrackDoesNotEndTheQueue() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        // A poll seeing it play first, or the stop is a renderer that never started.
        SystemClock.sleep(2500)

        reportedState = "STOPPED"
        assertTrue("the last track's stop was never read", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })
        assertEquals(
            "a track stopped five seconds in ended the queue instead of stopping",
            Player.STATE_IDLE,
            onMainGet { player.playbackState }
        )
    }

    @Test
    fun aZeroPositionBeforeTheStopKeepsTheTrackEnding() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        // Zero while the renderer still says it is playing, which is a reading the poll believes.
        reportedRelTime = "00:00:00"
        SystemClock.sleep(3000)
        reportedState = "STOPPED"
        assertEquals("the app never advanced off the stopped renderer", 1, awaitIndex(1))
        reportedState = "PLAYING"
        SystemClock.sleep(1500)

        assertEquals(
            "a finished track was lost to a zero reading: $reasons $finished",
            listOf("track-1"),
            finished.toList()
        )
    }

    @Test
    fun aServerWithNoLengthStillReportsATrackFinishing() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1, 0), itemAt(2, 0), itemAt(3, 0)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedState = "STOPPED"
        assertEquals("the app never advanced off the stopped renderer", 1, awaitIndex(1))
        reportedState = "PLAYING"
        SystemClock.sleep(1500)

        assertEquals(
            "a finished track went unreported because the server gave no length: $reasons $finished",
            listOf("track-1"),
            finished.toList()
        )
    }

    @Test
    fun aRendererReportingNoLengthStillReportsATrackFinishing() {
        reportedDuration = "00:00:00"
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedState = "STOPPED"
        assertEquals("the app never advanced off the stopped renderer", 1, awaitIndex(1))
        reportedState = "PLAYING"
        SystemClock.sleep(1500)

        assertEquals(
            "a finished track went unreported because the renderer gave no length: $reasons $finished",
            listOf("track-1"),
            finished.toList()
        )
    }

    @Test
    fun aShortTrackPlayedToTheEndReportsATrackFinishing() {
        reportedDuration = "00:00:20"
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1, 20), itemAt(2, 20)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedRelTime = "00:00:19"
        SystemClock.sleep(5000)
        reportedState = "STOPPED"
        assertEquals("the app never advanced off the stopped renderer", 1, awaitIndex(1))
        reportedState = "PLAYING"
        SystemClock.sleep(1500)

        assertEquals(
            "a short track played end to end was not reported as finished: $reasons $finished",
            listOf("track-1"),
            finished.toList()
        )
    }

    @Test
    fun aHandoverToALongerTrackStillReportsTheFinishedTrackAsFinished() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2, 600)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarPastTheScrobbleRule()

        // The renderer has moved on, and this reply describes the ten minute track it now holds.
        reportedDuration = "00:10:00"
        reportedTrackUri = urlAt(2)
        assertEquals("the app never followed the renderer", 1, awaitIndex(1))
        SystemClock.sleep(1500)

        assertEquals(
            "the finished track was judged by the next one's length: $reasons $finished",
            listOf("track-1"),
            finished.toList()
        )
    }

    @Test
    fun aScrubForwardAndBackDoesNotCountAsPlayed() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedRelTime = "00:02:55"
        val polledBefore = actions.count { it == "GetPositionInfo" }
        onMain { player.seekTo(175_000) }
        // A poll has to see the peak, or removing the reset below changes nothing here.
        SystemClock.sleep(2500)
        assertTrue(
            "no poll ran while the bar was at the peak, so this proves nothing",
            actions.count { it == "GetPositionInfo" } > polledBefore
        )
        reportedRelTime = "00:00:05"
        onMain { player.seekTo(5_000) }
        SystemClock.sleep(2500)
        reportedState = "STOPPED"
        assertTrue("the peak made the stop read as the track ending", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })

        assertEquals(
            "a scrub counted as playback: $reasons $finished",
            emptyList<String>(),
            finished.toList()
        )
    }

    @Test
    fun aStaleReadingAfterAScrubDoesNotCountAsPlayed() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedRelTime = "00:02:55"
        onMain { player.seekTo(175_000) }
        SystemClock.sleep(2500)

        // The first poll after the scrub back answers from before it.
        val sentBefore = actions.size
        onMain { player.seekTo(10_000) }
        awaitPositionPollAfterASeekFrom(sentBefore)
        reportedRelTime = "00:00:10"
        SystemClock.sleep(3500)

        reportedState = "STOPPED"
        assertTrue("the stale reading made the stop read as the track ending", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })

        assertEquals(
            "a reading from before the scrub counted as playback: $reasons $finished",
            emptyList<String>(),
            finished.toList()
        )
    }

    @Test
    fun aRefusedSeekDoesNotCountAsPlayed() {
        val reasons = CopyOnWriteArrayList<Int>()
        val finished = CopyOnWriteArrayList<String>()
        onMain {
            recordTrackChanges(reasons, finished)
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        refuseSeekCode = 711
        onMain { player.seekTo(175_000) }
        SystemClock.sleep(3000)
        reportedState = "STOPPED"
        assertTrue("the refused seek made the stop read as the track ending", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })

        assertEquals(
            "a refused seek counted as playback: $reasons $finished",
            emptyList<String>(),
            finished.toList()
        )
    }

    @Test
    fun aSeekWhileTheRendererIsStartingDoesNotHandItOverAgain() {
        stoppedPollsAfterHandover = 2
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2)), 0, 0L)
            player.playWhenReady = true
        }
        // Long enough for the first poll to read the stop, and short of the renderer starting.
        SystemClock.sleep(800)
        val handedOver = handovers.size

        onMain { player.seekTo(30_000) }
        SystemClock.sleep(1500)

        assertEquals(
            "the renderer was handed the track again while it was still starting: $handovers",
            handedOver,
            handovers.size
        )
    }

    @Test
    fun playingAgainAfterTheQueueEndedHandsTheTrackOverAgain() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedState = "STOPPED"
        assertTrue("the queue never ended", awaitState(Player.STATE_ENDED))
        reportedState = "PLAYING"
        val before = handovers.size

        // What the play button does on an ended player, a seek to the start and a play, no prepare.
        onMain {
            player.seekToDefaultPosition()
            player.play()
        }
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (handovers.size == before && SystemClock.elapsedRealtime() < deadline) {
            SystemClock.sleep(100)
        }

        assertEquals("the renderer was never handed the track again", before + 1, handovers.size)
    }

    @Test
    fun aRendererFinishingTheLastTrackEndsTheQueue() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedState = "STOPPED"
        assertTrue("the queue never ended", awaitState(Player.STATE_ENDED))
    }

    @Test
    fun aStoppedRendererStillNamingTheFinishedTrackDoesNotSendTheQueueBack() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedTrackUri = urlAt(1)
        reportedState = "STOPPED"
        // Past the waited out polls and the next track's load.
        SystemClock.sleep(12_000)

        assertEquals("the queue went back to the finished track", 1, onMainGet { player.currentMediaItemIndex })
        assertTrue("the next track was never handed over: $handovers", handovers.contains(urlAt(2)))
        assertFalse("the finished track was handed over again: $handovers", handovers.count { it == urlAt(1) } > 1)
    }

    @Test
    fun aReleaseDropsASeekStillWaitingOnTheRenderer() {
        stoppedPollsAfterHandover = 100
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 60_000L)
            player.playWhenReady = true
        }
        SystemClock.sleep(1000)
        assertFalse("the seek did not wait on the renderer: $actions", actions.contains("Seek"))
        onMain { player.release() }
        SystemClock.sleep(6000)

        assertFalse("a seek went out after the release: $actions", actions.contains("Seek"))
        // tearDown releases the player again, so it gets a fresh one.
        onMain { player = UpnpPlayer(controlPoint(), device, Looper.getMainLooper()) }
    }

    @Test
    fun aStopAtTheEndWaitsForARendererHoldingTheNextTrack() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        runTheBarToTheEndOfTheTrack()

        reportedState = "STOPPED"
        SystemClock.sleep(3000)
        assertEquals("a load went out while the renderer was starting its next track: $handovers", listOf(urlAt(1)), handovers)
        assertEquals("the queue moved before the renderer did", 0, onMainGet { player.currentMediaItemIndex })

        reportedTrackUri = urlAt(2)
        reportedState = "PLAYING"
        assertEquals("the app never followed the renderer onto the next track", 1, awaitIndex(1))
        assertEquals("the next track was handed over although the renderer had it: $handovers", listOf(urlAt(1)), handovers)
    }

    @Test
    fun aSeekBeforeTheRendererHasStartedWaitsForItToPlay() {
        stoppedPollsAfterHandover = 3
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        SystemClock.sleep(800)
        assertEquals("the track was not handed over before the scrub: $handovers", 1, handovers.size)
        onMain { player.seekTo(60_000L) }
        SystemClock.sleep(3000)

        val sent = actions.toList()
        val seek = sent.indexOf("Seek")
        assertTrue("no seek went out: $sent", seek >= 0)
        val readsBefore = sent.subList(0, seek).count { it == "GetTransportInfo" }
        assertTrue("the seek went out before the renderer reported playing: $sent", readsBefore > 3)
    }

    @Test
    fun aMoveToAnotherTrackAtAPositionWaitsForTheRendererToPlay() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(2500)

        stoppedPollsAfterHandover = 3
        actions.clear()
        onMain { player.seekTo(1, 60_000L) }
        SystemClock.sleep(3000)

        val sent = actions.toList()
        val handover = sent.indexOf("SetAVTransportURI")
        val seek = sent.indexOf("Seek")
        assertTrue("the move was never handed over: $sent", handover >= 0)
        assertTrue("no seek went out: $sent", seek > handover)
        val readsBetween = sent.subList(handover, seek).count { it == "GetTransportInfo" }
        assertTrue("the seek went out before the renderer reported playing: $sent", readsBetween > 3)
    }

    @Test
    fun aStopTwentySecondsShortOfTheEndLeavesTheQueueOnItsTrack() {
        // Refused, so an advance would load the next track at once instead of waiting it out.
        refuseNextUriCode = 602
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        reportedRelTime = "00:02:40"
        SystemClock.sleep(5000)

        reportedState = "STOPPED"
        assertTrue("a stop twenty seconds short of the end was taken as the track ending", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })
        assertEquals("the queue moved off the stopped track", 0, onMainGet { player.currentMediaItemIndex })
    }

    @Test
    fun aRendererLengthShorterThanTheServersStillEndsTheTrack() {
        refuseNextUriCode = 602
        reportedDuration = "00:02:40"
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        reportedRelTime = "00:02:35"
        SystemClock.sleep(5000)

        reportedState = "STOPPED"
        assertEquals("a track run to the renderer's length was taken as stopped from outside", 1, awaitIndex(1))
    }

    @Test
    fun aStopFromOutsideKeepsThePositionItStoppedAt() {
        refuseNextUriCode = 602
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(3)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))
        reportedRelTime = "00:01:30"
        SystemClock.sleep(5000)

        reportedRelTime = "00:00:00"
        reportedState = "STOPPED"
        assertTrue("the outside stop was never read", awaitState(Player.STATE_IDLE))
        assertNull("the stop ended in an error", onMainGet { player.playerError })
        val kept = onMainGet { player.currentPosition }
        assertTrue("the position went back to the stopped renderer's zero: $kept", kept >= 85_000)
        assertTrue("the position ran past where the bar stopped: $kept", kept < 95_000)
    }

    @Test
    fun aSongQueuedTwiceDoesNotSendTheQueueBackwards() {
        onMain {
            player.setMediaItems(listOf(itemAt(1), itemAt(2), itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never reached ready", awaitState(Player.STATE_READY))

        reportedTrackUri = urlAt(2)
        assertEquals("the app never followed the renderer off the first track", 1, awaitIndex(1))

        reportedTrackUri = urlAt(1)
        assertEquals("the queue went back to the first copy of the song", 2, awaitIndex(2))
    }

    private fun streamUrlAsking(formats: Map<String, String>): (MediaItem) -> String? = { item ->
        item.localConfiguration?.uri?.toString()?.let { url ->
            formats[item.mediaId]?.let { "$url&format=$it" } ?: url
        }
    }

    @Test
    fun theTrackFormatIsWhatTheServerWasAskedFor() {
        onMain {
            player.release()
            player = UpnpPlayer(
                controlPoint(), device, Looper.getMainLooper(),
                streamUrl = streamUrlAsking(
                    mapOf(
                        "track-1" to "opus", "track-2" to "raw", "track-4" to "aac",
                        "track-5" to "raw", "track-6" to "", "track-7" to "mp3"
                    )
                )
            )
            player.setMediaItems(
                listOf(
                    itemWith(1, "flac"),
                    itemWith(2, "flac"),
                    itemWith(3, "m4a"),
                    itemWith(4, "mp3", type = Constants.MEDIA_TYPE_PODCAST),
                    // A download transcoded to opus, while the renderer plays the server's flac named by the path.
                    itemWith(5, "opus", path = "Artist/Album.Deluxe/05 track.flac"),
                    // Empty values would otherwise publish the mime "audio/".
                    itemWith(6, ""),
                    // The one format whose mime is not audio/ plus its name, which media3 normalizes.
                    itemWith(7, "flac")
                ),
                0, 0L
            )
            player.playWhenReady = true
        }

        assertEquals("audio/opus", awaitMime("audio/opus"))
        onMain { player.seekToNextMediaItem() }
        assertEquals("audio/flac", awaitMime("audio/flac"))
        onMain { player.seekToNextMediaItem() }
        assertEquals("audio/m4a", awaitMime("audio/m4a"))
        onMain { player.seekToNextMediaItem() }
        assertEquals("audio/aac", awaitMime("audio/aac"))
        onMain { player.seekToNextMediaItem() }
        assertEquals("audio/flac", awaitMime("audio/flac"))
        assertEquals(
            "the published format does not say a renderer plays",
            UpnpPlayer.FORMAT_ID,
            onMainGet { player.currentTracks.groups.first().getTrackFormat(0).id }
        )
        onMain { player.seekToNextMediaItem() }
        assertEquals("audio/x-unknown", awaitMime("audio/x-unknown"))
        onMain { player.seekToNextMediaItem() }
        assertEquals("audio/mpeg", awaitMime("audio/mpeg"))
    }

    @Test
    fun aControllerSeesThePublishedFormat() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Its own stream URL, or the format rides on whatever transcode preference this device has saved.
        onMain {
            player.release()
            player = UpnpPlayer(controlPoint(), device, Looper.getMainLooper(), streamUrl = streamUrlAsking(emptyMap()))
        }
        val session = onMainGet { MediaSession.Builder(context, player).setId("upnp-tracks-test").build() }
        try {
            val controller = MediaController.Builder(context, session.token).buildAsync().get(10, TimeUnit.SECONDS)
            try {
                onMain { player.setMediaItems(listOf(itemWith(1, "flac")), 0, 0L) }

                val deadline = SystemClock.elapsedRealtime() + 10_000
                var seen: Pair<String?, String?>? = null
                while (SystemClock.elapsedRealtime() < deadline) {
                    seen = onMainGet {
                        controller.currentTracks.groups.firstOrNull()?.getTrackFormat(0)?.let { it.sampleMimeType to it.id }
                    }
                    if (seen?.first == "audio/flac") break
                    SystemClock.sleep(100)
                }
                assertEquals("the controller never saw the published format", "audio/flac", seen?.first)
                assertEquals("the format the controller sees carries no id", UpnpPlayer.FORMAT_ID, seen?.second)
            } finally {
                onMain { controller.release() }
            }
        } finally {
            onMain { session.release() }
        }
    }

    @Test
    fun anOpaqueStationAddressDoesNotBringDownTheApp() {
        val station = MediaItem.Builder()
            .setMediaId("radio-1")
            .setUri("radio.example:8000/live?format=pls")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setExtras(Bundle().apply { putString("type", Constants.MEDIA_TYPE_RADIO) })
                    .build()
            )
            .build()

        onMain { player.setMediaItems(listOf(station), 0, 0L) }

        assertEquals("audio/x-unknown", awaitMime("audio/x-unknown"))
    }

    private fun awaitMime(wanted: String): String? {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        var seen: String? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            seen = onMainGet {
                player.currentTracks.groups.firstOrNull()?.getTrackFormat(0)?.sampleMimeType
            }
            if (seen == wanted) return seen
            SystemClock.sleep(100)
        }
        return seen
    }

    @Test
    fun aPauseFromTheRenderersOwnRemoteShowsInTheApp() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never ready", awaitState(Player.STATE_READY))
        reportedRelTime = "00:00:20"
        val from = actions.size
        reportedState = "PAUSED_PLAYBACK"

        assertTrue("the app still shows playing on a paused renderer", awaitPlayWhenReady(false))
        val sent = actions.toList().subList(from, actions.size)
        assertFalse("the app sent a command of its own: $sent", sent.any { it == "Pause" || it == "Stop" || it == "Play" })
        SystemClock.sleep(2500)
        val bar = onMainGet { player.currentPosition }
        SystemClock.sleep(2500)
        assertEquals("the bar moved on a paused renderer", bar, onMainGet { player.currentPosition })
        assertEquals("the bar is not where the renderer paused", 20_000L, bar)
    }

    @Test
    fun aPlayFromTheRenderersOwnRemoteShowsInTheAppAndSendsWhatWaited() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never ready", awaitState(Player.STATE_READY))
        reportedState = "PAUSED_PLAYBACK"
        onMain { player.playWhenReady = false }
        assertTrue("the app never paused", awaitPlayWhenReady(false))
        onMain {
            player.seekTo(60_000L)
            player.addMediaItem(itemAt(2))
        }
        SystemClock.sleep(3000)
        val from = actions.size
        reportedState = "PLAYING"

        assertTrue("the app still shows paused on a playing renderer", awaitPlayWhenReady(true))
        SystemClock.sleep(500)
        val sent = actions.toList().subList(from, actions.size)
        assertFalse("the app sent a Play of its own: $sent", sent.contains("Play"))
        assertTrue("the seek made while paused never reached the renderer: $sent", sent.contains("Seek"))
        assertTrue("the track added while paused was never sent as next: $sent", sent.contains("SetNextAVTransportURI"))
    }

    @Test
    fun oneReadingOfTheOtherStateIsNotFollowed() {
        onMain {
            player.setMediaItems(listOf(itemAt(1)), 0, 0L)
            player.playWhenReady = true
        }
        assertTrue("never ready", awaitState(Player.STATE_READY))
        SystemClock.sleep(2500)
        // Every change is kept, since a flip the next reading undoes leaves the end state looking right.
        val changes = CopyOnWriteArrayList<Boolean>()
        onMain {
            player.addListener(object : Player.Listener {
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    changes += playWhenReady
                }
            })
        }
        pausedPolls.set(1)
        SystemClock.sleep(5000)

        assertEquals("the paused reading was never served, so this proves nothing", 0, pausedPolls.get())
        assertTrue("one paused reading changed the app's play state: $changes", changes.isEmpty())
    }

    private fun awaitPlayWhenReady(wanted: Boolean): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMainGet { player.playWhenReady } == wanted) return true
            SystemClock.sleep(100)
        }
        return false
    }

    /** Records the reason for each track change and the id every ending names. Main thread only. */
    private fun recordTrackChanges(reasons: MutableList<Int>, finished: MutableList<String>) {
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // An ending on an index that did not move is kept, since that is a misattached declaration.
                val moved = oldPosition.mediaItemIndex != newPosition.mediaItemIndex
                if (!moved && reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) return
                reasons += reason
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    oldPosition.mediaItem?.mediaId?.let { finished += it }
                }
            }
        })
    }

    private fun runTheBarPastTheScrobbleRule() {
        reportedRelTime = "00:02:30"
        SystemClock.sleep(5000)
        val bar = onMainGet { player.currentPosition }
        assertTrue("the bar never cleared half the track, so this proves nothing: $bar", bar >= 90_000)
    }

    private fun runTheBarToTheEndOfTheTrack() {
        reportedRelTime = "00:02:55"
        SystemClock.sleep(5000)
        val bar = onMainGet { player.currentPosition }
        assertTrue("the bar never reached the end of the track, so this proves nothing: $bar", bar >= 170_000)
    }

    /** Returns instead of failing, so a test depending on the state has to assert on the answer. */
    private fun awaitState(wanted: Int): Boolean {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMainGet { player.playbackState } == wanted) return true
            SystemClock.sleep(100)
        }
        return false
    }

    private fun awaitPositionPollAfterASeekFrom(from: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            val sent = actions.toList()
            val seek = sent.subList(from, sent.size).indexOf("Seek").let { if (it < 0) -1 else from + it }
            if (seek >= 0 && sent.subList(seek, sent.size).contains("GetPositionInfo")) {
                // The fake builds its answer after recording the call, so let that answer go first.
                SystemClock.sleep(200)
                return
            }
            SystemClock.sleep(20)
        }
        throw AssertionError("no position poll followed the scrub: $actions")
    }

    private fun awaitIndex(wanted: Int): Int {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            val now = onMainGet { player.currentMediaItemIndex }
            if (now == wanted) return now
            SystemClock.sleep(250)
        }
        return onMainGet { player.currentMediaItemIndex }
    }

    private fun awaitError(timeoutMillis: Long = 10_000): androidx.media3.common.PlaybackException? {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            onMainGet { player.playerError }?.let { return it }
            SystemClock.sleep(200)
        }
        return null
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> onMainGet(block: () -> T): T {
        var out: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { out = block() }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }
}
