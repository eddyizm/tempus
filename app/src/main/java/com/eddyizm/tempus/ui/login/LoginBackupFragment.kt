package com.eddyizm.tempus.ui.login

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.Fragment
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.preference.PreferenceManager
import com.eddyizm.tempus.R
import com.eddyizm.tempus.databinding.FragmentLoginBackupBinding
import com.eddyizm.tempus.helper.ThemeHelper
import com.eddyizm.tempus.service.BaseMediaService
import com.eddyizm.tempus.service.BaseMediaService.LocalBinder
import com.eddyizm.tempus.service.DownloaderService
import com.eddyizm.tempus.service.MediaService
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.util.SettingsBackup
import com.eddyizm.tempus.util.SettingsBackup.keys
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val ARG_SINGLE_PAGE_MODE = "single_page_mode"
private const val SETTINGS_BACKUP_MAX_BYTES = 256 * 1024

@UnstableApi
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

    private val settingsImportLauncher: ActivityResultLauncher<Array<String>> = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importSettings(uri)
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

        binding.btnImport.setOnClickListener {
            settingsImportLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
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

    @OptIn(UnstableApi::class)
    private fun importSettings(uri: Uri) {

        val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
        val oldLanguage: String? = prefs.getString("language", "default")
        val oldEqualizer: String = prefs.getString("selected_equalizer", "0")!!

        val json = readTextFile(uri, SETTINGS_BACKUP_MAX_BYTES)

        val count: Int
        if (json != null) {
            count = SettingsBackup.importSettings(json, prefs, keys(requireContext()))
            Toast.makeText(requireContext(),
                getString(R.string.settings_imported, count),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            Toast.makeText(
                requireContext(),
                R.string.settings_import_invalid,
                Toast.LENGTH_LONG
            ).show()
            return
        }

        hotReloadSettings(
            prefs,
            oldLanguage,
            oldEqualizer)
    }

    private fun hotReloadSettings(
        prefs: android.content.SharedPreferences,
        oldLanguage: String?,
        oldEqualizer: String) {

        /* Hot-reload lan remote */
        if (!Preferences.isLanRemoteEnabled()) {
            com.eddyizm.tempus.lan.LanRemoteSession.disconnect()
            com.eddyizm.tempus.lan.LanReceiverService.closePairing()
            requireContext().stopService(Intent(requireContext(), com.eddyizm.tempus.lan.LanReceiverService::class.java))
        }

        /* Hot-reload download requirements */
        DownloadService.sendSetRequirements(
            requireContext(),
            DownloaderService::class.java,
            if (Preferences.isDownloadWifiOnly()) Requirements(Requirements.NETWORK_UNMETERED) else Requirements(0),
            false
        )

        /* Hot-reload equalizer */
//        if (oldEqualizer != prefs.getString("selected_equalizer", "0")) {
//            if (mediaServiceBinder != null) {
//                mediaServiceBinder?.reloadEqualizer()
//            } else {
//                pendingEqualizerReload = true
//            }
//        }

        /* Hot-reload theme */
        ThemeHelper.applyTheme(
            prefs.getString(
                Preferences.THEME,
                ThemeHelper.DEFAULT_MODE
            ) ?: ThemeHelper.DEFAULT_MODE
        )

        /* Hot-reload language */
        val language = prefs.getString("language", "default")
        if (language == oldLanguage) {
            requireActivity().recreate()
        } else {
            AppCompatDelegate.setApplicationLocales(
                if ("default" == language) LocaleListCompat.getEmptyLocaleList()
                else LocaleListCompat.forLanguageTags(language)
            )
        }
    }

    private fun readTextFile(uri: Uri, maxBytes: Int): String? {
        try {
            requireContext().contentResolver.openInputStream(uri).use { `in` ->
                if (`in` == null) return null
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var read: Int
                while ((`in`.read(buffer).also { read = it }) != -1 && bytes.size() <= maxBytes) {
                    bytes.write(buffer, 0, read)
                }
                return if (bytes.size() <= maxBytes) String(
                    bytes.toByteArray(),
                    StandardCharsets.UTF_8
                ) else
                    null
            }
        } catch (e: IOException) {
            return null
        } catch (e: SecurityException) {
            return null
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