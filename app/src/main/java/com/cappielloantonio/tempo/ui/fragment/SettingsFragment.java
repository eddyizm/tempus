package com.cappielloantonio.tempo.ui.fragment;

import static com.google.android.material.internal.ViewUtils.hideKeyboard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        activity = (MainActivity) getActivity();

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
        activity.setSystemBarsVisibility(!activity.isLandscape);
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.setBottomSheetVisibility(true);

        if (activity.isLandscape) {
            activity.setNavigationDrawerLock(false);
        } else if (Preferences.getEnableDrawerOnPortrait()) {
            activity.setNavigationDrawerLock(false);
        }
    }

    private void initAppBar() {
        bind.settingsToolbar.setNavigationOnClickListener(v -> {
            activity.navController.navigateUp();
        });
    }
}
