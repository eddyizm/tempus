package com.eddyizm.tempus.upnp

import android.content.SharedPreferences
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderService
import com.eddyizm.tempus.App
import com.eddyizm.tempus.util.Preferences

/**
 * Offers the renderers as a service, so the system lists them in its output switcher (Android 11 and up,
 * through MediaTransferReceiver) and the app's own MediaRouter reaches them the same way on every version.
 */
class UpnpRouteProviderService : MediaRouteProviderService() {

    private var provider: UpnpRouteProvider? = null

    // Read on every change, so turning UPnP off empties the switcher without a restart.
    private val upnpSwitchListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == Preferences.UPNP_ENABLED) provider?.setEnabled(Preferences.isUpnpEnabled())
    }

    override fun onCreateMediaRouteProvider(): MediaRouteProvider {
        val provider = UpnpRouteProvider(this, UpnpRoutes.controlPoint)
        provider.selectionListener = UpnpRoutes
        provider.setEnabled(Preferences.isUpnpEnabled())
        App.getInstance().preferences.registerOnSharedPreferenceChangeListener(upnpSwitchListener)
        this.provider = provider
        UpnpRoutes.provider = provider
        return provider
    }

    override fun onDestroy() {
        App.getInstance().preferences.unregisterOnSharedPreferenceChangeListener(upnpSwitchListener)
        provider?.let {
            if (UpnpRoutes.provider === it) UpnpRoutes.provider = null
            it.selectionListener = null
            it.release()
        }
        provider = null
        super.onDestroy()
    }
}
