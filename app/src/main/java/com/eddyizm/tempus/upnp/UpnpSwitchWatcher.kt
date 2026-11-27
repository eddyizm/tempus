package com.eddyizm.tempus.upnp

import android.content.Context
import android.media.MediaRouter2
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat

/**
 * Hears when playback is switched from a renderer to the phone or a Bluetooth device in the system output
 * switcher. The framework reports that as a transfer from the renderer's session to the system session, and a
 * stop only as the session stopping. AndroidX MediaRouter passes neither on for sessions the system started.
 */
@RequiresApi(30)
class UpnpSwitchWatcher(context: Context, private val onSwitchedAway: () -> Unit) {

    private val router = MediaRouter2.getInstance(context)
    private val executor = ContextCompat.getMainExecutor(context)

    private val callback = object : MediaRouter2.TransferCallback() {
        override fun onTransfer(oldController: MediaRouter2.RoutingController, newController: MediaRouter2.RoutingController) {
            if (newController == router.systemController && isUpnpSession(oldController)) onSwitchedAway()
        }
    }

    fun start() = router.registerTransferCallback(executor, callback)

    fun stop() = router.unregisterTransferCallback(callback)

    // Session ids are "<package>/<provider service>:<id>", and only the UPnP provider runs in this service.
    private fun isUpnpSession(controller: MediaRouter2.RoutingController): Boolean =
        controller.id.contains(UpnpRouteProviderService::class.java.name)
}
