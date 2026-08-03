package com.cappielloantonio.tempo.ui.login

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.cappielloantonio.tempo.databinding.ActivityLoginBinding
import com.cappielloantonio.tempo.util.ActivityUtil
import com.google.android.material.tabs.TabLayoutMediator

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        ActivityUtil.enableThemeSwitch(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initEdgeToEdge()
        initTabLayout()
    }

    private fun initEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initTabLayout() {
        binding.viewPager.adapter = ViewPagerAdapter(this)

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Welcome"
                1 -> "Permissions"
                2 -> "Themes"
                3 -> "Servers"
                else -> ""
            }
        }.attach()
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                0 -> LoginGreeterFragment()
                1 -> LoginPermissionFragment()
                2 -> LoginThemeFragment()
                3 -> LoginServerFragment()
                else -> LoginGreeterFragment()
            }
        }
    }


}