package com.cappielloantonio.tempo.ui.login

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.databinding.FragmentLoginThemeBinding
import com.cappielloantonio.tempo.helper.ThemeHelper
import com.cappielloantonio.tempo.ui.activity.MainActivity
import com.cappielloantonio.tempo.util.Preferences

private const val ARG_SINGLE_PAGE_MODE = "standalone_page_view"

class LoginThemeFragment : Fragment() {
    private var singlePageMode: Boolean = false

    private var _binding: FragmentLoginThemeBinding? = null // memory-leak safe
    private val binding // only valid between onCreateView and onDestroyView.
        get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            singlePageMode = it.getBoolean(ARG_SINGLE_PAGE_MODE)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentLoginThemeBinding.inflate(inflater, container, false)

        init()

        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // release from memory
    }

    private fun init() {
        initTrueBlackSwitch()
        initButtonReturn()
        setupThemeSelector()
        setupDefaultAccentColorButtons()
    }

    private fun initTrueBlackSwitch() {
        if (App.getInstance().preferences.getBoolean("dark_theme_black", false)) {
            binding.trueBlackSwitch.isChecked = true
        }
        binding.trueBlackSwitch.setOnClickListener {
            if (binding.trueBlackSwitch.isChecked) {
                App.getInstance().preferences.edit { putBoolean("dark_theme_black", true) }
            } else {
                App.getInstance().preferences.edit { putBoolean("dark_theme_black", false) }
            }
            ThemeHelper.enableThemeSwitch(activity as AppCompatActivity)
            activity?.recreate()
        }
    }

    @OptIn(UnstableApi::class)
    private fun initButtonReturn() {
        if (!singlePageMode) {
            binding.buttonReturn.visibility = View.GONE
            return
        } else {
            binding.buttonReturn.visibility = View.VISIBLE
            binding.buttonReturn.setOnClickListener {
                requireActivity().finish()
                val tempus = Intent(requireActivity(), MainActivity::class.java).apply {
                    putExtra("LOGIN_ACTIVITY_INTENT", "open_legacy_settings_fragment")
                }
                startActivity(tempus)
            }
        }
    }

    private fun setupThemeSelector() {
        val themeOptions = resources.getStringArray(R.array.theme_list_titles).toList()
        val themeValues  = resources.getStringArray(R.array.theme_list_values)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            themeOptions
        )

        binding.themesList.apply {
            setAdapter(adapter)
            threshold = 0

            val showAllDropdown = {
                if (adapter.count > 0) {
                    adapter.getFilter().filter(null)
                    showDropDown()
                }
            }

            setOnClickListener {
                showAllDropdown()
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showAllDropdown()
            }

            val currentIndex = themeValues.indexOf(Preferences.getTheme())
            val initialText = if (currentIndex != -1 && currentIndex < themeOptions.size) {
                themeOptions[currentIndex]
            } else {
                Preferences.getTheme()
            }
            setText(initialText, false)

            setOnItemClickListener { _, _, position, _ ->
                val selectedValue = themeValues[position]
                ThemeHelper.applyTheme(selectedValue)
                Preferences.setTheme(selectedValue)
                ThemeHelper.enableThemeSwitch(activity as AppCompatActivity)
                activity?.recreate()
            }
        }
    }

    private fun setupDefaultAccentColorButtons() {
        binding.cardDefault.setOnClickListener {
            applyAccentColor("DYNAMIC")
        }
        binding.cardCoral.setOnClickListener {
            applyAccentColor("HEX:#FF5722")
        }
        binding.cardEmerald.setOnClickListener {
            applyAccentColor("HEX:#2E7D32")
        }
        binding.cardEmerald.setOnClickListener {
            applyAccentColor("HEX:#2E7D32")
        }
        binding.cardBlue.setOnClickListener {
            applyAccentColor("HEX:#1976D2")
        }
        binding.cardPurple.setOnClickListener {
            applyAccentColor("HEX:#7B1FA2")
        }
        binding.cardAmber.setOnClickListener {
            applyAccentColor("HEX:#FFA000")
        }
        binding.cardTeal.setOnClickListener {
            applyAccentColor("HEX:#00796B")
        }
        binding.cardSlate.setOnClickListener {
            applyAccentColor("HEX:#455A64")
        }
    }

    private fun applyAccentColor(accent: String) {
        Preferences.setColorAccent(accent)
        ThemeHelper.enableThemeSwitch(activity as AppCompatActivity)
        activity?.recreate()
    }

    companion object {
        @JvmStatic
        fun newInstance(singlePageMode: Boolean = false) =
            LoginThemeFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SINGLE_PAGE_MODE, singlePageMode)
                }
            }
    }
}