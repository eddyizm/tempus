package com.cappielloantonio.tempo.ui.fragment;

import static com.google.android.material.internal.ViewUtils.hideKeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.audiofx.AudioEffect;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputFilter;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreference;

import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentAlbumCatalogueBinding;
import com.cappielloantonio.tempo.databinding.FragmentSettingsBinding;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.interfaces.DialogClickCallback;
import com.cappielloantonio.tempo.interfaces.ScanCallback;
import com.cappielloantonio.tempo.service.EqualizerManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.DeleteDownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.DownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredAlbumSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredArtistSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StreamingCacheStorageDialog;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.util.ExternalAudioReader;
import com.cappielloantonio.tempo.viewmodel.SettingViewModel;

import java.util.Locale;
import java.util.Map;

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

    private void initAppBar() {
        bind.settingsToolbar.setNavigationOnClickListener(v -> {
            activity.navController.navigateUp();
        });
    }
}
