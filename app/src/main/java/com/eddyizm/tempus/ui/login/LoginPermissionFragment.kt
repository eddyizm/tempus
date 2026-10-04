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

    private val requestInternetLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        updatePermissionStates()
    }

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

        // Internet Permission
        binding.btnRequestInternet.setOnClickListener {
            requestInternetLauncher.launch(Manifest.permission.INTERNET)
        }

        // Notification Permission (API 33+)
        binding.btnRequestNotification.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Local Network Permission (API 37+)
        binding.btnRequestLocalNetwork.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 37) {
                requestLocalNetworkLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        }

        // Nearby Devices Permission (API 33+)
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
        updateInternetState()
        updateNotificationState()
        updateLocalNetworkState()
        updateNearbyDevicesState()
    }

    private fun updateInternetState() {
        val hasInternet = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

        binding.tvInternetProvided.visibility = if (hasInternet) View.VISIBLE else View.GONE
        binding.btnRequestInternet.visibility = if (hasInternet) View.GONE else View.VISIBLE
    }

    private fun updateNotificationState() {
        val hasNotification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        binding.tvNotificationProvided.visibility = if (hasNotification) View.VISIBLE else View.GONE
        binding.btnRequestNotification.visibility = if (hasNotification) View.GONE else View.VISIBLE
    }

    private fun updateLocalNetworkState() {
        val hasLocalNetwork = Build.VERSION.SDK_INT < 37 ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

        binding.tvLocalNetworkProvided.visibility = if (hasLocalNetwork) View.VISIBLE else View.GONE
        binding.btnRequestLocalNetwork.visibility = if (hasLocalNetwork) View.GONE else View.VISIBLE
    }

    private fun updateNearbyDevicesState() {
        val hasNearbyDevices = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED

        binding.tvNearbyDevicesProvided.visibility = if (hasNearbyDevices) View.VISIBLE else View.GONE
        binding.btnRequestNearbyDevices.visibility = if (hasNearbyDevices) View.GONE else View.VISIBLE
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