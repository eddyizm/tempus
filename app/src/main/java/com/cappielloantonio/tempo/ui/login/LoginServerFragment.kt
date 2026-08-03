package com.cappielloantonio.tempo.ui.login

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.databinding.FragmentLoginEditorBinding
import com.cappielloantonio.tempo.model.Server
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.viewmodel.ServerViewModel

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [androidx.fragment.app.Fragment] subclass.
 * Use the [LoginServerFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LoginServerFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var _binding: FragmentLoginEditorBinding? = null // memory-leak safe
    private val binding // only valid between onCreateView and onDestroyView.
        get() = _binding!!
    private lateinit var serverViewModel: ServerViewModel
    private lateinit var serverList: List<Server>
    private var selectedServerId: String = "Unselected"
    private var selectedServerPosition: Int = 0
    private var isInitialSyncDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            param1 = it.getString(ARG_PARAM1)
            param2 = it.getString(ARG_PARAM2)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginEditorBinding.inflate(inflater, container, false)

        init()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // release from memory
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
                requireContext(),
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
                    context,
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
                binding.serverPasswordField.setText(serverList[position].password)
                binding.serverPublicUrlField.setText(serverList[position].address)
                binding.serverLocalUrlField.setText(serverList[position].localAddress)
                binding.serverCertField.setText(serverList[position].clientCert)
                Toast.makeText(
                    context,
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
            requireActivity().finish()
            val tempus = Intent(context, MainActivity::class.java)
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
            requireActivity().finish()
            val tempus = Intent(requireActivity(), MainActivity::class.java).apply {
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

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment LoginEditorFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            LoginServerFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}