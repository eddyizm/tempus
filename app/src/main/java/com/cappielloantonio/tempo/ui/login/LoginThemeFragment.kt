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

// TODO: Rename parameter arguments, choose names that match
// the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
private const val ARG_PARAM1 = "param1"
private const val ARG_PARAM2 = "param2"

/**
 * A simple [Fragment] subclass.
 * Use the [LoginThemeFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class LoginThemeFragment : Fragment() {
    // TODO: Rename and change types of parameters
    private var param1: String? = null
    private var param2: String? = null

    private var _binding: FragmentLoginThemeBinding? = null // memory-leak safe
    private val binding // only valid between onCreateView and onDestroyView.
        get() = _binding!!

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
        binding.buttonReturn.setOnClickListener {
            requireActivity().finish()
            val tempus = Intent(requireActivity(), MainActivity::class.java).apply {
                putExtra("LOGIN_ACTIVITY_INTENT", "open_legacy_settings_fragment")
            }
            startActivity(tempus)
        }
    }

    private fun setupThemeSelector() {
        val themeOptions = resources.getStringArray(R.array.theme_list_values).toList()

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            themeOptions
        ) {
            override fun getFilter(): android.widget.Filter {
                return object : android.widget.Filter() {
                    override fun performFiltering(constraint: CharSequence?): FilterResults {
                        val results = FilterResults()
                        results.values = themeOptions
                        results.count = themeOptions.size
                        return results
                    }
                    override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                        notifyDataSetChanged()
                    }
                }
            }
        }

        binding.dropdownThemeMode.setAdapter(adapter)
        binding.dropdownThemeMode.setText(Preferences.getTheme(),false)

        binding.dropdownThemeMode.setOnItemClickListener { _, _, position, _ ->

            when (themeOptions[position]) {
                ThemeHelper.DEFAULT_MODE -> {
                    Preferences.setTheme(ThemeHelper.DEFAULT_MODE)
                    ThemeHelper.applyTheme(ThemeHelper.DEFAULT_MODE)
                }

                ThemeHelper.LIGHT_MODE -> {
                    Preferences.setTheme(ThemeHelper.LIGHT_MODE)
                    ThemeHelper.applyTheme(ThemeHelper.LIGHT_MODE)
                }

                ThemeHelper.DARK_MODE -> {
                    Preferences.setTheme(ThemeHelper.DARK_MODE)
                    ThemeHelper.applyTheme(ThemeHelper.DARK_MODE)
                }
            }

            ThemeHelper.enableThemeSwitch(activity as AppCompatActivity)
            activity?.recreate()
        }
    }

    private fun setupDefaultAccentColorButtons() {
        binding.cardCoral.setOnClickListener {
            applyAccentColor("#FF5722")
        }
        binding.cardEmerald.setOnClickListener {
            applyAccentColor("#2E7D32")
        }
        binding.cardEmerald.setOnClickListener {
            applyAccentColor("#2E7D32")
        }
        binding.cardBlue.setOnClickListener {
            applyAccentColor("#1976D2")
        }
        binding.cardPurple.setOnClickListener {
            applyAccentColor("#7B1FA2")
        }
        binding.cardAmber.setOnClickListener {
            applyAccentColor("#FFA000")
        }
        binding.cardTeal.setOnClickListener {
            applyAccentColor("#00796B")
        }
        binding.cardSlate.setOnClickListener {
            applyAccentColor("#455A64")
        }
    }

    private fun applyAccentColor(hexString: String) {
        Preferences.setColorAccent("HEX:$hexString")
        ThemeHelper.enableThemeSwitch(activity as AppCompatActivity)
        activity?.recreate()
    }

    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment LoginThemeFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            LoginThemeFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_PARAM1, param1)
                    putString(ARG_PARAM2, param2)
                }
            }
    }
}