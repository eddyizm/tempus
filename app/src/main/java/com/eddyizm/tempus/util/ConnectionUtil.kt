package com.eddyizm.tempus.util

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicInteger

object ConnectionUtil {
    // The queue's stream URLs carry the address in force when it was built, so the media service
    // holds the queue back until a ping answers. Counted, since the service starts several times.
    private val pingsInFlight = AtomicInteger(0)

    @Volatile
    private var lastPingIssuedAt = 0L

    // The ping timeout is a user setting with no upper bound, so the wait is read from it.
    private fun pingWaitMs(): Long = Preferences.getNetworkPingTimeout() * 1000L + 1_000L

    @JvmStatic
    fun markPingIssued() {
        lastPingIssuedAt = SystemClock.elapsedRealtime()
        pingsInFlight.incrementAndGet()
    }

    @JvmStatic
    fun markPingAnswered() {
        pingsInFlight.updateAndGet { if (it > 0) it - 1 else 0 }
    }

    // The age test stops a ping that never answers, an activity torn down mid request, from holding
    // the queue for the life of the process. A start with no activity behind it never waits.
    @JvmStatic
    fun pingsOutstanding(): Boolean {
        return pingsInFlight.get() > 0 &&
                SystemClock.elapsedRealtime() - lastPingIssuedAt < pingWaitMs()
    }

    @JvmStatic
    fun awaitPingsAnswered() {
        while (pingsOutstanding()) {
            Thread.sleep(25)
        }
    }

    // A local address of http://nas is a prefix of the public http://nas.duckdns.invalid, so the
    // character after the address has to be a path separator. Pure, so a test can reach it.
    @JvmStatic
    fun isUnderAddress(url: String?, address: String?): Boolean {
        if (url == null || address.isNullOrEmpty()) return false

        val base = address.trimEnd('/')
        if (base.isEmpty() || !url.startsWith(base)) return false

        return url.length == base.length || url[base.length] == '/'
    }
}
