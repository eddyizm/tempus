package com.eddyizm.tempus.lan

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.ArrayAdapter
import androidx.fragment.app.FragmentActivity
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object LanDevicePicker {
    @JvmStatic fun toggle(activity: FragmentActivity) {
        if (LanRemoteSession.isActive()) { LanRemoteSession.disconnect(); return }
        // No discovery at all while the LAN remote switch is off.
        if (!Preferences.isLanRemoteEnabled()) return
        if (Build.VERSION.SDK_INT >= 37 && activity.checkSelfPermission("android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            activity.startActivity(Intent(activity, LanPlaybackActivity::class.java)); return
        }
        val nsd = activity.getSystemService(Context.NSD_SERVICE) as NsdManager
        val main = Handler(Looper.getMainLooper())
        val devices = linkedMapOf<String, NsdServiceInfo>()
        val adapter = ArrayAdapter<String>(activity, android.R.layout.simple_list_item_1)
        var closed = false
        var resolving = false
        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.lan_choose_device)
            .setAdapter(adapter, null)
            .setNeutralButton(R.string.lan_pair_title) { _, _ -> activity.startActivity(Intent(activity, LanPlaybackActivity::class.java)) }
            .setNegativeButton(android.R.string.cancel, null).create()
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) { main.post { if (!closed) dialog.setTitle(R.string.lan_discovery_error) } }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) { main.post {
                if (!closed) { devices[info.serviceName] = info; adapter.clear(); adapter.addAll(devices.keys); dialog.setTitle(R.string.lan_choose_device) }
            } }
            override fun onServiceLost(info: NsdServiceInfo) { main.post {
                if (!closed) { devices.remove(info.serviceName); adapter.clear(); adapter.addAll(devices.keys) }
            } }
        }
        val lifecycleObserver = object : androidx.lifecycle.DefaultLifecycleObserver {
            override fun onDestroy(owner: androidx.lifecycle.LifecycleOwner) { dialog.dismiss() }
        }
        activity.lifecycle.addObserver(lifecycleObserver)
        dialog.setOnDismissListener {
            closed = true
            activity.lifecycle.removeObserver(lifecycleObserver)
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        dialog.show()
        dialog.setTitle(R.string.lan_searching)
        dialog.listView.setOnItemClickListener { _, _, position, _ ->
            if (!resolving) {
                val info = devices[adapter.getItem(position)] ?: return@setOnItemClickListener
                resolving = true
                dialog.setTitle(R.string.lan_connecting)
                @Suppress("DEPRECATION")
                try { nsd.resolveService(info, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(service: NsdServiceInfo, code: Int) { main.post {
                        resolving = false
                        if (!closed) dialog.setTitle(R.string.lan_connection_error)
                    } }
                    override fun onServiceResolved(service: NsdServiceInfo) { main.post {
                        if (!closed && !activity.isFinishing && !activity.isDestroyed) {
                            LanRemoteSession.connect(activity, service)
                            dialog.dismiss()
                        }
                    } }
                }) } catch (e: Exception) {
                    resolving = false
                    LanDiagnostics.record("Controller could not resolve device", e)
                    if (!closed) dialog.setTitle(R.string.lan_connection_error)
                }
            }
        }
        try { nsd.discoverServices(LanWire.TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (_: Exception) { dialog.setTitle(R.string.lan_discovery_error) }

    }
}
