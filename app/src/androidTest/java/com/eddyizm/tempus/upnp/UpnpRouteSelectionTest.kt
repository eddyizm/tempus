package com.eddyizm.tempus.upnp

import android.content.Context
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** An emulator reaches no local network, so the renderer is faked. */
@RunWith(AndroidJUnit4::class)
class UpnpRouteSelectionTest {

    private val description = """
        <?xml version="1.0"?>
        <root xmlns="urn:schemas-upnp-org:device-1-0">
          <device>
            <deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>
            <friendlyName>Fake Renderer</friendlyName>
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

    /** No packet goes out. The second round waits on [secondRound] so a test sees round one alone. */
    private class OneFakeRenderer(private val device: UpnpDevice) : UpnpControlPoint() {
        val calls = AtomicInteger()
        val secondRound = CountDownLatch(1)

        override fun discover(timeoutMillis: Int): List<UpnpDevice> {
            if (calls.incrementAndGet() == 2) secondRound.await(5, TimeUnit.SECONDS)
            return listOf(device)
        }
    }

    private lateinit var fake: OneFakeRenderer
    private lateinit var context: Context
    private lateinit var router: MediaRouter
    private lateinit var provider: UpnpRouteProvider
    private lateinit var callback: MediaRouter.Callback
    private lateinit var device: UpnpDevice

    private val selected = AtomicInteger()
    private val unselected = AtomicInteger()

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        device = UpnpDevice.parseDescription(description, "http://192.0.2.10:1549/").single()

        // A provider only searches while something listens for its routes.
        callback = object : MediaRouter.Callback() {}

        // MediaRouteProvider builds a Handler with no Looper in its constructor, so this is main thread.
        fake = OneFakeRenderer(device)
        onMain {
            provider = UpnpRouteProvider(context, fake)
            provider.selectionListener = object : UpnpRouteProvider.SelectionListener {
                override fun onRendererSelected(device: UpnpDevice) {
                    selected.incrementAndGet()
                }

                override fun onRendererUnselected(device: UpnpDevice) {
                    unselected.incrementAndGet()
                }
            }
            router = MediaRouter.getInstance(context)
            router.addProvider(provider)
            router.addCallback(
                MediaRouteSelector.Builder().addControlCategory(UpnpRouteProvider.CATEGORY_UPNP).build(),
                callback,
                MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY
            )
        }
    }

    @After
    fun tearDown() {
        // Guarded so a setUp failure is reported as itself, not as the uninitialized fields it left.
        if (!::router.isInitialized) return
        fake.secondRound.countDown()
        onMain {
            router.unselect(MediaRouter.UNSELECT_REASON_STOPPED)
            router.removeCallback(callback)
            router.removeProvider(provider)
        }
    }

    @Test
    fun aPickedRendererIsStillTheSelectedRouteAfterwards() {
        val route = awaitRoute()
        assertNotNull("the renderer never reached the picker", route)

        onMain { router.selectRoute(route!!) }

        assertEquals("the provider was never told the renderer was picked", 1, selected.get())
        assertEquals("selected route", route!!.id, onMainGet { router.selectedRoute.id })

        // The fall back lands a few seconds after picking, so the assert has to wait it out.
        Thread.sleep(SETTLE_MILLIS)

        assertEquals("the selection was dropped", route.id, onMainGet { router.selectedRoute.id })
        assertEquals("the renderer was let go of", 0, unselected.get())
    }

    @Test
    fun aRendererReachesThePickerBeforeEveryRoundHasRun() {
        val route = awaitRoute()
        assertNotNull("the renderer never reached the picker", route)

        assertTrue("published only after round ${fake.calls.get()}", fake.calls.get() <= 2)
    }

    private fun awaitRoute(): MediaRouter.RouteInfo? {
        val deadline = System.currentTimeMillis() + ROUTE_WAIT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val route = onMainGet {
                router.routes.firstOrNull { it.id.endsWith(device.udn) }
            }
            if (route != null) return route
            Thread.sleep(250)
        }
        return null
    }

    private fun onMain(block: () -> Unit) =
        InstrumentationRegistry.getInstrumentation().runOnMainSync(block)

    private fun <T> onMainGet(block: () -> T): T {
        var out: T? = null
        val done = CountDownLatch(1)
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            out = block()
            done.countDown()
        }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    private companion object {
        const val ROUTE_WAIT_MILLIS = 20_000L

        /** Longer than the few seconds the fall back was seen to take. */
        const val SETTLE_MILLIS = 10_000L
    }
}
