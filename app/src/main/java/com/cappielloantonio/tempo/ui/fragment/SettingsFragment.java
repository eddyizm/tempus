package com.cappielloantonio.tempo.ui.fragment;

import static com.google.android.material.internal.ViewUtils.hideKeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.MenuItem;
import android.widget.ImageView;
import androidx.appcompat.widget.SearchView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentSettingsBinding;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.util.Preferences;

public class SettingsFragment extends Fragment {

    private MainActivity activity;
    private FragmentSettingsBinding bind;
    private boolean isLandscape;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = (MainActivity) getActivity();

        int orientation = getResources().getConfiguration().orientation;
        isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = FragmentSettingsBinding.inflate(inflater,container,false);
        View view = bind.getRoot();

        initAppBar();

        return view;

    }

    @Override
    public void onViewCreated(@NonNull View view,
                              @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Add the PreferenceFragment only the first time
        if (savedInstanceState == null) {
            SettingsContainerFragment prefFragment = new SettingsContainerFragment();

            // Use the child fragment manager so the PreferenceFragment is scoped to this fragment
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings_container, prefFragment)
                    .setReorderingAllowed(true)   // optional but recommended
                    .commit();
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(false);
        activity.setBottomSheetVisibility(false);
        activity.setNavigationDrawerLock(true);
        activity.setSystemBarsVisibility(!isLandscape);
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.setBottomSheetVisibility(true);
        if (isLandscape) {
            activity.setNavigationDrawerLock(false);
        } else if (Preferences.getEnableDrawerOnPortrait()) {
            activity.setNavigationDrawerLock(false);
        }
    }

    private void initAppBar() {
        bind.settingsToolbar.inflateMenu(R.menu.settings_menu);
        MenuItem searchItem = bind.settingsToolbar.getMenu().findItem(R.id.action_search);
        if (searchItem != null) {
            SearchView searchView = (SearchView) searchItem.getActionView();
            if (searchView != null) {
                searchView.setMaxWidth(Integer.MAX_VALUE);
                searchView.setIconifiedByDefault(false);
                searchView.setQueryHint(getString(R.string.settings_search_hint));

                ImageView searchMagIcon = searchView.findViewById(androidx.appcompat.R.id.search_mag_icon);
                if (searchMagIcon != null) {
                    searchMagIcon.setImageDrawable(null);
                    searchMagIcon.setVisibility(View.GONE);
                }
                searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                    @Override
                    public boolean onQueryTextSubmit(String query) {
                        updateSearchQuery(query);
                        return true;
                    }

                    @Override
                    public boolean onQueryTextChange(String newText) {
                        updateSearchQuery(newText);
                        return true;
                    }
                });
            }
        }
        bind.settingsToolbar.setNavigationOnClickListener(v -> {
            activity.navController.navigateUp();
        });
    }

    private void updateSearchQuery(String query) {
        SettingsContainerFragment prefFragment = (SettingsContainerFragment) getChildFragmentManager()
                .findFragmentById(R.id.settings_container);
        if (prefFragment != null) {
            prefFragment.setSearchQuery(query);
        }
    }
}
