package com.eddyizm.tempus.upnp

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class UpnpVolumeTest {

    private fun event(lastChange: String) =
        """<?xml version="1.0"?><e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"><e:property>""" +
            "<LastChange>${UpnpControlPoint.escape(lastChange)}</LastChange></e:property></e:propertyset>"

    private val levels = """<Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/"><InstanceID val="0">""" +
        """<Volume channel="LF" val="40"/><Volume channel="Master" val="18"/><Mute channel="Master" val="0"/>""" +
        "</InstanceID></Event>"

    @Test
    fun theMasterLevelIsReadOutOfTheEscapedLastChange() {
        assertEquals(18, UpnpControlPoint.masterVolumeOf(event(levels)))
    }

    @Test
    fun theLgC1sEventIsRead() {
        val c1 = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
            """<e:propertyset xmlns:e="urn:schemas-upnp-org:event-1-0"><e:property><LastChange>""" +
            """&lt;Event xmlns="urn:schemas-upnp-org:metadata-1-0/RCS/"&gt;&lt;InstanceID val="0"&gt;""" +
            """&lt;PresetNameList val="FactoryDefaults"/&gt;&lt;X_Subtitle val="UNKNOWN"/&gt;""" +
            """&lt;X_Current3DFormatter val="UNKNOWN"/&gt;&lt;X_Possible3DFormatter val="UNKNOWN"/&gt;""" +
            """&lt;Mute Channel="Master" val="0"/&gt;&lt;Volume Channel="Master" val="9"/&gt;""" +
            """&lt;VolumeDB Channel="Master" val="9"/&gt;&lt;/InstanceID&gt;&lt;/Event&gt;""" +
            "</LastChange></e:property></e:propertyset>"

        assertEquals(9, UpnpControlPoint.masterVolumeOf(c1))
    }

    @Test
    fun anEventWithNoMasterVolumeGivesNoLevel() {
        val muteOnly = """<Event><InstanceID val="0"><Mute channel="Master" val="1"/></InstanceID></Event>"""

        assertNull(UpnpControlPoint.masterVolumeOf(event(muteOnly)))
    }

    @Test
    fun theMaximumComesFromTheVolumeVariableAndNoOther() {
        val scpd = """<?xml version="1.0"?><scpd xmlns="urn:schemas-upnp-org:service-1-0"><serviceStateTable>
            <stateVariable sendEvents="no"><name>VolumeDB</name><dataType>i2</dataType>
              <allowedValueRange><minimum>-32767</minimum><maximum>0</maximum></allowedValueRange></stateVariable>
            <stateVariable sendEvents="no"><name>Volume</name><dataType>ui2</dataType>
              <allowedValueRange><minimum>0</minimum><maximum>60</maximum><step>1</step></allowedValueRange></stateVariable>
            </serviceStateTable></scpd>"""

        assertEquals(60, UpnpControlPoint.volumeMaxOf(scpd))
        assertNull(UpnpControlPoint.volumeMaxOf(scpd.replace("<maximum>60</maximum>", "")))
        assertNull(UpnpControlPoint.volumeMaxOf(scpd.replace("<maximum>60</maximum>", "<maximum>0</maximum>")))
    }

    @Test
    fun eachServiceKeepsItsEventAndDescriptionUrls() {
        val description = """<?xml version="1.0"?><root xmlns="urn:schemas-upnp-org:device-1-0"><device>
            <friendlyName>Renderer</friendlyName><serviceList>
            <service><serviceType>${UpnpDevice.AV_TRANSPORT}</serviceType><controlURL>/avt/control</controlURL>
              <eventSubURL>/avt/event</eventSubURL><SCPDURL>/avt/scpd.xml</SCPDURL></service>
            <service><serviceType>${UpnpDevice.RENDERING_CONTROL}</serviceType><controlURL>/rc/control</controlURL>
              <eventSubURL>/rc/event</eventSubURL><SCPDURL>/rc/scpd.xml</SCPDURL></service>
            </serviceList></device></root>"""

        val device = UpnpDevice.parseDescription(description, "http://192.0.2.10:1549/").single()

        assertEquals("http://192.0.2.10:1549/rc/event", device.eventUrls[UpnpDevice.RENDERING_CONTROL])
        assertEquals("http://192.0.2.10:1549/rc/scpd.xml", device.scpdUrls[UpnpDevice.RENDERING_CONTROL])
    }

    @Test
    fun aSubscriptionAsksForEventsAtTheCallbackAndKeepsWhatTheRendererGranted() {
        val sent = LinkedBlockingQueue<Request>()
        val controlPoint = UpnpControlPoint(answering(sent) { it.header("SID", "uuid:sub-1").header("TIMEOUT", "Second-1800") })

        val (sid, seconds) = controlPoint.subscribe("http://192.0.2.10:1549/rc/event", "http://192.0.2.77:40000/")

        val request = sent.single()
        assertEquals("SUBSCRIBE", request.method)
        assertEquals("<http://192.0.2.77:40000/>", request.header("CALLBACK"))
        assertEquals("upnp:event", request.header("NT"))
        assertEquals("uuid:sub-1", sid)
        assertEquals(1800L, seconds)
    }

    @Test
    fun aSubscriptionAnsweredWithoutAnIdIsRefused() {
        val controlPoint = UpnpControlPoint(answering(LinkedBlockingQueue()) { it })

        try {
            controlPoint.subscribe("http://192.0.2.10:1549/rc/event", "http://192.0.2.77:40000/")
            throw AssertionError("a subscription with no id was taken")
        } catch (e: UpnpException) {
            assertEquals(200, e.code)
        }
    }

    @Test
    fun aNotifyFromTheRendererIsAnsweredAndItsLevelHandedOn() {
        val heard = LinkedBlockingQueue<Int>()
        val events = UpnpEvents.open("http://127.0.0.1:1549/rc/event") { heard += it }
        try {
            val reply = notify(events.callbackUrl, event(levels))

            assertTrue("answered: $reply", reply.startsWith("HTTP/1.1 200"))
            assertEquals(18, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            events.close()
        }
    }

    @Test
    fun aNotifyFromAnotherAddressIsNotTakenAsTheRenderersLevel() {
        val heard = LinkedBlockingQueue<Int>()
        // Still loopback, so this test can reach the listener, but not the address the requests come from.
        val events = UpnpEvents.open("http://127.0.0.2:1549/rc/event") { heard += it }
        try {
            // The listener closes on a stranger without reading, which can reset this end.
            runCatching { notify(events.callbackUrl, event(levels)) }

            assertNull("a stranger's level was taken", heard.poll(1, TimeUnit.SECONDS))
        } finally {
            events.close()
        }
    }

    @Test
    fun aStrangerWhoSendsNothingDoesNotHoldUpTheRenderer() {
        val heard = LinkedBlockingQueue<Int>()
        val events = UpnpEvents.open("http://127.0.0.1:1549/rc/event") { heard += it }
        val uri = java.net.URI(events.callbackUrl)
        // A second loopback source, accepted first, that never sends.
        val stranger = Socket()
        try {
            assumeTrue(runCatching { stranger.bind(InetSocketAddress("127.0.0.2", 0)) }.isSuccess)
            stranger.connect(InetSocketAddress(uri.host, uri.port))
            thread { runCatching { notify(events.callbackUrl, event(levels)) } }

            // Well under the 2 s a read waits, so a listener that read the stranger first would miss this.
            assertEquals(18, heard.poll(1, TimeUnit.SECONDS))
        } finally {
            stranger.close()
            events.close()
        }
    }

    @Test
    fun aChunkedNotifyIsRead() {
        val heard = LinkedBlockingQueue<Int>()
        val events = UpnpEvents.open("http://127.0.0.1:1549/rc/event") { heard += it }
        try {
            val reply = notify(events.callbackUrl, event(levels), chunked = true)

            assertTrue("answered: $reply", reply.startsWith("HTTP/1.1 200"))
            assertEquals(18, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            events.close()
        }
    }

    @Test
    fun aHugeDeclaredLengthDoesNotStopTheListener() {
        val heard = LinkedBlockingQueue<Int>()
        val events = UpnpEvents.open("http://127.0.0.1:1549/rc/event") { heard += it }
        try {
            val uri = java.net.URI(events.callbackUrl)
            for (framing in listOf("Content-Length: 2147483647\r\n\r\n", "Transfer-Encoding: chunked\r\n\r\n1\r\nA\r\n7fffffff\r\n")) {
                Socket(uri.host, uri.port).use { it.getOutputStream().write("NOTIFY / HTTP/1.1\r\n$framing".toByteArray()) }
            }

            thread(isDaemon = true) { runCatching { notify(events.callbackUrl, event(levels)) } }
            assertEquals(18, heard.poll(5, TimeUnit.SECONDS))
        } finally {
            events.close()
        }
    }

    private fun notify(callbackUrl: String, body: String, chunked: Boolean = false): String {
        val uri = java.net.URI(callbackUrl)
        Socket(uri.host, uri.port).use { socket ->
            val bytes = body.toByteArray(Charsets.UTF_8)
            fun chunk(part: ByteArray) = "${Integer.toHexString(part.size)};ext=1\r\n".toByteArray() + part + "\r\n".toByteArray()
            val half = bytes.size / 2
            // Quoted, as UDA 1.1 writes its own chunked example.
            val framed = if (chunked) {
                "TRANSFER-ENCODING: \"chunked\"\r\n\r\n".toByteArray() +
                    chunk(bytes.copyOfRange(0, half)) + chunk(bytes.copyOfRange(half, bytes.size)) + "0\r\n\r\n".toByteArray()
            } else {
                "Content-Length: ${bytes.size}\r\n\r\n".toByteArray() + bytes
            }
            socket.getOutputStream().write(
                ("NOTIFY / HTTP/1.1\r\nHOST: ${uri.host}:${uri.port}\r\nCONTENT-TYPE: text/xml\r\n" +
                    "NT: upnp:event\r\nNTS: upnp:propchange\r\nSID: uuid:sub-1\r\nSEQ: 0\r\n").toByteArray() + framed
            )
            return socket.getInputStream().bufferedReader().readLine().orEmpty()
        }
    }

    private fun answering(sent: LinkedBlockingQueue<Request>, headers: (Response.Builder) -> Response.Builder) =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                sent += chain.request()
                headers(
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body("".toResponseBody("text/plain".toMediaType()))
                ).build()
            })
            .build()
}
