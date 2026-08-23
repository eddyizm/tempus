package com.eddyizm.tempus.ui.login

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
import androidx.core.graphics.toColorInt
import androidx.media3.common.util.UnstableApi
import com.eddyizm.tempus.App
import com.eddyizm.tempus.R
import com.eddyizm.tempus.databinding.FragmentLoginThemeBinding
import com.eddyizm.tempus.helper.ThemeHelper
import com.eddyizm.tempus.ui.activity.MainActivity
import com.eddyizm.tempus.util.Preferences
import com.eddyizm.tempus.ui.dialog.ColorPickerDialog

private const val ARG_SINGLE_PAGE_MODE = "single_page_mode"

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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySavedAccentColorToCard()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null // release from memory
    }

    private fun init() {
        initTrueBlackSwitch()
        initDynamicColorsSwitch()
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

    private fun initDynamicColorsSwitch() {
        binding.defaultDynamicSwitch.isChecked = Preferences.isDynamicColorAccent()
        binding.defaultDynamicSwitch.setOnClickListener {
            Preferences.setDynamicColorAccent(binding.defaultDynamicSwitch.isChecked)
            applyAccentColor(Preferences.getColorAccent())
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
        binding.cardFlirt.setOnClickListener {

            // We register a "listener" that the dialog can trigger when dismissed
            childFragmentManager.setFragmentResultListener("dialog_result_key", viewLifecycleOwner) { _, bundle ->
                val selectedColor = bundle.getString("color_key")
                if (selectedColor != null) {
                    applyAccentColor(selectedColor)
                }
                binding.cardFlirt.setCardBackgroundColor(
                    selectedColor
                        ?.removePrefix("HEX:")
                        ?.toColorInt()
                    ?: "#B5076B".toColorInt()
                )
            }

            /*
                We don't actually need to pass this argument,
                I implemented it because we can use it as future reference
                for much more complex behavior.
            */
            val dialog = ColorPickerDialog.newInstance(
                getString(R.string.la_theme_dialog_color_picker_title)
            )
            dialog.show(childFragmentManager, "ColorPickerDialog")
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

    private fun applySavedAccentColorToCard() {
        val savedColor = Preferences.getColorAccent()
        /*
            Remember, DYNAMIC is the default value.
            Actually, we should deprecate it in favor of
            the method isDynamicColorAccent()

            DYNAMIC is legacy behavior...
            jeez development moves fast

        */
        val colorInt = if (savedColor == "DYNAMIC" || savedColor.isEmpty()) {
            "#B5076B".toColorInt()
        } else {
            val rawHex = savedColor.removePrefix("HEX:")
            if (rawHex.startsWith("#")) {
                rawHex.toColorInt()
            } else {
                "#$rawHex".toColorInt() // May seem redundant but if unchecked the app blows up
            }
        }
        binding.cardFlirt.setCardBackgroundColor(colorInt)
    }

    companion object {
        @JvmStatic
        fun newInstance(singlePageMode: Boolean = false): LoginThemeFragment =
            LoginThemeFragment().apply {
                arguments = Bundle().apply {
                    putBoolean(ARG_SINGLE_PAGE_MODE, singlePageMode)
                }
            }
    }
}