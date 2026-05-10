package com.cappielloantonio.tempo.ui.activity;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.splashscreen.SplashScreen;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.ActivityCrashBinding;
import com.cappielloantonio.tempo.ui.fragment.CrashExportFragment;
import com.cappielloantonio.tempo.ui.fragment.CrashInfoFragment;
import com.cappielloantonio.tempo.ui.fragment.CrashLogsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.navigation.NavigationView;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import cat.ereza.customactivityoncrash.config.CaocConfig;

@UnstableApi
public class CrashActivity extends AppCompatActivity {
    private static final String TAG = "MainActivityLogs";
    ActivityCrashBinding bind;
    String stackTraceFromIntent;
    CaocConfig configFromIntent;
    private Toolbar toolbar;
    private BottomNavigationView bottomNav;

    private DrawerLayout drawerLayout;
    private NavigationView navView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        bind = ActivityCrashBinding.inflate(getLayoutInflater());
        View view = bind.getRoot();
        setContentView(view);

        stackTraceFromIntent = CustomActivityOnCrash.getStackTraceFromIntent(getIntent());
        configFromIntent = CustomActivityOnCrash.getConfigFromIntent(getIntent());

        init();
        initAppBar();
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bind = null;
    }

    private void init() {
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_crash);

        toolbar = findViewById(R.id.crash_toolbar);
        FrameLayout contentFrame = findViewById(R.id.crash_content_frame);

        // These will be null if the view is not present in the current layout
        bottomNav = findViewById(R.id.crash_bottom_nav);
        drawerLayout = findViewById(R.id.crash_drawer_layout);
        navView = findViewById(R.id.crash_nav_view);

        if (bottomNav != null) {
            setupBottomNav(bottomNav);
        }

        if (drawerLayout != null && navView != null) {
            setupDrawer(drawerLayout, navView);
        }
    }

    private void initAppBar() {

        int orientation = getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (isLandscape) {
            ActionBarDrawerToggle drawerToggle = new ActionBarDrawerToggle(
                    this,
                    drawerLayout,
                    toolbar,
                    R.string.toolbar_navigation_drawer_open,
                    R.string.toolbar_navigation_drawer_closed
            );
            drawerLayout.addDrawerListener(drawerToggle);
            drawerToggle.syncState();
        }
    }

    public String getStackTrace() {
        return stackTraceFromIntent;
    }

    public CaocConfig getConfigFromIntent() {
        return configFromIntent;
    }

    private void setupBottomNav(BottomNavigationView nav) {
        nav.setOnNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.crash_nav_info) {
                loadFragment(new CrashInfoFragment());
                return true;
            } else if (itemId == R.id.crash_nav_logs) {
                loadFragment(new CrashLogsFragment());
                return true;
            } else if (itemId == R.id.crash_nav_export) {
                loadFragment(new CrashExportFragment());
                return true;
            }
            return false;
        });

        // nav.setSelectedItemId(R.id.crash_nav_info);
        loadFragment(new CrashInfoFragment());
        nav.getMenu().findItem(R.id.crash_nav_info).setChecked(true);}

    private void setupDrawer(DrawerLayout drawer, NavigationView navigationView) {
        navigationView.setNavigationItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.crash_nav_info) {
                loadFragment(new CrashInfoFragment());
            } else if (itemId == R.id.crash_nav_logs) {
                loadFragment(new CrashLogsFragment());
            } else if (itemId == R.id.crash_nav_export) {
                loadFragment(new CrashExportFragment());
            }
            drawer.closeDrawers();
            return true;
        });
        loadFragment(new CrashInfoFragment());
        navigationView.setCheckedItem(R.id.crash_nav_info);
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.crash_content_frame, fragment)
                .setReorderingAllowed(true)
                .addToBackStack(null)
                .commit();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }
}
