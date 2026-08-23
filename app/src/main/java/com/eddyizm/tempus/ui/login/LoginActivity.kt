package com.eddyizm.tempus.ui.login

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.eddyizm.tempus.databinding.ActivityLoginBinding
import com.eddyizm.tempus.R;
import com.eddyizm.tempus.helper.ThemeHelper
import com.google.android.material.tabs.TabLayoutMediator

private const val GREETER_FRAGMENT: Int     = 0
private const val PERMISSIONS_FRAGMENT: Int = 1
private const val THEMES_FRAGMENT: Int      = 2
private const val SERVERS_FRAGMENT: Int     = 3

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private var hideTabLayout: Boolean = false
    private var hideTopAppBar: Boolean = true
    private var selectedFragment: Int = GREETER_FRAGMENT

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeHelper.enableThemeSwitch(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initIntentHandler()
        initEdgeToEdge()
        initTopAppBarLayout()
        initTabLayout()
    }

    private fun initIntentHandler() {
        hideTabLayout    = intent.getBooleanExtra("HIDE_TAB_LAYOUT", false)
        hideTopAppBar    = intent.getBooleanExtra("HIDE_TOPAPPBAR_LAYOUT", true)

        selectedFragment = intent.getIntExtra("SELECT_FRAGMENT", GREETER_FRAGMENT)
    }

    private fun initEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun initTopAppBarLayout() {
        if (hideTopAppBar) {
            binding.topAppBar.visibility = View.GONE
            return
        }
        binding.topAppBar.setOnClickListener { finish() }
    }

    private fun initTabLayout() {

        binding.viewPager.adapter = ViewPagerAdapter(this)
        binding.viewPager.setCurrentItem(selectedFragment, false)

        if (hideTabLayout) {
            binding.tabLayout.visibility = View.GONE
            binding.viewPager.isUserInputEnabled = false
        }

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                GREETER_FRAGMENT     -> getString(R.string.la_tab_greeter)
                PERMISSIONS_FRAGMENT -> getString(R.string.la_tab_permission)
                THEMES_FRAGMENT      -> getString(R.string.la_tab_themes)
                SERVERS_FRAGMENT     -> getString(R.string.la_tab_server)
                else -> ""
            }
        }.attach()
    }

    private inner class ViewPagerAdapter(activity: AppCompatActivity) : FragmentStateAdapter(activity) {
        override fun getItemCount(): Int = 4

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                GREETER_FRAGMENT -> {
                    binding.topAppBar.title = getString(R.string.la_appbar_greeter_title)
                    LoginGreeterFragment.newInstance(
                        singlePageMode = hideTabLayout
                    )
                }
                PERMISSIONS_FRAGMENT -> {
                    binding.topAppBar.title = getString(R.string.la_appbar_permission_title)
                    LoginPermissionFragment.newInstance(
                        singlePageMode = hideTabLayout
                    )
                }
                THEMES_FRAGMENT -> {
                    binding.topAppBar.title = getString(R.string.la_appbar_themes_title)
                    LoginThemeFragment.newInstance(
                        singlePageMode = hideTabLayout
                    )
                }
                SERVERS_FRAGMENT -> {
                    binding.topAppBar.title = getString(R.string.la_appbar_server_title)
                    LoginServerFragment.newInstance(
                        singlePageMode = hideTabLayout
                    )
                }
                else -> LoginGreeterFragment.newInstance(
                    singlePageMode = hideTabLayout
                )
            }
        }
    }
}