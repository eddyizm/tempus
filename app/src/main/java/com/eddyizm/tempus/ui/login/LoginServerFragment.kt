package com.eddyizm.tempus.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.URLUtil
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.databinding.FragmentLoginServerBinding
import com.eddyizm.tempus.model.Server
import com.eddyizm.tempus.subsonic.utils.StringUtil
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.viewmodel.ServerViewModel
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

private const val ARG_SINGLE_PAGE_MODE = "single_page_mode"

class LoginServerFragment : Fragment() {
    private var singlePageMode: Boolean = false

    private var _binding: FragmentLoginServerBinding? = null // memory-leak safe
    private val binding // only valid between onCreateView and onDestroyView.
        get() = _binding!!
    private lateinit var serverViewModel: ServerViewModel
    private lateinit var serverList: List<Server>
    private var selectedServerPosition: Int = 0
    private var isInitialSyncDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            singlePageMode = it.getBoolean(ARG_SINGLE_PAGE_MODE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginServerBinding.inflate(inflater, container, false)

        init()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // release from memory
    }

    fun init() {
        setupServerDropdownSelector()
        initTestButton()
        initCreateOrUpdateButton()
        initDeleteButton()
        initOldLoginButton()
        initLocalUrlSwitch()
        initClientCertSwitch()
    }

    @OptIn(UnstableApi::class)
    fun setupServerDropdownSelector() {
        serverViewModel = ViewModelProvider(this)[ServerViewModel::class.java]
        initServerListSync()
        initServerListSelector()
    }

    /**
     * Keeps the dropdown list up-to-date with changes on the database
     */
    fun initServerListSync() {

        val defaultServer = Server(
            serverId = "Unselected",
            serverName = "Add new server",
            username = "",
            password = "",
            address = "",
            localAddress = "",
            timestamp = 0,
            isLowSecurity = false,
            clientCert = ""
        )

        serverViewModel.allServers.observe(viewLifecycleOwner) { servers ->
            serverList = listOf(defaultServer) + (servers?.map { server ->
                Server(
                    serverId = server.serverId,
                    serverName = server.serverName,
                    username = server.username,
                    password = server.password,
                    address = server.address,
                    localAddress = server.localAddress,
                    timestamp = server.timestamp,
                    isLowSecurity = server.isLowSecurity,
                    clientCert = server.clientCert
                )
            } ?: emptyList())
            val adapter = ArrayAdapter(
                requireContext(),
                R.layout.item_login_server2,
                serverList.map { it.serverName }.toTypedArray()
            )
            adapter.setDropDownViewResource(R.layout.item_login_server2)
            binding.serversList.setAdapter(adapter)
            // Don't start dropdown with blank item, use the first dummy item
            if (!isInitialSyncDone && serverList.isNotEmpty()) {
                binding.serversList.setText(serverList[0].serverName, false)
                binding.createOrUpdateButton.text = getString(R.string.la_server_button_create)
                binding.deleteButton.isEnabled = false
                binding.testButton.isEnabled = false
                isInitialSyncDone = true
            }
        }
    }

    /**
     * React to server selection and trigger a custom action
     */
    fun initServerListSelector() {
        binding.serversList.setOnItemClickListener { _, _, position, _ ->
            selectedServerPosition = position
            if (position == 0) {
                onFirstServerSelected()
            } else {
                onNonFirstServerSelected(position)
            }
        }
    }

    private fun onFirstServerSelected() {
        binding.createOrUpdateButton.text = getString(R.string.la_server_button_create)
        binding.deleteButton.isEnabled = false
        binding.testButton.isEnabled = false
        binding.serverNameField.setText("")
        binding.serverUserField.setText("")
        binding.serverPasswordField.setText("")
        binding.serverPublicUrlField.setText("")
        binding.serverLocalUrlField.setText("")
        binding.serverCertField.setText("")
    }

    private fun onNonFirstServerSelected(position: Int) {
        binding.createOrUpdateButton.text = getString(R.string.la_server_button_update)
        binding.deleteButton.isEnabled = true
        binding.testButton.isEnabled = true
        binding.serverNameField.setText(serverList[position].serverName)
        binding.serverUserField.setText(serverList[position].username)
        binding.serverPasswordField.setText(serverList[position].password)
        binding.serverPublicUrlField.setText(serverList[position].address)

        /* Optional field */
        val localAddress: String? = serverList[position].localAddress
        if (!localAddress.isNullOrEmpty()) {
            binding.serverLocalUrlSwitch.isChecked = true
            binding.serverLocalUrlFieldContainer.visibility = View.VISIBLE
            binding.serverLocalUrlField.setText(localAddress)
        } else {
            binding.serverLocalUrlField.setText("") // migrate old null to string
        }

        /* Optional field */
        val clientCert: String? = serverList[position].clientCert
        if (!clientCert.isNullOrEmpty()) {
            binding.serverCertSwitch.isChecked = true
            binding.serverCertFieldContainer.visibility = View.VISIBLE
            binding.serverCertField.setText(clientCert)
        } else {
            binding.serverCertField.setText("") // migrate old null to string
        }
    }

    private fun updateSelectedServer(position: Int) {
        if (position == 0) {
            onFirstServerSelected()
            selectedServerPosition = 0 // particularly for the delete button
        } else {
            onNonFirstServerSelected(position)
        }
        binding.serversList.setText(serverList[position].serverName, false)
        binding.serversList.performCompletion()
    }

    @OptIn(UnstableApi::class)
    fun initTestButton() {
        binding.testButton.setOnLongClickListener {
            updateLegacySharedPreferences()
            requireActivity().finish()
            val tempus = Intent(context, MainActivity::class.java)
            startActivity(tempus)
            return@setOnLongClickListener true
        }
        binding.testButton.setOnClickListener {
            testConnection()
        }
    }

    fun initDeleteButton() {
        binding.deleteButton.setOnClickListener {
            serverViewModel.deleteServer(serverList[selectedServerPosition])
            updateSelectedServer(0)
            Toast.makeText(
                context,
                getString(R.string.la_server_toast_deleted),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    /* DEPRECATION WARNING
    *
    * This button has been hidden from the UI in favor of the Top AppBar.
    *
    * This needs to be refactored to provide a true 'Login' button,
    * set a propper id name and send the correct intent to MainActivity.
    *
    * This will become an entrypoint for the LoginActivity -> MainActivity workflow,
    * and will become the guard that prevents users from crashing MainActivity with invalid creds.
    *
    *  */
    @OptIn(UnstableApi::class)
    fun initOldLoginButton() {
        binding.button5.setOnClickListener {
            requireActivity().finish()
            val tempus = Intent(requireActivity(), MainActivity::class.java).apply {
                putExtra("LOGIN_ACTIVITY_INTENT", "open_legacy_login_fragment")
            }
            startActivity(tempus)
        }
    }

    fun initLocalUrlSwitch() {
        binding.serverLocalUrlSwitch.setOnClickListener {
            if (binding.serverLocalUrlSwitch.isChecked) {
                binding.serverLocalUrlFieldContainer.visibility = View.VISIBLE
                // elvis operator because old configs save null
                binding.serverLocalUrlField.setText(serverList[selectedServerPosition].localAddress ?: "")
            } else {
                binding.serverLocalUrlFieldContainer.visibility = View.GONE
                binding.serverLocalUrlField.setText("")
            }

        }
    }

    fun initClientCertSwitch() {
        binding.serverCertSwitch.setOnClickListener {
            if (binding.serverCertSwitch.isChecked) {
                binding.serverCertFieldContainer.visibility = View.VISIBLE
                // elvis operator because old configs save null
                binding.serverCertField.setText(serverList[selectedServerPosition].clientCert ?: "")
            } else {
                binding.serverCertFieldContainer.visibility = View.GONE
                binding.serverCertField.setText("")
            }
        }
    }

    fun updateLegacySharedPreferences() {

        val s: Server = serverList[selectedServerPosition]

        val server: String = s.serverName
        val user: String = s.username
        val password: String = s.password
        val address: String = s.address
        val localAddress: String = s.localAddress ?: s.address
        val clientCert: String = s.clientCert ?: ""

        App.getInstance().preferences.edit { putString("server", server) }
        App.getInstance().preferences.edit { putString("user", user) }
        App.getInstance().preferences.edit { putString("password", password) }
        App.getInstance().preferences.edit { putString("in_use_server_address", address) }
        App.getInstance().preferences.edit { putString("local_address", localAddress) }
        App.getInstance().preferences.edit { putString("client_cert", clientCert) }

        App.getSubsonicClientInstance(true)

    }

    fun initCreateOrUpdateButton() {

        binding.createOrUpdateButton.setOnClickListener {

            if (!inputSanitization()) return@setOnClickListener

            var serverId: String
            if (selectedServerPosition == 0) { // New server, we use db_row_total+1 as primary key
                serverId = (serverList.count() + 1).toString()
            } else { // Known server, we use its original primary key (whatever it is set to)
                serverId = serverList[selectedServerPosition].serverId
            }

            val newServer = Server(
                serverId = serverId,
                serverName = binding.serverNameField.text.toString(),
                username = binding.serverUserField.text.toString(),
                password = binding.serverPasswordField.text.toString(),
                address = binding.serverPublicUrlField.text.toString(),
                localAddress = binding.serverLocalUrlField.text.toString(),
                timestamp = System.currentTimeMillis(),
                isLowSecurity = binding.serverPlaintextPassowrd.isChecked,
                clientCert = binding.serverCertField.text.toString()
            )

            if (selectedServerPosition == 0) {
                serverViewModel.insertServer(newServer)
                Toast.makeText(
                    context,
                    getString(R.string.la_server_toast_created),
                    Toast.LENGTH_SHORT
                ).show()
                updateSelectedServer(0)
            } else {
                serverViewModel.updateServer(newServer)
                Toast.makeText(
                    context,
                    getString(R.string.la_server_toast_updated),
                    Toast.LENGTH_SHORT
                ).show()
                updateSelectedServer(0)
            }
        }
    }

    private fun inputSanitization(): Boolean {

        val errMsgEmpty: String = getString(R.string.la_server_field_server_empty_error)
        val errMsgUrl: String = getString(R.string.la_server_field_server_url_error)
        if (binding.serverNameField.text.toString().isEmpty()) {
            binding.serverNameField.error = errMsgEmpty
            return false
        } else if (binding.serverUserField.text.toString().isEmpty()) {
            binding.serverUserField.error = errMsgEmpty
            return false
        } else if (binding.serverPasswordField.text.toString().isEmpty()) {
            binding.serverPasswordField.error = errMsgEmpty
            return false
        } else if (binding.serverPublicUrlField.text.toString().isEmpty()) {
            binding.serverPublicUrlField.error = errMsgEmpty
            return false
        } else if (!urlSanitization(binding.serverPublicUrlField.text.toString())) {
            binding.serverPublicUrlField.error = errMsgUrl
            return false
        } else if (binding.serverLocalUrlSwitch.isChecked
            && binding.serverLocalUrlField.text.toString().isEmpty()) {
            binding.serverLocalUrlField.error = errMsgEmpty
            return false
        } else if (binding.serverLocalUrlSwitch.isChecked
            && !urlSanitization(binding.serverLocalUrlField.text.toString())
        ) {
            binding.serverLocalUrlField.error = errMsgUrl
            return false
        }
        return true
    }

    private fun urlSanitization(url: String): Boolean {
        if (!URLUtil.isValidUrl(url)) {
            return false
        } else if (url.toHttpUrlOrNull() == null) {
            return false
        }
        return true
    }

    private fun testConnection() {
        binding.testButton.isEnabled = false
        Toast.makeText(
            context,
            getString(R.string.la_server_toast_connection_testing),
            Toast.LENGTH_SHORT
        ).show()

        val serverUrl = serverList[selectedServerPosition].address
        val username = serverList[selectedServerPosition].username
        val password = serverList[selectedServerPosition].password
        val clientName = "Tempus"
        val apiVersion = "1.16.0"
        val url: String

        if (serverList[selectedServerPosition].isLowSecurity) {
            url = "$serverUrl/rest/ping.view?u=$username&p=$password&v=$apiVersion&c=$clientName&f=json"
        } else {
            val salt = UUID.randomUUID().toString().substring(0, 6)
            val token = StringUtil.tokenize(password + salt)
            url = "$serverUrl/rest/ping.view?u=$username&t=$token&s=$salt&v=$apiVersion&c=$clientName&f=json"
        }

        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                binding.testButton.post {
                    binding.testButton.isEnabled = true
                    Toast.makeText(
                        context,
                        getString(R.string.la_server_toast_connection_error) + e.localizedMessage,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    var isSubsonicOk = false

                    if (response.isSuccessful) {
                        val responseBody = response.body?.string()
                        if (responseBody != null) {
                            try {
                                val jsonRoot = JSONObject(responseBody)
                                val subsonicResponse =
                                    jsonRoot.getJSONObject("subsonic-response")
                                if (subsonicResponse.getString("status") == "ok") {
                                    isSubsonicOk = true
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }

                    // Switch back to Main Thread to update UI
                    binding.testButton.post {
                        binding.testButton.isEnabled = true
                        if (isSubsonicOk) {
                            Toast.makeText(
                                context,
                                getString(R.string.la_server_toast_connection_success),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            Toast.makeText(
                                context,
                                getString(R.string.la_server_toast_connection_failure),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            }
        })
    }

    companion object {
        @JvmStatic
        fun newInstance(singlePageMode: Boolean = false): LoginServerFragment =
            LoginServerFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SINGLE_PAGE_MODE, singlePageMode)
                }
            }
    }
}