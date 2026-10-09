package com.eddyizm.tempus.upnp

import android.os.Looper
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.DeviceInfo
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The renderer is faked by an HTTP client, and its events are real NOTIFY requests sent over loopback. */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class UpnpPlayerVolumeTest {

    // Loopback, since the listener only takes events from the renderer's own address.
    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0"><device>
          <friendlyName>Fake Renderer</friendlyName>
          <UDN>uuid:11111111-2222-3333-4444-666666666666</UDN>
          <serviceList>
            <service><serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
              <controlURL>/avt/control</controlURL><eventSubURL>/avt/event</eventSubURL><SCPDURL>/avt/scpd.xml</SCPDURL></service>
            <service><serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
              <controlURL>/rc/control</controlURL><eventSubURL>/rc/event</eventSubURL><SCPDURL>/rc/scpd.xml</SCPDURL></service>
          </serviceList>
        </device></root>
    """.trimIndent()

    private val scpd = """<?xml version="1.0"?><scpd xmlns="urn:schemas-upnp-org:service-1-0"><serviceStateTable>
        <stateVariable sendEvents="no"><name>Volume</name><dataType>ui2</dataType>
        <allowedValueRange><minimum>0</minimum><maximum>60</maximum><step>1</step></allowedValueRange></stateVariable>
        </serviceStateTable></scpd>"""

    private class Sent(val method: String, val action: String, val sid: String?, val callback: String?, val body: String)

    private val sent = CopyOnWriteArrayList<Sent>()
    private val subscriptions = AtomicInteger()
    @Volatile private var grantSeconds = 300
    @Volatile private var refuseRenewals = false
    @Volatile private var setVolumeDelayMs = 0L
    private val subscriptionsToRefuse = AtomicInteger()
    private val setVolumesToRefuse = AtomicInteger()

    private lateinit var player: UpnpPlayer

    private fun controlPoint() = UpnpControlPoint(
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                val action = request.header("SOAPAction").orEmpty().substringAfterLast('#').trim('"')
                val body = Buffer().also { request.body?.writeTo(it) }.readUtf8()
                sent += Sent(request.method, action, request.header("SID"), request.header("CALLBACK"), body)
                if (action == "SetVolume") SystemClock.sleep(setVolumeDelayMs)
                answer(request, action)
            })
            .build()
    )

    private fun answer(request: Request, action: String): Response {
        val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
        return when {
            request.method == "SUBSCRIBE" && request.header("SID") == null && subscriptionsToRefuse.getAndDecrement() > 0 ->
                response.code(503).message("Service Unavailable").body("".toResponseBody(null))
            request.method == "SUBSCRIBE" && request.header("SID") != null && refuseRenewals ->
                response.code(412).message("Precondition Failed").body("".toResponseBody(null))
            request.method == "SUBSCRIBE" -> response
                .header("SID", request.header("SID") ?: "uuid:sub-${subscriptions.incrementAndGet()}")
                .header("TIMEOUT", "Second-$grantSeconds")
                .body("".toResponseBody(null))
            action == "SetVolume" && setVolumesToRefuse.getAndDecrement() > 0 ->
                response.code(500).message("Internal Server Error").body("".toResponseBody(null))
            request.url.encodedPath == "/rc/scpd.xml" -> response.body(scpd.toResponseBody(XML))
            else -> response.body(
                ("""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">""" +
                    """<s:Body><u:${action}Response xmlns:u="urn:x"/></s:Body></s:Envelope>""").toResponseBody(XML)
            )
        }.build()
    }

    @Before
    fun setUp() {
        sent.clear()
        subscriptions.set(0)
        grantSeconds = 300
        refuseRenewals = false
        setVolumeDelayMs = 0L
        subscriptionsToRefuse.set(0)
        setVolumesToRefuse.set(0)
    }

    @After
    fun tearDown() {
        if (::player.isInitialized) onMain { player.release() }
    }

    private fun start() {
        val device = UpnpDevice.parseDescription(description, "http://127.0.0.1:1549/").single()
        onMain { player = UpnpPlayer(controlPoint(), device, Looper.getMainLooper()) }
    }

    @Test
    fun theVolumeIsOfferedOnlyOnceTheRendererHasReportedALevel() {
        start()
        val callback = awaitSubscription(1)

        assertFalse(onMainGet { player.isCommandAvailable(Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS) })
        assertEquals(DeviceInfo.PLAYBACK_TYPE_LOCAL, onMainGet { player.deviceInfo.playbackType })

        notify(callback, 18)
        awaitVolume(18)

        assertTrue(onMainGet { player.isCommandAvailable(Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS) })
        assertEquals(DeviceInfo.PLAYBACK_TYPE_REMOTE, onMainGet { player.deviceInfo.playbackType })
        assertEquals("the maximum did not come from the renderer's own range", 60, onMainGet { player.deviceInfo.maxVolume })
    }

    @Test
    fun aStepIsTakenFromTheLevelTheRendererSettledOnNotTheEchoBeforeIt() {
        start()
        val callback = awaitSubscription(1)

        // An LG C1's real level came about 0.3 s after an echo of the last level it was sent.
        notify(callback, 0)
        SystemClock.sleep(300)
        assertFalse(
            "a level was offered before the renderer settled",
            onMainGet { player.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME) }
        )
        notify(callback, 18)
        awaitVolume(18)
        onMain { player.increaseDeviceVolume(0) }

        assertEquals(listOf(19), awaitSetVolumes(1))
    }

    @Test
    fun aKeyHeldDownSendsTheNewestLevelInsteadOfEveryStep() {
        start()
        val callback = awaitSubscription(1)
        notify(callback, 10)
        awaitVolume(10)
        setVolumeDelayMs = 1000

        repeat(10) { onMain { player.increaseDeviceVolume(0) } }
        SystemClock.sleep(2500)

        val levels = setVolumes()
        assertTrue("every step went to the renderer: $levels", levels.size <= 2)
        assertEquals(20, levels.last())
        assertEquals(20, onMainGet { player.deviceVolume })
    }

    @Test
    fun aRefusedRenewalSubscribesAgain() {
        // Twenty seconds is renewed after ten, the shortest wait.
        grantSeconds = 20
        refuseRenewals = true
        start()
        awaitSubscription(1)

        awaitSubscription(2, timeoutMillis = 15_000)
        assertTrue("no renewal was asked for first", sent.any { it.method == "SUBSCRIBE" && it.sid == "uuid:sub-1" })
    }

    @Test
    fun aRenewalTheRendererTakesIsRenewedAgain() {
        grantSeconds = 20
        start()
        awaitSubscription(1)

        fun renewals() = sent.count { it.method == "SUBSCRIBE" && it.sid == "uuid:sub-1" }
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (renewals() < 2 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        assertTrue("renewed ${renewals()} times", renewals() >= 2)
    }

    @Test
    fun aRefusedFirstSubscriptionIsTriedAgain() {
        subscriptionsToRefuse.set(1)
        start()
        awaitSubscription(1)

        val callback = awaitSubscription(2, timeoutMillis = 40_000)
        notify(callback, 18)
        awaitVolume(18)
    }

    @Test
    fun aLevelSetDirectlyIsSentAndNoLevelLeavesTheRenderersRange() {
        start()
        val callback = awaitSubscription(1)
        notify(callback, 59)
        awaitVolume(59)

        onMain { player.setDeviceVolume(25, 0) }
        assertEquals(listOf(25), awaitSetVolumes(1))
        onMain { player.setDeviceVolume(80, 0) }
        assertEquals(listOf(25, 60), awaitSetVolumes(2))
        onMain { player.increaseDeviceVolume(0) }
        assertEquals(listOf(25, 60, 60), awaitSetVolumes(3))
        onMain { player.setDeviceVolume(0, 0) }
        assertEquals(listOf(25, 60, 60, 0), awaitSetVolumes(4))
        onMain { player.decreaseDeviceVolume(0) }
        assertEquals(listOf(25, 60, 60, 0, 0), awaitSetVolumes(5))
    }

    @Test
    fun aRefusedLevelGoesBackToTheLevelTheRendererReported() {
        start()
        val callback = awaitSubscription(1)
        notify(callback, 30)
        awaitVolume(30)
        setVolumesToRefuse.set(1)

        onMain { player.increaseDeviceVolume(0) }

        assertEquals(listOf(31), awaitSetVolumes(1))
        awaitVolume(30)
    }

    @Test
    fun aRefusedLevelDoesNotUndoANewerOne() {
        start()
        val callback = awaitSubscription(1)
        notify(callback, 30)
        awaitVolume(30)
        setVolumesToRefuse.set(1)
        setVolumeDelayMs = 1000

        onMain { player.increaseDeviceVolume(0) }
        SystemClock.sleep(200)
        onMain { player.increaseDeviceVolume(0) }

        assertEquals(listOf(31, 32), awaitSetVolumes(2))
        SystemClock.sleep(2000)
        assertEquals(32, onMainGet { player.deviceVolume })
    }

    @Test
    fun releasingThePlayerEndsTheSubscription() {
        start()
        val callback = URI(awaitSubscription(1))

        onMain { player.release() }
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (sent.none { it.method == "UNSUBSCRIBE" } && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)

        assertEquals("uuid:sub-1", sent.firstOrNull { it.method == "UNSUBSCRIBE" }?.sid)
        while (runCatching { Socket(callback.host, callback.port).close() }.isSuccess) {
            if (SystemClock.elapsedRealtime() > deadline) throw AssertionError("the event listener is still open")
            SystemClock.sleep(50)
        }
    }

    /** The callback URL of the [count]th new subscription asked for, refused ones included. */
    private fun awaitSubscription(count: Int, timeoutMillis: Long = 5000): String {
        val deadline = SystemClock.elapsedRealtime() + timeoutMillis
        while (SystemClock.elapsedRealtime() < deadline) {
            val made = sent.filter { it.method == "SUBSCRIBE" && it.callback != null }
            if (made.size >= count) return made[count - 1].callback!!.trim('<', '>')
            SystemClock.sleep(50)
        }
        throw AssertionError("subscription $count never came: ${sent.map { "${it.method} ${it.action}" }}")
    }

    private fun awaitVolume(level: Int) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (onMainGet { player.isCommandAvailable(Player.COMMAND_GET_DEVICE_VOLUME) && player.deviceVolume == level }) return
            SystemClock.sleep(50)
        }
        throw AssertionError("the player never settled on $level")
    }

    private fun setVolumes(): List<Int> = sent.filter { it.action == "SetVolume" }
        .map { it.body.substringAfter("<DesiredVolume>").substringBefore("</DesiredVolume>").toInt() }

    private fun awaitSetVolumes(count: Int): List<Int> {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (setVolumes().size < count && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
        return setVolumes()
    }

    private fun notify(callbackUrl: String, level: Int) {
        val lastChange = UpnpControlPoint.escape(
            """<Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/"><InstanceID val="0">""" +
                """<Volume channel="Master" val="$level"/></InstanceID></Event>"""
        )
        val body = ("""<?xml version="1.0"?><e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0">""" +
            "<e:property><LastChange>$lastChange</LastChange></e:property></e:propertyset>").toByteArray()
        val uri = URI(callbackUrl)
        Socket(uri.host, uri.port).use { socket ->
            socket.getOutputStream().write(
                ("NOTIFY / HTTP/1.1\r\nHOST: ${uri.host}:${uri.port}\r\nNT: upnp:event\r\nNTS: upnp:propchange\r\n" +
                    "SID: uuid:sub-1\r\nContent-Length: ${body.size}\r\n\r\n").toByteArray() + body
            )
            socket.getInputStream().bufferedReader().readLine()
        }
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> onMainGet(block: () -> T): T {
        var out: T? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { out = block() }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    companion object {
        private val XML = "text/xml".toMediaType()
    }
}
