package com.cappielloantonio.tempo.ui.activity

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.media3.common.util.UnstableApi
import com.cappielloantonio.tempo.App
import com.cappielloantonio.tempo.R
import com.cappielloantonio.tempo.databinding.ActivityLoginBinding
import com.cappielloantonio.tempo.model.Server
import com.cappielloantonio.tempo.ui.fragment.LoginEditorFragment
import com.cappielloantonio.tempo.ui.fragment.LoginGreeterFragment
import com.cappielloantonio.tempo.ui.fragment.LoginViewerFragment
import com.cappielloantonio.tempo.util.ActivityUtil
import com.cappielloantonio.tempo.viewmodel.ServerViewModel
import com.google.android.material.tabs.TabLayout


class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityUtil.enableThemeSwitch(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initEdgeToEdge()
        initTabLayout(savedInstanceState)
    }

    private fun initEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initTabLayout(bundle: Bundle?) {
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Greetings"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Editor"))
        binding.tabLayout.addTab(binding.tabLayout.newTab().setText("Viewer"))

        if (bundle == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, LoginGreeterFragment())
                .commit()
        }

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                val selectedFragment = when (tab.position) {
                    0 -> LoginGreeterFragment()
                    1 -> LoginEditorFragment()
                    2-> LoginViewerFragment()
                    else -> LoginGreeterFragment()
                }
                supportFragmentManager.beginTransaction()
                    .setCustomAnimations(
                        android.R.anim.fade_in,
                        android.R.anim.fade_out
                    )
                    .replace(R.id.fragmentContainer, selectedFragment)
                    .commit()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }


}