package com.eddyizm.tempus.upnp

/**
 * Joins [UpnpRouteProviderService], which the system binds to offer renderers in its output switcher,
 * and the media service, which plays on the renderer picked there or in the app's own cast dialog.
 * Both run in the app's process, and everything here is called on its main thread.
 */
object UpnpRoutes : UpnpRouteProvider.SelectionListener {

    // One per process, so the provider and the players share it and toggling UPnP leaves no HTTP client behind.
    val controlPoint by lazy { UpnpControlPoint() }

    /** Set by the media service while UPnP is on. */
    @Volatile var playback: UpnpRouteProvider.SelectionListener? = null

    /** Set by [UpnpRouteProviderService] while the system or the app is bound to it. */
    @Volatile var provider: UpnpRouteProvider? = null

    override fun onRendererSelected(device: UpnpDevice) {
        playback?.onRendererSelected(device)
    }

    override fun onRendererUnselected(device: UpnpDevice) {
        playback?.onRendererUnselected(device)
    }

    // Without the media service, a route picked in the system switcher would show as casting with nothing playing.
    override fun acceptsSelection(): Boolean = playback != null

    override fun onVolumeRequested(device: UpnpDevice, volume: Int) {
        playback?.onVolumeRequested(device, volume)
    }

    override fun onVolumeAdjusted(device: UpnpDevice, delta: Int) {
        playback?.onVolumeAdjusted(device, delta)
    }

    /** What the renderer's player knows of its volume, for the slider in the system output switcher. */
    fun reportVolume(device: UpnpDevice, level: Int?, max: Int) {
        provider?.setRouteVolume(device.routeId, level, max)
    }
}
