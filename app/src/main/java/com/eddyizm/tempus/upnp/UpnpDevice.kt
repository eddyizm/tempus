package com.eddyizm.tempus.upnp

import android.util.Log
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

data class UpnpDevice(
    val udn: String,
    val friendlyName: String,
    val manufacturer: String,
    val modelName: String,
    val controlUrls: Map<String, String>,
    val location: String
) {
    val avTransportControlUrl: String? get() = controlUrls[AV_TRANSPORT]

    val isRenderer: Boolean get() = avTransportControlUrl != null

    // UDN when declared, since the device may answer from another port.
    val routeId: String get() = udn.ifBlank { "$location|$friendlyName" }

    companion object {
        private const val TAG = "UpnpDevice"

        const val AV_TRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1"

        // One device per element that declares services, since a multi zone receiver publishes one per zone.
        @JvmStatic
        fun parseDescription(xml: String, location: String): List<UpnpDevice> {
            val root = try {
                DocumentBuilderFactory.newInstance()
                    .also { it.isNamespaceAware = false }
                    .newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
                    .documentElement
            } catch (e: Exception) {
                Log.w(TAG, "unparseable description from $location", e)
                return emptyList()
            }

            val base = directChildren(root, "URLBase").firstOrNull()
                ?.textContent?.trim()?.takeIf { it.isNotEmpty() } ?: location

            val devices = ArrayList<UpnpDevice>()
            val elements = root.getElementsByTagName("device")
            for (i in 0 until elements.length) {
                val element = elements.item(i) as? Element ?: continue
                parseDevice(element, base, location)?.let { devices += it }
            }
            return devices
        }

        /** Direct children only, or an embedded device's services and identity read as this device's own. */
        private fun parseDevice(device: Element, base: String, location: String): UpnpDevice? {
            fun own(tag: String) = directChildren(device, tag).firstOrNull()?.textContent?.trim().orEmpty()

            val controlUrls = HashMap<String, String>()
            val services = directChildren(device, "serviceList").flatMap { directChildren(it, "service") }
            for (service in services) {
                val type = firstText(service, "serviceType")?.trim() ?: continue
                // An empty controlURL resolves to the description URL itself and would pass as a control endpoint.
                firstText(service, "controlURL")?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
                    resolve(base, raw)?.let { controlUrls[type] = it }
                }
            }
            if (controlUrls.isEmpty()) return null

            return UpnpDevice(
                udn = own("UDN"),
                friendlyName = own("friendlyName"),
                manufacturer = own("manufacturer"),
                modelName = own("modelName"),
                controlUrls = controlUrls,
                location = location
            )
        }

        private fun directChildren(parent: Element, tag: String): List<Element> {
            val out = ArrayList<Element>()
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i) as? Element ?: continue
                if (child.tagName.substringAfter(':') == tag) out += child
            }
            return out
        }

        private fun firstText(scope: Element, tag: String): String? {
            val nodes = scope.getElementsByTagName(tag)
            if (nodes.length == 0) return null
            return nodes.item(0).textContent
        }

        private fun resolve(base: String, url: String): String? = try {
            URI(base).resolve(url).toString()
        } catch (e: Exception) {
            null
        }
    }
}
