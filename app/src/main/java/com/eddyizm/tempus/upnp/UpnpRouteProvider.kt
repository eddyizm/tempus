package com.eddyizm.tempus.upnp

import android.content.Context
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouteDescriptor
import androidx.mediarouter.media.MediaRouteDiscoveryRequest
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderDescriptor
import androidx.mediarouter.media.MediaRouter
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class UpnpRouteProvider(
    context: Context,
    private val controlPoint: UpnpControlPoint = UpnpControlPoint()
) : MediaRouteProvider(context) {

    // Both name the device, since the framework selects the new route before unselecting the old.
    interface SelectionListener {
        fun onRendererSelected(device: UpnpDevice)
        fun onRendererUnselected(device: UpnpDevice)
    }

    var selectionListener: SelectionListener? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    // Replaced whole, never edited in place, or publish() throws ConcurrentModificationException.
    @Volatile private var devices: Map<String, UpnpDevice> = emptyMap()

    @Volatile private var scanning = false
    @Volatile private var activeScan = false
    @Volatile private var scanInFlight = false

    private val rescan = object : Runnable {
        override fun run() {
            if (!scanning) return
            // The worker's queue is unbounded, so a tick posted while a scan runs builds a backlog.
            if (!scanInFlight && !worker.isShutdown) {
                scanInFlight = true
                worker.execute {
                    try {
                        scanOnce()
                    } finally {
                        scanInFlight = false
                    }
                }
            }
            handler.postDelayed(this, if (activeScan) ACTIVE_INTERVAL_MS else PASSIVE_INTERVAL_MS)
        }
    }

    /** The executor thread is not a daemon and the owning service cycles often, leaking one per cycle. */
    fun release() {
        scanning = false
        activeScan = false
        handler.removeCallbacksAndMessages(null)
        worker.shutdown()
    }

    // A route button on screen asks for no discovery, only the chooser dialog or the Cast SDK does.
    override fun onDiscoveryRequestChanged(request: MediaRouteDiscoveryRequest?) {
        val wanted = request?.selector?.hasControlCategory(CATEGORY_UPNP) == true
        val active = wanted && request?.isActiveScan == true
        if (wanted == scanning && active == activeScan) return
        scanning = wanted
        activeScan = active
        handler.removeCallbacks(rescan)
        if (wanted) handler.post(rescan)
    }

    /** Published as each round lands, since the picker gives up on a search that shows it nothing. */
    private fun scanOnce() {
        repeat(ROUNDS_PER_SCAN) {
            if (!scanning) return
            val found = try {
                controlPoint.discover()
            } catch (e: Exception) {
                Log.w(TAG, "discovery failed", e)
                return
            }

            // Never dropped on one empty scan, since SSDP is lossy and a device may answer past a round.
            if (found.isNotEmpty()) {
                devices = LinkedHashMap(devices).apply {
                    for (device in found) put(device.routeId, device)
                }
            }
            if (devices.isNotEmpty()) handler.post { publish() }
        }
    }

    private fun publish() {
        val builder = MediaRouteProviderDescriptor.Builder()
        for ((id, device) in devices) {
            builder.addRoute(
                MediaRouteDescriptor.Builder(id, device.friendlyName.ifBlank { "UPnP renderer" })
                    .setDescription(device.modelName.ifBlank { device.manufacturer })
                    .addControlFilter(CONTROL_FILTER)
                    .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
                    // Fixed, since an LG C1 reports zero volume while audibly playing and unmuted.
                    .setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_FIXED)
                    .setEnabled(true)
                    .build()
            )
        }
        setDescriptor(builder.build())
    }

    override fun onCreateRouteController(routeId: String): RouteController? {
        val device = devices[routeId] ?: return null
        return object : RouteController() {
            override fun onSelect() {
                selectionListener?.onRendererSelected(device)
            }

            override fun onUnselect(reason: Int) {
                selectionListener?.onRendererUnselected(device)
            }
        }
    }

    companion object {
        private const val TAG = "UpnpRouteProvider"

        const val CATEGORY_UPNP = "com.eddyizm.tempus.UPNP"

        private val CONTROL_FILTER = IntentFilter().apply { addCategory(CATEGORY_UPNP) }

        private const val ROUNDS_PER_SCAN = 3

        private val ACTIVE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(10)

        private val PASSIVE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(2)
    }
}
