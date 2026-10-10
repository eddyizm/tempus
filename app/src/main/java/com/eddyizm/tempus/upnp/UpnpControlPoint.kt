package com.eddyizm.tempus.upnp

import android.util.Log
import java.io.StringReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Element
import org.xml.sax.InputSource

class UpnpException(val code: Int, val description: String) :
    Exception("UPnP error $code: $description") {

    /** Transport state makes the action illegal. Not a missing capability, never record it as one. */
    val isTransitionRefused: Boolean get() = code == TRANSITION_NOT_AVAILABLE

    /** After a URL handover, this means the renderer could not fetch that URL. */
    val isResourceMissing: Boolean get() = code == RESOURCE_NOT_FOUND

    companion object {
        const val TRANSITION_NOT_AVAILABLE = 701
        const val RESOURCE_NOT_FOUND = 716
    }
}

/** SSDP to find renderers, SOAP to drive them. Every call blocks, so never make one on the main thread. */
open class UpnpControlPoint(
    private val http: OkHttpClient = defaultClient()
) {

    // open for tests: emulators have no local network.
    open fun discover(timeoutMillis: Int = 3000): List<UpnpDevice> =
        fetchDescriptions(search(timeoutMillis))

    /** Reads run together, since one address that connects and then says nothing holds a whole call. */
    internal fun fetchDescriptions(locations: Collection<String>): List<UpnpDevice> {
        if (locations.isEmpty()) return emptyList()

        val pool = Executors.newFixedThreadPool(minOf(locations.size, MAX_DESCRIPTION_FETCHES))
        val fetched = try {
            pool.invokeAll(locations.map { location -> Callable { location to get(location) } })
                .map { it.get() }
        } finally {
            pool.shutdown()
        }

        val devices = ArrayList<UpnpDevice>()
        val seen = HashSet<String>()
        for ((location, xml) in fetched) {
            if (xml == null) continue
            for (device in UpnpDevice.parseDescription(xml, location)) {
                if (!device.isRenderer) continue
                // One device answers from several ports, so identity is the UDN and not the address.
                if (device.udn.isNotEmpty() && !seen.add(device.udn)) continue
                devices += device
            }
        }
        return devices
    }

    /** Window 3s while MX is 2: devices answer after a random delay up to MX, and an MX wide window lost that tail. */
    private fun search(timeoutMillis: Int): Set<String> {
        val found = LinkedHashSet<String>()
        val socket = try {
            DatagramSocket()
        } catch (e: Exception) {
            Log.w(TAG, "cannot open a socket to search", e)
            return found
        }

        try {
            val group = InetAddress.getByName(SSDP_HOST)
            for (target in SEARCH_TARGETS) {
                val payload = (
                    "M-SEARCH * HTTP/1.1\r\n" +
                        "HOST: $SSDP_HOST:$SSDP_PORT\r\n" +
                        "MAN: \"ssdp:discover\"\r\n" +
                        "MX: 2\r\n" +
                        "ST: $target\r\n\r\n"
                    ).toByteArray()
                socket.send(DatagramPacket(payload, payload.size, InetSocketAddress(group, SSDP_PORT)))
            }

            val buffer = ByteArray(8192)
            val deadline = System.currentTimeMillis() + timeoutMillis
            while (System.currentTimeMillis() < deadline) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    // What is left of the window, floored at 1ms since zero blocks forever.
                    socket.soTimeout =
                        (deadline - System.currentTimeMillis()).coerceIn(1L, timeoutMillis.toLong()).toInt()
                    socket.receive(packet)
                } catch (e: Exception) {
                    break
                }
                locationOf(String(packet.data, 0, packet.length))?.let { found += it }
            }
        } catch (e: Exception) {
            Log.w(TAG, "search failed", e)
        } finally {
            socket.close()
        }
        return found
    }

    fun setAvTransportUri(device: UpnpDevice, url: String, metadata: String) {
        soap(
            requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "SetAVTransportURI",
            listOf("InstanceID" to "0", "CurrentURI" to url, "CurrentURIMetaData" to metadata)
        )
    }

    /** Gapless handoff. Not universal, and absent it answers 602 and the caller sets the URI on stop. */
    fun setNextAvTransportUri(device: UpnpDevice, url: String, metadata: String) {
        soap(
            requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "SetNextAVTransportURI",
            listOf("InstanceID" to "0", "NextURI" to url, "NextURIMetaData" to metadata)
        )
    }

    // A renderer letting go of a track can refuse a Play with 501 or 701, so it is asked once more.
    fun play(device: UpnpDevice) {
        // Resolved first, since a missing AVTransport throws a 501 of its own.
        val control = requireAvTransport(device)
        for (attempt in 1..PLAY_ATTEMPTS) {
            try {
                sendPlay(control)
                return
            } catch (e: UpnpException) {
                val timing = e.code == PLAY_REFUSED_NOW || e.isTransitionRefused
                if (!timing || attempt == PLAY_ATTEMPTS) throw e
                Log.d(TAG, "play refused with ${e.code}, asking again in ${PLAY_RETRY_DELAY_MS}ms")
                Thread.sleep(PLAY_RETRY_DELAY_MS)
            }
        }
    }

    private fun sendPlay(controlUrl: String) {
        soap(controlUrl, UpnpDevice.AV_TRANSPORT, "Play", listOf("InstanceID" to "0", "Speed" to "1"))
    }

    fun pause(device: UpnpDevice) {
        soap(requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "Pause", listOf("InstanceID" to "0"))
    }

    fun stop(device: UpnpDevice) {
        soap(requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "Stop", listOf("InstanceID" to "0"))
    }

    fun seek(device: UpnpDevice, target: String) {
        soap(
            requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "Seek",
            listOf("InstanceID" to "0", "Unit" to "REL_TIME", "Target" to target)
        )
    }

    /** Empty means the renderer did not say, never that nothing is allowed. Renderers may answer 602. */
    fun currentTransportActions(device: UpnpDevice): Set<String> = try {
        soap(
            requireAvTransport(device), UpnpDevice.AV_TRANSPORT,
            "GetCurrentTransportActions", listOf("InstanceID" to "0")
        )["Actions"]
            ?.split(',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            .orEmpty()
    } catch (e: UpnpException) {
        emptySet()
    }

    fun transportInfo(device: UpnpDevice): Map<String, String> =
        soap(requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "GetTransportInfo", listOf("InstanceID" to "0"))

    fun positionInfo(device: UpnpDevice): Map<String, String> =
        soap(requireAvTransport(device), UpnpDevice.AV_TRANSPORT, "GetPositionInfo", listOf("InstanceID" to "0"))

    private fun requireAvTransport(device: UpnpDevice): String =
        device.avTransportControlUrl ?: throw UpnpException(501, "no AVTransport on ${device.friendlyName}")

    private fun soap(
        controlUrl: String,
        serviceType: String,
        action: String,
        args: List<Pair<String, String>>
    ): Map<String, String> {
        val body = args.joinToString("") { (name, value) -> "<$name>${escape(value)}</$name>" }
        val envelope = ENVELOPE.format(action, serviceType, body, action)

        val request = Request.Builder()
            .url(controlUrl)
            .addHeader("SOAPAction", "\"$serviceType#$action\"")
            .post(envelope.toRequestBody(SOAP_MEDIA_TYPE))
            .build()

        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw errorFrom(text, response.code)
            return parseSoapResponse(text, action)
        }
    }

    private fun errorFrom(xml: String, httpCode: Int): UpnpException {
        val fields = parseSoapResponse(xml, "")
        val code = fields["errorCode"]?.toIntOrNull() ?: httpCode
        return UpnpException(code, fields["errorDescription"].orEmpty())
    }

    // Short, since a device that connects and then sends nothing held a whole round.
    private val descriptionClient: OkHttpClient by lazy {
        http.newBuilder()
            .connectTimeout(1, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.SECONDS)
            .build()
    }

    private fun get(url: String): String? = try {
        descriptionClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            val body = response.body
            when {
                !response.isSuccessful -> null
                body.source().request(MAX_DESCRIPTION_BYTES + 1) -> {
                    Log.w(TAG, "description is over $MAX_DESCRIPTION_BYTES bytes, ignoring it: $url")
                    null
                }
                // string() drops a byte order mark the XML parser would refuse.
                else -> body.string()
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "GET failed: $url", e)
        null
    }

    companion object {
        private const val TAG = "UpnpControlPoint"

        // 501 is Action Failed, what the LG C1 answers while it is still letting go of a track.
        private const val PLAY_REFUSED_NOW = 501

        private const val PLAY_ATTEMPTS = 2
        private const val PLAY_RETRY_DELAY_MS = 1000L

        private const val SSDP_HOST = "239.255.255.250"
        private const val SSDP_PORT = 1900

        private const val MAX_DESCRIPTION_FETCHES = 8

        /** Cap on a description body, which any device on the network can make as large as it likes. */
        private const val MAX_DESCRIPTION_BYTES = 1L * 1024 * 1024

        private val SEARCH_TARGETS = listOf(
            "urn:schemas-upnp-org:device:MediaRenderer:1",
            UpnpDevice.AV_TRANSPORT
        )

        private val SOAP_MEDIA_TYPE = "text/xml; charset=utf-8".toMediaType()

        private const val ENVELOPE =
            "<?xml version=\"1.0\"?>" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"" +
                " s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">" +
                "<s:Body><u:%s xmlns:u=\"%s\">%s</u:%s></s:Body></s:Envelope>"

        /** 60s read, since an unauthorized renderer prompts on its own screen and holds Play open. */
        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()

        /** Splits on the header's own colon, leaving the one in the scheme alone. */
        @JvmStatic
        fun locationOf(reply: String): String? = reply.lineSequence()
            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }
            ?.substringAfter(':')
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

        /** Every leaf is recorded under its own name, so an errorCode nested in a Fault lands here too. */
        @JvmStatic
        fun parseSoapResponse(xml: String, action: String): Map<String, String> {
            val root = try {
                DocumentBuilderFactory.newInstance()
                    .also { it.isNamespaceAware = false }
                    .newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
                    .documentElement
            } catch (e: Exception) {
                return emptyMap()
            }

            val out = LinkedHashMap<String, String>()
            collect(root, action, out)
            return out
        }

        private fun collect(element: Element, action: String, out: MutableMap<String, String>) {
            val children = element.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                val name = child.tagName.substringAfter(':')
                val hasElementChild = (0 until child.childNodes.length)
                    .any { child.childNodes.item(it) is Element }
                if (hasElementChild) {
                    collect(child, action, out)
                } else if (name != "${action}Response") {
                    out[name] = child.textContent.orEmpty()
                }
            }
        }

        // Empty metadata is legal and several renderers then show nothing, so always send a real item.
        // DLNA.ORG_OP=01 declares byte range support. A wildcard there and the LG C1 drops a seek.
        @JvmStatic
        fun didl(url: String, title: String, artist: String, album: String, mimeType: String): String =
            "<DIDL-Lite xmlns=\"urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/\"" +
                " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"" +
                " xmlns:upnp=\"urn:schemas-upnp-org:metadata-1-0/upnp/\">" +
                "<item id=\"1\" parentID=\"0\" restricted=\"1\">" +
                "<dc:title>${escape(title)}</dc:title>" +
                "<upnp:artist>${escape(artist)}</upnp:artist>" +
                "<upnp:album>${escape(album)}</upnp:album>" +
                "<upnp:class>object.item.audioItem.musicTrack</upnp:class>" +
                "<res protocolInfo=\"http-get:*:${escape(mimeType)}:DLNA.ORG_OP=01;DLNA.ORG_FLAGS=01700000000000000000000000000000\">" +
                "${escape(url)}</res>" +
                "</item></DIDL-Lite>"

        /** Stream URLs carry credentials as query parameters, and those ampersands are escaped twice. */
        @JvmStatic
        fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}
