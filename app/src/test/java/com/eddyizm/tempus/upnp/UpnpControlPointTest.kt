package com.eddyizm.tempus.upnp

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Parsing and escaping under a renderer conversation. Fixtures are real answers, values replaced. */
class UpnpControlPointTest {

    private val relativeDescription = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Living Room Renderer</friendlyName>
            <manufacturer>Example</manufacturer>
            <modelName>Renderer One</modelName>
            <UDN>uuid:11111111-2222-3333-4444-555555555555</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                <controlURL>/AVTransport/control.xml</controlURL>
                <SCPDURL>/AVTransport/scpd.xml</SCPDURL>
              </service>
              <service>
                <serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>
                <controlURL>/RenderingControl/control.xml</controlURL>
                <SCPDURL>/RenderingControl/scpd.xml</SCPDURL>
              </service>
            </serviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun relativeControlUrlsResolveAgainstTheLocationTheDescriptionCameFrom() {
        val device = UpnpDevice.parseDescription(relativeDescription, "http://192.0.2.10:1549/").single()

        assertEquals("Living Room Renderer", device.friendlyName)
        assertEquals("http://192.0.2.10:1549/AVTransport/control.xml", device.avTransportControlUrl)
        assertTrue(device.isRenderer)
    }

    @Test
    fun urlBaseWinsOverTheLocationWhenTheDescriptionDeclaresOne() {
        val withBase = relativeDescription.replace(
            "<device>", "<URLBase>http://192.0.2.99:80/</URLBase><device>"
        )

        val device = UpnpDevice.parseDescription(withBase, "http://192.0.2.10:1549/").single()

        assertEquals("http://192.0.2.99:80/AVTransport/control.xml", device.avTransportControlUrl)
    }

    @Test
    fun aDeviceWithNoServicesIsNotADeviceWeCanUse() {
        // Routers, printers and set top boxes all answer the same search.
        val gateway = """
            <?xml version="1.0"?>
            <root xmlns="urn:schemas-upnp-org:device-1-0">
              <device>
                <deviceType>urn:schemas-upnp-org:device:InternetGatewayDevice:1</deviceType>
                <friendlyName>Some Router</friendlyName>
              </device>
            </root>
        """.trimIndent()

        assertEquals(emptyList<UpnpDevice>(), UpnpDevice.parseDescription(gateway, "http://192.0.2.1:1900/rootDesc.xml"))
    }

    @Test
    fun aDeviceOfferingOnlyRenderingControlIsNotARenderer() {
        val noTransport = relativeDescription.replace(
            "urn:schemas-upnp-org:service:AVTransport:1", "urn:schemas-upnp-org:service:Other:1"
        )

        val device = UpnpDevice.parseDescription(noTransport, "http://192.0.2.10:1549/").single()

        assertEquals(false, device.isRenderer)
    }

    private val multiZoneDescription = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:Basic:1</deviceType>
            <friendlyName>Multi Zone Receiver</friendlyName>
            <manufacturer>Example</manufacturer>
            <modelName>Receiver Nine</modelName>
            <UDN>uuid:11111111-2222-3333-4444-000000000000</UDN>
            <serviceList>
              <service>
                <serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>
                <controlURL>/root/cm/control</controlURL>
                <SCPDURL>/root/cm/scpd.xml</SCPDURL>
              </service>
            </serviceList>
            <deviceList>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Zone One</friendlyName>
                <manufacturer>Example</manufacturer>
                <modelName>Zone</modelName>
                <UDN>uuid:11111111-2222-3333-4444-000000000001</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/zone1/avt/control</controlURL>
                    <SCPDURL>/zone1/avt/scpd.xml</SCPDURL>
                  </service>
                </serviceList>
              </device>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
                <friendlyName>Zone Two</friendlyName>
                <manufacturer>Example</manufacturer>
                <modelName>Zone</modelName>
                <UDN>uuid:11111111-2222-3333-4444-000000000002</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>
                    <controlURL>/zone2/avt/control</controlURL>
                    <SCPDURL>/zone2/avt/scpd.xml</SCPDURL>
                  </service>
                </serviceList>
              </device>
              <device>
                <deviceType>urn:schemas-upnp-org:device:MediaServer:1</deviceType>
                <friendlyName>Receiver Library</friendlyName>
                <UDN>uuid:11111111-2222-3333-4444-000000000003</UDN>
                <serviceList>
                  <service>
                    <serviceType>urn:schemas-upnp-org:service:ContentDirectory:1</serviceType>
                    <controlURL>/library/cd/control</controlURL>
                    <SCPDURL>/library/cd/scpd.xml</SCPDURL>
                  </service>
                </serviceList>
              </device>
            </deviceList>
          </device>
        </root>
    """.trimIndent()

    @Test
    fun everyEmbeddedRendererComesBackAsADeviceOfItsOwn() {
        val location = "http://192.0.2.40:8080/description.xml"
        val controlPoint = UpnpControlPoint(clientAnswering(mapOf(location to multiZoneDescription)))

        val devices = controlPoint.fetchDescriptions(listOf(location))

        assertEquals(listOf("Zone One", "Zone Two"), devices.map { it.friendlyName })
        assertEquals(
            listOf(
                "uuid:11111111-2222-3333-4444-000000000001",
                "uuid:11111111-2222-3333-4444-000000000002"
            ),
            devices.map { it.udn }
        )
        assertEquals("http://192.0.2.40:8080/zone1/avt/control", devices[0].avTransportControlUrl)
        assertEquals("http://192.0.2.40:8080/zone2/avt/control", devices[1].avTransportControlUrl)
        assertEquals(setOf(UpnpDevice.AV_TRANSPORT), devices[0].controlUrls.keys)
        assertEquals(setOf(UpnpDevice.AV_TRANSPORT), devices[1].controlUrls.keys)
    }

    @Test
    fun twoRenderersInOneDocumentWithNoUdnStayTwoDevices() {
        val location = "http://192.0.2.40:8080/description.xml"
        val anonymous = multiZoneDescription.replace(Regex("<UDN>[^<]*</UDN>"), "")
        val controlPoint = UpnpControlPoint(clientAnswering(mapOf(location to anonymous)))

        val devices = controlPoint.fetchDescriptions(listOf(location))

        assertEquals(listOf("Zone One", "Zone Two"), devices.map { it.friendlyName })
        assertEquals(listOf("", ""), devices.map { it.udn })
        assertEquals(
            "the two zones do not have a route id each that survives a control port moving",
            listOf("$location|Zone One", "$location|Zone Two"),
            devices.map { it.routeId }
        )
    }

    @Test
    fun aDescriptionServedWithAByteOrderMarkStillParses() {
        val location = "http://192.0.2.70:8080/description.xml"
        val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) +
            relativeDescription.toByteArray(Charsets.UTF_8)
        val controlPoint = UpnpControlPoint(clientAnsweringBytes(location, marked))

        val devices = controlPoint.fetchDescriptions(listOf(location))

        assertEquals(listOf("Living Room Renderer"), devices.map { it.friendlyName })
        assertEquals("http://192.0.2.70:8080/AVTransport/control.xml", devices.single().avTransportControlUrl)
    }

    /** Byte backed body, so the response carries exactly these bytes, not a string okhttp encoded. */
    private fun clientAnsweringBytes(url: String, body: ByteArray): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            if (chain.request().url.toString() != url) throw IOException("nothing serving $url")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/xml".toMediaType()))
                .build()
        })
        .build()

    @Test
    fun anEmptyControlUrlIsNotAnEndpoint() {
        // An empty element resolves to the description URL, so every action went to that address.
        val emptyControl = relativeDescription.replace(
            "<controlURL>/AVTransport/control.xml</controlURL>", "<controlURL/>"
        )

        val device = UpnpDevice.parseDescription(emptyControl, "http://192.0.2.10:1549/").single()

        assertNull(device.avTransportControlUrl)
        assertEquals(false, device.isRenderer)
    }

    @Test
    fun garbageInsteadOfXmlIsNotADevice() {
        assertEquals(emptyList<UpnpDevice>(), UpnpDevice.parseDescription("<not xml", "http://192.0.2.10:1549/"))
    }

    @Test
    fun theOutArgumentsOfAResponseAreReadByName() {
        val response = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <u:GetPositionInfoResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <Track>1</Track>
                  <TrackDuration>00:07:11</TrackDuration>
                  <RelTime>00:01:58</RelTime>
                </u:GetPositionInfoResponse>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val out = UpnpControlPoint.parseSoapResponse(response, "GetPositionInfo")

        assertEquals("00:01:58", out["RelTime"])
        assertEquals("00:07:11", out["TrackDuration"])
    }

    @Test
    fun aRefusalCarriesItsCodeSoTheCallerCanTellRefusedApartFromUnsupported() {
        val fault = """
            <?xml version="1.0"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
              <s:Body>
                <s:Fault>
                  <faultcode>s:Client</faultcode>
                  <faultstring>UPnPError</faultstring>
                  <detail>
                    <UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                      <errorCode>606</errorCode>
                      <errorDescription>Action not authorized</errorDescription>
                    </UPnPError>
                  </detail>
                </s:Fault>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        val out = UpnpControlPoint.parseSoapResponse(fault, "GetVolume")

        assertEquals("606", out["errorCode"])
        assertEquals("Action not authorized", out["errorDescription"])
    }

    @Test
    fun aTransitionRefusalIsToldFromOtherCodes() {
        assertTrue(UpnpException(701, "Transition not available").isTransitionRefused)
        assertEquals(false, UpnpException(606, "").isTransitionRefused)
    }

    @Test
    fun aStreamUrlSurvivesBeingWrittenIntoTheMetadataDocument() {
        // Credentials ride in the query string, so an unescaped ampersand truncates the URL.
        val url = "http://192.0.2.5:4533/rest/stream.view?u=user&p=secret&id=tr-1"

        val didl = UpnpControlPoint.didl(url, "Title", "Artist", "Album", "audio/flac")

        assertTrue(didl.contains("u=user&amp;p=secret&amp;id=tr-1"))
        assertEquals(false, didl.contains("u=user&p="))
        // Without the byte range flag the LG C1 gives up every seek within a second of accepting it.
        assertTrue(didl.contains(":audio/flac:DLNA.ORG_OP=01;"))
    }

    @Test
    fun metadataTextThatLooksLikeMarkupDoesNotBreakTheDocument() {
        val didl = UpnpControlPoint.didl("http://192.0.2.5/a.flac", "<b>Ampersand & Co</b>", "A", "B", "audio/flac")

        assertTrue(didl.contains("&lt;b&gt;Ampersand &amp; Co&lt;/b&gt;"))
    }

    @Test
    fun theLocationHeaderKeepsItsScheme() {
        val reply = "HTTP/1.1 200 OK\r\n" +
            "CACHE-CONTROL: max-age=1800\r\n" +
            "LOCATION: http://192.0.2.10:1549/\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"

        assertEquals("http://192.0.2.10:1549/", UpnpControlPoint.locationOf(reply))
    }

    @Test
    fun aReplyWithNoLocationIsIgnoredInsteadOfGuessedAt() {
        assertNull(UpnpControlPoint.locationOf("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n\r\n"))
    }

    private fun descriptionNamed(name: String, udn: String): String = relativeDescription
        .replace("Living Room Renderer", name)
        .replace("uuid:11111111-2222-3333-4444-555555555555", udn)

    private fun clientAnswering(
        alive: Map<String, String>,
        dead: Set<String> = emptySet(),
        deadDelayMillis: Long = 2000
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(Interceptor { chain ->
            val url = chain.request().url.toString()
            if (url in dead) {
                Thread.sleep(deadDelayMillis)
                throw IOException("no route to $url")
            }
            val body = alive[url] ?: throw IOException("nothing serving $url")
            Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(body.toResponseBody("text/xml".toMediaType()))
                .build()
        })
        .build()

    @Test
    fun addressesThatNeverAnswerDoNotHoldUpTheOneThatDoes() {
        val live = "http://192.0.2.10:1549/"
        val dead = setOf("http://192.0.2.11:1549/", "http://192.0.2.12:1549/")
        val controlPoint = UpnpControlPoint(
            clientAnswering(mapOf(live to relativeDescription), dead, deadDelayMillis = 2000)
        )

        val start = System.currentTimeMillis()
        val devices = controlPoint.fetchDescriptions(dead.toList() + live)
        val elapsed = System.currentTimeMillis() - start

        assertEquals(listOf("Living Room Renderer"), devices.map { it.friendlyName })
        // Serial, the two dead addresses cost 4000ms. Under 3500ms can only have happened in parallel.
        assertTrue("took ${elapsed}ms", elapsed < 3500)
    }

    @Test
    fun renderersComeBackInTheOrderTheAddressesWereGiven() {
        val first = "http://192.0.2.10:1549/"
        val second = "http://192.0.2.20:1549/"
        val third = "http://192.0.2.30:1549/"
        val controlPoint = UpnpControlPoint(
            clientAnswering(
                mapOf(
                    first to descriptionNamed("Kitchen", "uuid:aaaaaaaa-0000-0000-0000-000000000001"),
                    second to descriptionNamed("Bedroom", "uuid:aaaaaaaa-0000-0000-0000-000000000002"),
                    third to descriptionNamed("Garage", "uuid:aaaaaaaa-0000-0000-0000-000000000003")
                )
            )
        )

        val devices = controlPoint.fetchDescriptions(listOf(first, second, third))

        assertEquals(listOf("Kitchen", "Bedroom", "Garage"), devices.map { it.friendlyName })
    }

    @Test
    fun anAddressThatFailsIsLeftOutInsteadOfEndingTheSearch() {
        val broken = "http://192.0.2.11:1549/"
        val live = "http://192.0.2.10:1549/"
        val controlPoint = UpnpControlPoint(clientAnswering(mapOf(live to relativeDescription)))

        val devices = controlPoint.fetchDescriptions(listOf(broken, live))

        assertEquals(listOf("Living Room Renderer"), devices.map { it.friendlyName })
    }

    @Test
    fun aPlayRefusedWhileTheRendererIsStillStoppingIsAskedAgain() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val controlPoint = UpnpControlPoint(clientFailingPlayOnce(calls, 501))

        controlPoint.play(UpnpDevice.parseDescription(relativeDescription, "http://192.0.2.10:1549/").single())

        assertEquals(2, calls.get())
    }

    @Test
    fun aRendererThatKeepsRefusingIsGivenUpOnInsteadOfAskedForever() {
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val controlPoint = UpnpControlPoint(clientAlwaysFailingPlay(calls, 701))
        val device = UpnpDevice.parseDescription(relativeDescription, "http://192.0.2.10:1549/").single()

        try {
            controlPoint.play(device)
            throw AssertionError("the refusal should have reached the caller")
        } catch (e: UpnpException) {
            assertEquals(701, e.code)
        }
        assertEquals(2, calls.get())
    }

    private fun clientAlwaysFailingPlay(calls: java.util.concurrent.atomic.AtomicInteger, errorCode: Int) =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                calls.incrementAndGet()
                val body = """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                    <s:Body><s:Fault><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                    <errorCode>$errorCode</errorCode><errorDescription>Refused</errorDescription>
                    </UPnPError></detail></s:Fault></s:Body></s:Envelope>"""
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(500)
                    .message("Internal Server Error")
                    .body(body.toResponseBody("text/xml".toMediaType()))
                    .build()
            })
            .build()

    @Test
    fun aRefusalThatIsNotAboutTimingIsNotAskedAgain() {
        // 718 is an illegal instance. Asking twice only delays the failure the caller has to show.
        val calls = java.util.concurrent.atomic.AtomicInteger()
        val controlPoint = UpnpControlPoint(clientFailingPlayOnce(calls, 718))
        val device = UpnpDevice.parseDescription(relativeDescription, "http://192.0.2.10:1549/").single()

        try {
            controlPoint.play(device)
            throw AssertionError("the refusal should have reached the caller")
        } catch (e: UpnpException) {
            assertEquals(718, e.code)
        }
        assertEquals(1, calls.get())
    }

    /** Fails the first Play with [errorCode], answers every later one. */
    private fun clientFailingPlayOnce(calls: java.util.concurrent.atomic.AtomicInteger, errorCode: Int) =
        OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val first = calls.incrementAndGet() == 1
                val body = if (first) {
                    """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                        <s:Body><s:Fault><detail><UPnPError xmlns="urn:schemas-upnp-org:control-1-0">
                        <errorCode>$errorCode</errorCode><errorDescription>Refused</errorDescription>
                        </UPnPError></detail></s:Fault></s:Body></s:Envelope>"""
                } else {
                    """<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
                        <s:Body><u:PlayResponse xmlns:u="urn:schemas-upnp-org:service:AVTransport:1"/>
                        </s:Body></s:Envelope>"""
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(if (first) 500 else 200)
                    .message(if (first) "Internal Server Error" else "OK")
                    .body(body.toResponseBody("text/xml".toMediaType()))
                    .build()
            })
            .build()
}
