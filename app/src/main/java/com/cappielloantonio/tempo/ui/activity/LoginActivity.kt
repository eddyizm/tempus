package com.cappielloantonio.tempo.ui.activity

import android.R
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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


class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var serverViewModel: ServerViewModel
    private lateinit var serverList: List<Server>
    private lateinit var spinnerServers: Spinner
    private var selectedServerId: String = "Unselected"
    private var selectedServerPosition: Int = 0

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
        spinnerServers = binding.serversList // Not sure why this would be null
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
                R.layout.simple_spinner_item,
                serverList.map { it.serverName }.toTypedArray()
            )
            adapter.setDropDownViewResource(R.layout.simple_spinner_dropdown_item)
            spinnerServers.adapter = adapter
        }
    }

    /**
     * React to server selection and trigger a custom action
     */
    fun onServerSelected() {
        spinnerServers.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                // Dummy implementation triggers a toast with the selection
                selectedServerId = serverList[position].serverId
                selectedServerPosition = position
                if (position == 0) {
                    binding.button2.text = "Create"
                    binding.button3.isEnabled = false
                    binding.serverNameField.setText("")
                    binding.serverUserField.setText("")
                    binding.serverPasswordField.setText("")
                    binding.serverPublicUrlField.setText("")
                    binding.serverLocalUrlField.setText("")
                    Toast.makeText(
                        this@LoginActivity,
                        "Creating new server",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    binding.button2.text = "Update"
                    binding.button3.isEnabled = true
                    val selectedServerName = parent.getItemAtPosition(position).toString()
                    binding.serverNameField.setText(serverList[position].serverName)
                    binding.serverUserField.setText(serverList[position].username)
                    binding.serverPasswordField.setText(serverList[position].password)
                    binding.serverPublicUrlField.setText(serverList[position].address)
                    binding.serverLocalUrlField.setText(serverList[position].localAddress)
                    Toast.makeText(
                        this@LoginActivity,
                        "Selected: $selectedServerName",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>) {
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun setupLoginButton() {
        binding.button4.setOnClickListener {
            updateLegacySharedPreferences()
            finish()
            val tempus = Intent(this@LoginActivity, MainActivity::class.java)
            startActivity(tempus)
        }
    }

    fun setupDeleteButton() {
        binding.button3.setOnClickListener {
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

        App.getInstance().preferences.edit { putString("server", server) }
        App.getInstance().preferences.edit { putString("user", user) }
        App.getInstance().preferences.edit { putString("password", password) }
        App.getInstance().preferences.edit { putString("in_use_server_address", address) }
        App.getInstance().preferences.edit { putString("local_address", localAddress) }

    }

    fun setupUpdateButton() {

        binding.button2.setOnClickListener {

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
            } else if (binding.serverLocalUrlField.text.toString().isEmpty()) {
                binding.serverLocalUrlField.error = errMsg
                return@setOnClickListener
            }

            var serverId: String
            if (selectedServerPosition == 0) { // New server, we use db_row_total+1 as primary key
                serverId = (serverList.count()+1).toString()
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
                isLowSecurity = binding.serverPlaintextPassowrd.isChecked == true,
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