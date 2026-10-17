package com.eddyizm.tempus.upnp

/** What the media service does with its UPnP route provider when the UPnP switch is read. */
object UpnpRoutesGate {

    enum class Change { REGISTER, RELEASE, NONE }

    fun change(enabled: Boolean, registered: Boolean): Change = when {
        enabled && !registered -> Change.REGISTER
        !enabled && registered -> Change.RELEASE
        else -> Change.NONE
    }
}
