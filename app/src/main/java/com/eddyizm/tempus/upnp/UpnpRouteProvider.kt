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

        /** False refuses the route, so the system picker does not show a cast nothing plays on. */
        fun acceptsSelection(): Boolean = true

        /** The volume slider of the system output switcher, in the range last given to [setRouteVolume]. */
        fun onVolumeRequested(device: UpnpDevice, volume: Int) {}

        fun onVolumeAdjusted(device: UpnpDevice, delta: Int) {}
    }

    private class Volume(val level: Int, val max: Int)

    var selectionListener: SelectionListener? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    // Replaced whole, never edited in place, or publish() throws ConcurrentModificationException.
    @Volatile private var devices: Map<String, UpnpDevice> = emptyMap()

    // Main thread. Only routes whose renderer reported a level, the others stay fixed.
    private var volumes: Map<String, Volume> = emptyMap()

    // Off publishes no routes and searches for none, whatever the system asks for.
    private var enabled = true
    private var request: MediaRouteDiscoveryRequest? = null

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

    // The media service keeps a passive request open while UPnP is on, so the system switcher has renderers to
    // list. The chooser dialog and the system switcher ask for an active scan while they are open.
    override fun onDiscoveryRequestChanged(request: MediaRouteDiscoveryRequest?) {
        this.request = request
        applyDiscovery()
    }

    /** Main thread, like [onDiscoveryRequestChanged]. */
    fun setEnabled(enabled: Boolean) {
        if (this.enabled == enabled) return
        this.enabled = enabled
        if (!enabled) {
            devices = emptyMap()
            volumes = emptyMap()
            setDescriptor(null)
        }
        applyDiscovery()
    }

    private fun applyDiscovery() {
        val request = request
        val wanted = enabled && request?.selector?.hasControlCategory(CATEGORY_UPNP) == true
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
            if (devices.isNotEmpty()) handler.post { if (enabled) publish() }
        }
    }

    /** Main thread. Null while the renderer has not reported a level, which leaves the slider out. */
    fun setRouteVolume(routeId: String, level: Int?, max: Int) {
        val updated = LinkedHashMap(volumes)
        if (level == null || max <= 0) updated.remove(routeId) else updated[routeId] = Volume(level.coerceIn(0, max), max)
        volumes = updated
        if (enabled && devices.isNotEmpty()) publish()
    }

    private fun publish() {
        val builder = MediaRouteProviderDescriptor.Builder()
        for ((id, device) in devices) {
            val volume = volumes[id]
            val route = MediaRouteDescriptor.Builder(id, device.friendlyName.ifBlank { "UPnP renderer" })
                .setDescription(device.modelName.ifBlank { device.manufacturer })
                .addControlFilter(CONTROL_FILTER)
                .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
            // The system switcher draws a fixed route's slider empty, so it is variable once UpnpPlayer knows a level.
            if (volume != null) {
                route.setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE)
                    .setVolume(volume.level)
                    .setVolumeMax(volume.max)
            } else {
                route.setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_FIXED)
            }
            builder.addRoute(
                route
                    .setEnabled(true)
                    // The system picker lists routes to any app asking for their category, so only Tempus may.
                    .setVisibilityRestricted(setOf(context.packageName))
                    .build()
            )
        }
        setDescriptor(builder.build())
    }

    // The service is exported for the system, so another app could bind it and pick a renderer for Tempus.
    override fun onCreateRouteController(routeId: String, options: RouteControllerOptions): RouteController? {
        val client = options.clientPackageName
        if (client.isNotEmpty() && client != context.packageName) return null
        return onCreateRouteController(routeId)
    }

    override fun onCreateRouteController(routeId: String): RouteController? {
        val device = devices[routeId] ?: return null
        if (selectionListener?.acceptsSelection() == false) return null
        return object : RouteController() {
            override fun onSelect() {
                selectionListener?.onRendererSelected(device)
            }

            override fun onUnselect(reason: Int) {
                selectionListener?.onRendererUnselected(device)
            }

            override fun onSetVolume(volume: Int) {
                selectionListener?.onVolumeRequested(device, volume)
            }

            override fun onUpdateVolume(delta: Int) {
                selectionListener?.onVolumeAdjusted(device, delta)
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
