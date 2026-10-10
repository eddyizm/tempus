package com.eddyizm.tempus.util

import android.content.SharedPreferences
import com.eddyizm.tempus.App
import com.eddyizm.tempus.upnp.UpnpRoutesGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class NetworkPlaybackSwitchesTest {

    /** Runs [block] against preferences holding only [stored]; any other key reads its default. */
    private fun withPreferences(stored: Map<String, Boolean>, block: () -> Unit) {
        val prefs = mock<SharedPreferences>()
        whenever(prefs.getBoolean(anyString(), anyBoolean())).thenAnswer {
            stored[it.getArgument<String>(0)] ?: it.getArgument<Boolean>(1)
        }
        val app = mock<App>()
        whenever(app.preferences).thenReturn(prefs)

        mockStatic(App::class.java).use { appStatic ->
            appStatic.`when`<App> { App.getInstance() }.thenReturn(app)
            block()
        }
    }

    @Test
    fun upnpIsOffUntilTurnedOn() {
        // An update must not start discovering renderers on its own.
        withPreferences(emptyMap()) {
            assertFalse(Preferences.isUpnpEnabled())
        }
    }

    @Test
    fun lanRemoteIsOffUntilTurnedOn() {
        withPreferences(emptyMap()) {
            assertFalse(Preferences.isLanRemoteEnabled())
        }
    }

    @Test
    fun switchesReadWhatWasSaved() {
        withPreferences(mapOf(Preferences.UPNP_ENABLED to true, Preferences.LAN_REMOTE_ENABLED to true)) {
            assertTrue(Preferences.isUpnpEnabled())
            assertTrue(Preferences.isLanRemoteEnabled())
        }
    }

    @Test
    fun nearbyDevicesIsNotAskedForWithBothSwitchesOff() {
        withPreferences(emptyMap()) {
            assertFalse(Preferences.needsNearbyDevices())
        }
    }

    @Test
    fun nearbyDevicesIsAskedForWhenEitherSwitchIsOn() {
        withPreferences(mapOf(Preferences.UPNP_ENABLED to true)) {
            assertTrue(Preferences.needsNearbyDevices())
        }
        withPreferences(mapOf(Preferences.LAN_REMOTE_ENABLED to true)) {
            assertTrue(Preferences.needsNearbyDevices())
        }
    }

    @Test
    fun upnpRoutesAreRegisteredOnlyWhenTheSwitchIsOn() {
        assertEquals(UpnpRoutesGate.Change.REGISTER, UpnpRoutesGate.change(enabled = true, registered = false))
        assertEquals(UpnpRoutesGate.Change.NONE, UpnpRoutesGate.change(enabled = false, registered = false))
    }

    @Test
    fun upnpRoutesAreReleasedWhenTheSwitchIsTurnedOff() {
        assertEquals(UpnpRoutesGate.Change.RELEASE, UpnpRoutesGate.change(enabled = false, registered = true))
        assertEquals(UpnpRoutesGate.Change.NONE, UpnpRoutesGate.change(enabled = true, registered = true))
    }
}
