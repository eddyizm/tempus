package com.cappielloantonio.tempo.ui.activity

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.databinding.ActivityLoginBinding
import com.cappielloantonio.tempo.model.Server
import com.cappielloantonio.tempo.viewmodel.ServerViewModel
import kotlin.jvm.java
import androidx.core.content.edit

import com.cappielloantonio.tempo.R


class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var serverViewModel: ServerViewModel
    private lateinit var serverList: List<Server>
    private var selectedServerId: String = "Unselected"
    private var selectedServerPosition: Int = 0
    private var isInitialSyncDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        init()
    }

    fun init() {
        setupServerDropdownSelector()
        setupLoginButton()
        setupUpdateButton()
        setupDeleteButton()
        setupOldLoginButton()
    }

    @OptIn(UnstableApi::class)
    fun setupServerDropdownSelector() {
        serverViewModel = ViewModelProvider(this)[ServerViewModel::class.java]
        syncServerList()
        onServerSelected()
    }

    /**
     * Keeps the dropdown list up-to-date with changes on the database
     */
    fun syncServerList() {

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

        serverViewModel.allServers.observe(this) { servers ->
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
                this,
                R.layout.item_login_server2,
                serverList.map { it.serverName }.toTypedArray()
            )
            adapter.setDropDownViewResource(R.layout.item_login_server2)
            binding.serversList.setAdapter(adapter)
            // Don't start dropdown with blank item, use the first dummy item
            if (!isInitialSyncDone && serverList.isNotEmpty()) {
                binding.serversList.setText(serverList[0].serverName, false)
                binding.createOrUpdateButton.text = getString(R.string.la_button_create)
                binding.deleteButton.isEnabled = false
                binding.loginButton.isEnabled = false
                isInitialSyncDone = true
            }
        }
    }

    /**
     * React to server selection and trigger a custom action
     */
    fun onServerSelected() {
        binding.serversList.setOnItemClickListener { parent, _, position, _ ->
            selectedServerId = serverList[position].serverId
            selectedServerPosition = position

            if (position == 0) {
                binding.createOrUpdateButton.text = getString(R.string.la_button_create)
                binding.deleteButton.isEnabled = false
                binding.loginButton.isEnabled = false
                binding.serverNameField.setText("")
                binding.serverUserField.setText("")
                binding.serverPasswordField.setText("")
                binding.serverPublicUrlField.setText("")
                binding.serverLocalUrlField.setText("")
                binding.serverCertField.setText("")
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.la_toast_creating),
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                binding.createOrUpdateButton.text = getString(R.string.la_button_update)
                binding.deleteButton.isEnabled = true
                binding.loginButton.isEnabled = true
                val selectedServerName = parent.getItemAtPosition(position).toString()
                binding.serverNameField.setText(serverList[position].serverName)
                binding.serverUserField.setText(serverList[position].username)
                binding.serverPasswordField.setText("")
                binding.serverPublicUrlField.setText(serverList[position].address)
                binding.serverLocalUrlField.setText(serverList[position].localAddress)
                binding.serverCertField.setText(serverList[position].clientCert)
                Toast.makeText(
                    this@LoginActivity,
                    getString(R.string.la_toast_selected) + " " + selectedServerName,
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun setupLoginButton() {
        binding.loginButton.setOnClickListener {
            updateLegacySharedPreferences()
            finish()
            val tempus = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(tempus)
        }
    }

    fun setupDeleteButton() {
        binding.deleteButton.setOnClickListener {
            serverViewModel.deleteServer(serverList[selectedServerPosition])
        }
    }

    @OptIn(UnstableApi::class)
    fun setupOldLoginButton() {
        binding.button5.setOnClickListener {
            finish()
            val tempus = Intent(this@LoginActivity, MainActivity::class.java).apply {
                putExtra("LOGIN_ACTIVITY_INTENT", "open_legacy_login_fragment")
            }
            startActivity(tempus)
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

    fun setupUpdateButton() {

        binding.createOrUpdateButton.setOnClickListener {

            val errMsg: String = "Mandatory Field"
            if (binding.serverNameField.text.toString().isEmpty()) {
                binding.serverNameField.error = errMsg
                return@setOnClickListener
            } else if (binding.serverUserField.text.toString().isEmpty()) {
                binding.serverUserField.error = errMsg
                return@setOnClickListener
            } else if (binding.serverPasswordField.text.toString().isEmpty()) {
                binding.serverUserField.error = errMsg
                return@setOnClickListener
            } else if (binding.serverPublicUrlField.text.toString().isEmpty()) {
                binding.serverPublicUrlField.error = errMsg
                return@setOnClickListener
            }

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
                clientCert = ""
            )

            if (selectedServerPosition == 0) {
                serverViewModel.insertServer(newServer)
            } else {
                serverViewModel.updateServer(newServer)
            }
        }
    }
}