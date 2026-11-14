package com.eddyizm.tempus.ui.login

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.eddyizm.tempus.R
import com.eddyizm.tempus.databinding.FragmentLoginBackupBinding
import com.eddyizm.tempus.util.SettingsBackup
import com.eddyizm.tempus.util.SettingsBackup.keys
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ARG_SINGLE_PAGE_MODE = "single_page_mode"

class LoginBackupFragment : Fragment() {

    private var singlePageMode: Boolean = false

    private var _binding: FragmentLoginBackupBinding? = null
    private val binding get() = _binding!!

    private val settingsExportLauncher: ActivityResultLauncher<String> = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            writeSettingsToUri(uri)
        }
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
        _binding = FragmentLoginBackupBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnExport.setOnClickListener {
            val date = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
            val defaultFileName = "tempus_settings_$date.json"
            settingsExportLauncher.launch(defaultFileName)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun writeSettingsToUri(uri: Uri) {
        val json = SettingsBackup.exportSettings(
            PreferenceManager.getDefaultSharedPreferences(requireContext()),
            keys(requireContext())
        )
        try {
            requireContext().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(json.toByteArray(StandardCharsets.UTF_8))
                Toast.makeText(
                    requireContext(),
                    R.string.settings_exported,
                    Toast.LENGTH_SHORT
                ).show()
            } ?: throw IOException("No output stream")
        } catch (e: IOException) {
            Toast.makeText(
                requireContext(),
                R.string.settings_export_failed,
                Toast.LENGTH_LONG
            ).show()
        } catch (e: SecurityException) {
            Toast.makeText(
                requireContext(),
                R.string.settings_export_failed,
                Toast.LENGTH_LONG
            ).show()
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(singlePageMode: Boolean = false): LoginBackupFragment =
            LoginBackupFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SINGLE_PAGE_MODE, singlePageMode)
                }
            }
    }
}