package com.eddyizm.tempus.lan

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputFilter
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.eddyizm.tempus.R
import com.eddyizm.tempus.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Receiver availability and controller discovery are independent roles, not app modes. */
class LanSettingsFragment : PreferenceFragmentCompat() {
    private lateinit var app: Context
    private lateinit var nsd: NsdManager
    private lateinit var receiver: SwitchPreference
    private lateinit var pairing: SwitchPreference
    private lateinit var paired: Preference
    private lateinit var pendingRequests: PreferenceCategory
    private lateinit var devices: PreferenceCategory
    private lateinit var status: Preference
    private lateinit var controlledDevice: Preference
    private lateinit var releaseControl: Preference

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val pairingClient = LanPairingClient()
    private val discovered = linkedMapOf<String, NsdServiceInfo>()
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var operation: Future<*>? = null
    private var dialog: AlertDialog? = null
    private var active = false
    private var generation = 0L
    private var resolving = false
    private var busy = false
    private var receiverStartingAt = 0L
    private var receiverStopping = false
    private var pendingKey = ""
    private var requestingReceiver = false

    private val permission = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (!active) return@registerForActivityResult
        if (result.values.all { it }) {
            if (requestingReceiver) startReceiver() else search()
        } else {
            showStatus(R.string.lan_permission)
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        app = requireContext().applicationContext
        nsd = app.getSystemService(NsdManager::class.java)
        setPreferencesFromResource(R.xml.lan_preferences, rootKey)
        receiver = preference("lan_receiver_enabled")
        pairing = preference("lan_pairing_enabled")
        paired = preference("lan_paired_controllers")
        pendingRequests = preference("lan_pair_requests")
        devices = preference("lan_available_devices")
        status = preference("lan_setup_status")
        controlledDevice = preference("lan_controlled_device")
        releaseControl = preference("lan_release_device")
        configureDeviceName()

        receiver.setOnPreferenceChangeListener { _, value ->
            if (value == true) requestReceiver() else stopReceiver()
            // Render the service's actual state, rather than persisting an intent to start.
            false
        }
        pairing.setOnPreferenceChangeListener { _, value ->
            if (value == true) LanReceiverService.openPairing() else LanReceiverService.closePairing()
            renderReceiver()
            false
        }
        paired.setOnPreferenceClickListener {
            showPairedControllers()
            true
        }
        preference<Preference>("lan_find_devices").setOnPreferenceClickListener {
            search()
            true
        }
        releaseControl.setOnPreferenceClickListener {
            LanRemoteSession.disconnect()
            showStatus(R.string.lan_disconnected)
            true
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        LanRemoteSession.state().observe(viewLifecycleOwner) { state ->
            controlledDevice.isVisible = state.active
            releaseControl.isVisible = state.active
            controlledDevice.summary = if (state.connected) state.name else
                getString(R.string.lan_device_status, state.name, getString(state.error.takeIf { it != 0 } ?: R.string.lan_connecting))
        }
    }

    private fun <T : Preference> preference(key: String): T = checkNotNull(findPreference<T>(key))

    private fun configureDeviceName() {
        val name = preference<EditTextPreference>("lan_name")
        name.text = LanReceiverService.name(app)
        name.summary = name.text
        name.setOnBindEditTextListener { editor ->
            editor.setSingleLine(true)
            editor.filters = arrayOf(InputFilter.LengthFilter(40))
            editor.setSelection(editor.text.length)
        }
        name.setOnPreferenceChangeListener { _, value ->
            val trimmed = value.toString().trim()
            if (trimmed.isEmpty() || trimmed.length > 40) {
                showStatus(R.string.lan_name_required)
                false
            } else {
                LanReceiverService.prefs(app).edit().putString("name", trimmed).apply()
                name.text = trimmed
                name.summary = trimmed
                if (LanReceiverService.running) {
                    Toast.makeText(app, R.string.lan_name_saved, Toast.LENGTH_LONG).show()
                }
                false
            }
        }
    }

    private fun requestReceiver() {
        if (receiverStartingAt != 0L) return
        if (Preferences.getServer().isNullOrBlank()) {
            showStatus(R.string.lan_login_first)
            return
        }
        requestingReceiver = true
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= 33) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        if (Build.VERSION.SDK_INT >= 37) needed.add("android.permission.ACCESS_LOCAL_NETWORK")
        val missing = needed.filter { ContextCompat.checkSelfPermission(app, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) startReceiver() else permission.launch(missing.toTypedArray())
    }

    private fun startReceiver() {
        try {
            LanReceiverService.error = null
            receiverStopping = false
            receiverStartingAt = SystemClock.elapsedRealtime()
            ContextCompat.startForegroundService(app, Intent(app, LanReceiverService::class.java))
            renderReceiver()
        } catch (_: Exception) {
            receiverStartingAt = 0L
            showStatus(R.string.lan_connection_error)
            renderReceiver()
        }
    }

    private fun stopReceiver() {
        receiverStartingAt = 0L
        receiverStopping = true
        LanReceiverService.closePairing()
        app.stopService(Intent(app, LanReceiverService::class.java))
        renderReceiver()
    }

    private fun renderReceiver() {
        val now = SystemClock.elapsedRealtime()
        if (LanReceiverService.running) receiverStartingAt = 0L
        if (!LanReceiverService.running) receiverStopping = false
        if (receiverStartingAt != 0L && (LanReceiverService.error != null || now - receiverStartingAt > 10000)) {
            receiverStartingAt = 0L
            showStatus(R.string.lan_connection_error)
        }
        val enabled = LanReceiverService.running && !receiverStopping
        receiver.isChecked = enabled || receiverStartingAt != 0L
        receiver.summary = getString(if (receiverStartingAt != 0L) R.string.lan_connecting else R.string.lan_receiver_summary)
        pairing.isVisible = enabled
        paired.isVisible = enabled
        val open = enabled && now < LanReceiverService.pairingUntil
        pairing.isChecked = open
        pairing.summary = if (open) {
            val seconds = (LanReceiverService.pairingUntil - now + 999) / 1000
            getString(R.string.lan_pairing_remaining, "%d:%02d".format(seconds / 60, seconds % 60))
        } else getString(R.string.lan_pairing_closed)
        renderPairingRequests(open, now)
    }

    private fun renderPairingRequests(open: Boolean, now: Long) {
        val requests = if (open) LanReceiverService.pending.values
            .filter { LanPolicy.mayApprove(now, LanReceiverService.pairingUntil, it.time) }
            .sortedBy { it.fingerprint } else emptyList()
        pendingRequests.isVisible = requests.isNotEmpty()
        val key = requests.joinToString { "${it.fingerprint}:${it.time}" }
        if (key == pendingKey) return
        pendingKey = key
        pendingRequests.removeAll()
        requests.forEach { request ->
            pendingRequests.addPreference(Preference(requireContext()).apply {
                title = request.name
                summary = getString(R.string.lan_pair_request, request.name, request.code)
                isPersistent = false
                setOnPreferenceClickListener {
                    showDialog(MaterialAlertDialogBuilder(requireContext())
                        .setTitle(R.string.lan_pair_title)
                        .setMessage(getString(R.string.lan_pair_request, request.name, request.code))
                        .setPositiveButton(R.string.lan_pair_confirm) { _, _ ->
                            if (!LanReceiverService.approve(app, request)) showStatus(R.string.lan_pairing_expired)
                            renderReceiver()
                        }.setNegativeButton(android.R.string.cancel, null).create())
                    true
                }
            })
        }
    }

    private fun showPairedControllers() {
        val fingerprints = LanReceiverService.trusted(app).sorted()
        if (fingerprints.isEmpty()) {
            showStatus(R.string.lan_no_paired)
            return
        }
        val prefs = LanReceiverService.prefs(app)
        val names = fingerprints.map { prefs.getString("peer-$it", getString(R.string.lan_unknown_device)) }.toTypedArray()
        showDialog(MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lan_paired)
            .setItems(names) { _, index ->
                val fingerprint = fingerprints[index]
                showDialog(MaterialAlertDialogBuilder(requireContext()).setTitle(names[index])
                    .setMessage(R.string.lan_revoke_question)
                    .setPositiveButton(R.string.lan_revoke) { _, _ ->
                        prefs.edit().putStringSet("trusted", LanReceiverService.trusted(app) - fingerprint)
                            .remove("peer-$fingerprint").apply()
                    }.setNegativeButton(android.R.string.cancel, null).create())
            }.setNegativeButton(android.R.string.cancel, null).create())
    }

    private fun search() {
        if (Build.VERSION.SDK_INT >= 37 && ContextCompat.checkSelfPermission(app, "android.permission.ACCESS_LOCAL_NETWORK") != PackageManager.PERMISSION_GRANTED) {
            requestingReceiver = false
            permission.launch(arrayOf("android.permission.ACCESS_LOCAL_NETWORK"))
            return
        }
        if (discoveryListener != null) return
        discovered.clear()
        renderDevices()
        val token = generation
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStopDiscoveryFailed(type: String, error: Int) = Unit
            override fun onStartDiscoveryFailed(type: String, error: Int) {
                main.post {
                    if (isCurrent(token)) {
                        discoveryListener = null
                        showStatus(R.string.lan_discovery_error)
                    }
                }
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { if (isCurrent(token)) { discovered[info.serviceName] = info; renderDevices() } }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { if (isCurrent(token)) { discovered.remove(info.serviceName); renderDevices() } }
            }
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(LanWire.TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            showStatus(R.string.lan_searching)
        } catch (_: Exception) {
            discoveryListener = null
            showStatus(R.string.lan_discovery_error)
        }
    }

    private fun renderDevices() {
        devices.removeAll()
        devices.isVisible = discovered.isNotEmpty()
        discovered.values.forEach { info ->
            devices.addPreference(Preference(requireContext()).apply {
                title = info.serviceName.removePrefix("Tempus ")
                summary = getString(R.string.lan_select_receiver)
                setIcon(R.drawable.ic_navigate_next)
                isPersistent = false
                setOnPreferenceClickListener { resolve(info); true }
            })
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(info: NsdServiceInfo) {
        if (resolving || busy) return
        resolving = true
        showStatus(R.string.lan_connecting)
        val token = generation
        try {
            nsd.resolveService(info, object : NsdManager.ResolveListener {
                override fun onResolveFailed(service: NsdServiceInfo, error: Int) {
                    main.post { if (isCurrent(token)) { resolving = false; showStatus(R.string.lan_connection_error) } }
                }
                override fun onServiceResolved(service: NsdServiceInfo) {
                    main.post { if (isCurrent(token)) { resolving = false; connectOrPair(service) } }
                }
            })
        } catch (_: Exception) {
            resolving = false
            showStatus(R.string.lan_connection_error)
        }
    }

    private fun connectOrPair(info: NsdServiceInfo) {
        if (busy) return
        busy = true
        val token = generation
        val name = LanReceiverService.name(app)
        operation = io.submit {
            val result = runCatching { pairingClient.hello(info, name) }
            main.post {
                if (!isCurrent(token)) return@post
                busy = false
                result.onSuccess { reply ->
                    val prefs = LanReceiverService.prefs(app)
                    val known = prefs.getStringSet("receivers", emptySet())!!.contains(reply.fingerprint)
                    if (reply.approved && known) {
                        LanRemoteSession.connect(app, info, reply.fingerprint)
                        requireActivity().finish()
                    } else {
                        showDialog(MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.lan_pair_title)
                            .setMessage(getString(R.string.lan_pair_compare, reply.code))
                            .setPositiveButton(R.string.lan_pair_confirm) { _, _ ->
                                val receivers = prefs.getStringSet("receivers", emptySet())!!.toSet() + reply.fingerprint
                                prefs.edit().putStringSet("receivers", receivers).apply()
                                connectOrPair(info)
                            }.setNegativeButton(android.R.string.cancel, null).create())
                    }
                }.onFailure { showConnectionFailure(it) }
            }
        }
    }

    private fun showConnectionFailure(failure: Throwable) {
        showStatus(when ((failure as? LanConnectionFailure)?.stage) {
            LanConnectionFailure.Stage.IDENTITY -> R.string.lan_identity_error
            LanConnectionFailure.Stage.CONNECT -> R.string.lan_connect_error
            LanConnectionFailure.Stage.TLS -> R.string.lan_tls_error
            LanConnectionFailure.Stage.VERIFY -> R.string.lan_identity_mismatch
            LanConnectionFailure.Stage.RESPONSE -> R.string.lan_response_error
            null -> R.string.lan_connection_error
        })
    }

    private fun showStatus(resource: Int) {
        status.setSummary(resource)
    }

    // Network callbacks from a previous screen lifetime must not update new UI
    // or select a receiver after the user has navigated away.
    private fun isCurrent(token: Long): Boolean = active && token == generation

    private fun showDialog(next: AlertDialog) {
        dialog?.dismiss()
        dialog = next
        next.show()
    }

    private val refresh = object : Runnable {
        override fun run() {
            if (!active) return
            renderReceiver()
            LanReceiverService.error?.let { status.summary = it }
            main.postDelayed(this, 1000)
        }
    }

    override fun onStart() {
        super.onStart()
        active = true
        main.post(refresh)
    }

    override fun onStop() {
        // Only screen work is cancelled. The receiver's two-minute pairing window
        // and the selected playback destination survive rotation/navigation.
        active = false
        generation++
        resolving = false
        busy = false
        main.removeCallbacks(refresh)
        discoveryListener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        discoveryListener = null
        operation?.cancel(true)
        pairingClient.cancel()
        dialog?.dismiss()
        dialog = null
        super.onStop()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }
}
