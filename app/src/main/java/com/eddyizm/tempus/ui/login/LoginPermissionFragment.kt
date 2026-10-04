package com.eddyizm.tempus.ui.login

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.eddyizm.tempus.databinding.FragmentLoginPermissionBinding

private const val ARG_SINGLE_PAGE_MODE = "single_page_mode"

class LoginPermissionFragment : Fragment() {
    private var singlePageMode: Boolean = false

    private var _binding: FragmentLoginPermissionBinding? = null
    private val binding get() = _binding!!

    private val requestNotificationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updatePermissionStates()
    }

    private val requestLocalNetworkLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updatePermissionStates()
    }

    private val requestNearbyDevicesLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updatePermissionStates()
    }

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
        _binding = FragmentLoginPermissionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRequestNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        binding.btnRequestLocalNetwork.setOnClickListener {
            val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
            requestLocalNetworkLauncher.launch(localNetworkPermission)
        }

        binding.btnRequestNearbyDevices.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNearbyDevicesLauncher.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
            }
        }

        updatePermissionStates()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
    }

    private fun updatePermissionStates() {
        updateNotificationState()
        updateLocalNetworkState()
        updateNearbyDevicesState()
    }

    private fun updateNotificationState() {
        try {
            val hasNotification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (hasNotification) {
                binding.tvNotificationProvided.visibility = View.VISIBLE
                binding.btnRequestNotification.visibility = View.GONE
            } else {
                binding.tvNotificationProvided.visibility = View.GONE
                binding.btnRequestNotification.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateLocalNetworkState() {
        try {
            val localNetworkPermission = "android.permission.ACCESS_LOCAL_NETWORK"
            val hasLocalNetwork = ContextCompat.checkSelfPermission(
                requireContext(),
                localNetworkPermission
            ) == PackageManager.PERMISSION_GRANTED

            if (hasLocalNetwork) {
                binding.tvLocalNetworkProvided.visibility = View.VISIBLE
                binding.btnRequestLocalNetwork.visibility = View.GONE
            } else {
                binding.tvLocalNetworkProvided.visibility = View.GONE
                binding.btnRequestLocalNetwork.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun updateNearbyDevicesState() {
        try {
            val hasNearbyDevices = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.NEARBY_WIFI_DEVICES
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                // Permission doesn't exist below API 33
                true
            }

            if (hasNearbyDevices) {
                binding.tvNearbyDevicesProvided.visibility = View.VISIBLE
                binding.btnRequestNearbyDevices.visibility = View.GONE
            } else {
                binding.tvNearbyDevicesProvided.visibility = View.GONE
                binding.btnRequestNearbyDevices.visibility = View.VISIBLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(singlePageMode: Boolean = false): LoginPermissionFragment =
            LoginPermissionFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SINGLE_PAGE_MODE, singlePageMode)
                }
            }
    }
}