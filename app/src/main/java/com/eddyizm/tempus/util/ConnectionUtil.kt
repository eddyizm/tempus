package com.eddyizm.tempus.util

import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.eddyizm.tempus.App
import java.util.concurrent.atomic.AtomicInteger

object ConnectionUtil {
    private const val TAG = "ConnectionUtil"

    private val pingsInFlight = AtomicInteger(0)

    @Volatile
    private var lastPingIssuedAt = 0L

    @Volatile
    private var pingDeferredUntil = 0L

    @JvmStatic
    fun deferPing() {
        pingDeferredUntil = Long.MAX_VALUE
    }

    @JvmStatic
    fun endPingDeferral() {
        pingDeferredUntil = 0L
    }

    @JvmStatic
    fun isPingDeferred(): Boolean = pingDeferredUntil == Long.MAX_VALUE

    @JvmStatic
    fun expirePingDeferral() {
        if (pingDeferredUntil == Long.MAX_VALUE) pingDeferredUntil = SystemClock.elapsedRealtime() + pingWaitMs()
    }

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

    @JvmStatic
    fun pingsOutstanding(): Boolean {
        return SystemClock.elapsedRealtime() < pingDeferredUntil || (pingsInFlight.get() > 0 &&
                SystemClock.elapsedRealtime() - lastPingIssuedAt < pingWaitMs())
    }

    @JvmStatic
    fun isUnderAddress(url: String?, address: String?): Boolean {
        if (url == null || address.isNullOrEmpty()) return false

        val base = address.trimEnd('/')
        if (base.isEmpty() || !url.startsWith(base)) return false

        return url.length == base.length || url[base.length] == '/'
    }

    @JvmStatic
    fun moveToAddress(url: String, addresses: List<String?>, inUse: String?): String {
        if (inUse.isNullOrEmpty()) return url

        val base = (addresses + inUse)
            .filter { isUnderAddress(url, it) }
            .map { it!!.trimEnd('/') }
            .maxByOrNull { it.length } ?: return url
        val target = inUse.trimEnd('/')

        return if (base == target) url else target + url.substring(base.length)
    }

    @JvmStatic
    @OptIn(UnstableApi::class)
    fun moveStreamToAddressInUse(dataSpec: DataSpec): DataSpec {
        val url = dataSpec.uri.toString()
        val moved = moveToAddress(url, listOf(Preferences.getLocalAddress(), Preferences.getServer()),
            Preferences.getInUseServerAddress())
        if (moved == url) return dataSpec

        Log.d(TAG, "Stream moved onto the in use server address")
        return dataSpec.withUri(moved.toUri())
    }

    @JvmStatic
    fun useServerAddress(address: String?) {
        Preferences.setInUseServerAddress(address)
        App.refreshSubsonicClient()
    }
}
