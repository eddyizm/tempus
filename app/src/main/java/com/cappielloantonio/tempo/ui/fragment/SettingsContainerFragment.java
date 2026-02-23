package com.cappielloantonio.tempo.ui.fragment;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
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
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
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
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.interfaces.DialogClickCallback;
import com.cappielloantonio.tempo.interfaces.ScanCallback;
import com.cappielloantonio.tempo.service.EqualizerManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.DeleteDownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.DownloadStorageDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredAlbumSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredArtistSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StarredSyncDialog;
import com.cappielloantonio.tempo.ui.dialog.StreamingCacheStorageDialog;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalAudioReader;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.UIUtil;
import com.cappielloantonio.tempo.viewmodel.SettingViewModel;

import java.util.Locale;
import java.util.Map;

@OptIn(markerClass = UnstableApi.class)
public class SettingsContainerFragment extends PreferenceFragmentCompat {

    private static final String TAG = "SettingsFragment";
    private MainActivity activity;

    private SettingViewModel settingViewModel;

    private ActivityResultLauncher<Intent> directoryPickerLauncher;

    private MediaService.LocalBinder mediaServiceBinder;
    private boolean isServiceBound = false;
    private ActivityResultLauncher<Intent> equalizerResultLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        equalizerResultLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {}
        );

        if (!BuildConfig.FLAVOR.equals("tempus")) {
            PreferenceCategory githubUpdateCategory = findPreference("settings_github_update_category_key");
            if (githubUpdateCategory != null) {
                getPreferenceScreen().removePreference(githubUpdateCategory);
            }
        }

        directoryPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        if (data != null) {
                            Uri uri = data.getData();
                            if (uri != null) {
                                requireContext().getContentResolver().takePersistableUriPermission(
                                        uri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                );

                                Preferences.setDownloadDirectoryUri(uri.toString());
                                ExternalAudioReader.refreshCache();
                                Toast.makeText(requireContext(), R.string.settings_download_folder_set, Toast.LENGTH_SHORT).show();
                                checkDownloadDirectory();
                            }
                        }
                    }
                });
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        View view = super.onCreateView(inflater, container, savedInstanceState);
        settingViewModel = new ViewModelProvider(requireActivity()).get(SettingViewModel.class);

        if (view != null) {
            getListView().setPadding(0, 0, 0, (int) getResources().getDimension(R.dimen.global_padding_bottom));
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        activity.setBottomNavigationBarVisibility(false);
        activity.setBottomSheetVisibility(false);
    }

    @Override
    public void onResume() {
        super.onResume();

        checkSystemEqualizer();
        checkCacheStorage();
        checkStorage();
        checkDownloadDirectory();

        setStreamingCacheSize();
        setAppLanguage();
        setVersion();
        setNetorkPingTimeoutBase();

        actionLogout();
        actionScan();
        actionSyncStarredAlbums();
        actionSyncStarredTracks();
        actionSyncStarredArtists();
        actionChangeStreamingCacheStorage();
        actionChangeDownloadStorage();
        actionSetDownloadDirectory();
        actionDeleteDownloadStorage();
        actionKeepScreenOn();
        actionAutoDownloadLyrics();
        actionMiniPlayerHeart();

        bindMediaService();
        actionAppEqualizer();
    }

    @Override
    public void onStop() {
        super.onStop();
        activity.setBottomSheetVisibility(true);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.global_preferences, rootKey);
        ListPreference themePreference = findPreference(Preferences.THEME);
        if (themePreference != null) {
            themePreference.setOnPreferenceChangeListener(
                    (preference, newValue) -> {
                        String themeOption = (String) newValue;
                        ThemeHelper.applyTheme(themeOption);
                        return true;
                    });
        }
    }

    private void checkSystemEqualizer() {
        Preference equalizer = findPreference("system_equalizer");

        if (equalizer == null) return;

        Intent intent = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);

        if ((intent.resolveActivity(requireActivity().getPackageManager()) != null)) {
            equalizer.setOnPreferenceClickListener(preference -> {
                equalizerResultLauncher.launch(intent);
                return true;
            });
        } else {
            equalizer.setVisible(false);
        }
    }

    private void checkCacheStorage() {
        Preference storage = findPreference("streaming_cache_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                storage.setSummary(Preferences.getStreamingCacheStoragePreference() == 0 ? R.string.download_storage_internal_dialog_negative_button : R.string.download_storage_external_dialog_positive_button);
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkStorage() {
        Preference storage = findPreference("download_storage");

        if (storage == null) return;

        try {
            if (requireContext().getExternalFilesDirs(null)[1] == null) {
                storage.setVisible(false);
            } else {
                int pref = Preferences.getDownloadStoragePreference();
                if (pref == 0) {
                    storage.setSummary(R.string.download_storage_internal_dialog_negative_button);
                } else if (pref == 1) {
                    storage.setSummary(R.string.download_storage_external_dialog_positive_button);
                } else {
                    storage.setSummary(R.string.download_storage_directory_dialog_neutral_button);
                }
            }
        } catch (Exception exception) {
            storage.setVisible(false);
        }
    }

    private void checkDownloadDirectory() {
        Preference storage = findPreference("download_storage");
        Preference directory = findPreference("set_download_directory");

        if (directory == null) return;

        String current = Preferences.getDownloadDirectoryUri();
        if (current != null) {
            if (storage != null) storage.setVisible(false);
            directory.setVisible(true);
            directory.setIcon(R.drawable.ic_close);
            directory.setTitle(R.string.settings_clear_download_folder);
            directory.setSummary(current);
        } else {
            if (storage != null) storage.setVisible(true);
            if (Preferences.getDownloadStoragePreference() == 2) {
                directory.setVisible(true);
                directory.setIcon(R.drawable.ic_folder);
                directory.setTitle(R.string.settings_set_download_folder);
                directory.setSummary(R.string.settings_choose_download_folder);
            } else {
                directory.setVisible(false);
            }
        }
    }

    private void setNetorkPingTimeoutBase() {
        EditTextPreference networkPingTimeoutBase = findPreference("network_ping_timeout_base");

        if (networkPingTimeoutBase != null) {
            networkPingTimeoutBase.setSummaryProvider(EditTextPreference.SimpleSummaryProvider.getInstance());
            networkPingTimeoutBase.setOnBindEditTextListener(editText -> {
                editText.setInputType(InputType.TYPE_CLASS_NUMBER);
                editText.setFilters(new InputFilter[]{ (source, start, end, dest, dstart, dend) -> {
                    for (int i = start; i < end; i++) {
                        if (!Character.isDigit(source.charAt(i))) {
                            return "";
                        }
                    }
                    return null;
                }});
            });

            networkPingTimeoutBase.setOnPreferenceChangeListener((preference, newValue) -> {
                String input = (String) newValue;
                return input != null && !input.isEmpty();
            });
        }
    }

    private void setStreamingCacheSize() {
        ListPreference streamingCachePreference = findPreference("streaming_cache_size");

        if (streamingCachePreference != null) {
            streamingCachePreference.setSummaryProvider(new Preference.SummaryProvider<ListPreference>() {
                @Nullable
                @Override
                public CharSequence provideSummary(@NonNull ListPreference preference) {
                    CharSequence entry = preference.getEntry();

                    if (entry == null) return null;

                    long currentSizeMb = DownloadUtil.getStreamingCacheSize(requireActivity()) / (1024 * 1024);

                    return getString(R.string.settings_summary_streaming_cache_size, entry, String.valueOf(currentSizeMb));
                }
            });
        }
    }

    private void setAppLanguage() {
        ListPreference localePref = (ListPreference) findPreference("language");

        Map<String, String> locales = UIUtil.getLangPreferenceDropdownEntries(requireContext());

        CharSequence[] entries = locales.keySet().toArray(new CharSequence[locales.size()]);
        CharSequence[] entryValues = locales.values().toArray(new CharSequence[locales.size()]);

        localePref.setEntries(entries);
        localePref.setEntryValues(entryValues);

        String value = localePref.getValue();
        if ("default".equals(value)) {
            localePref.setSummary(requireContext().getString(R.string.settings_system_language));
        } else {
            localePref.setSummary(Locale.forLanguageTag(value).getDisplayName());
        }

        localePref.setOnPreferenceChangeListener((preference, newValue) -> {
            if ("default".equals(newValue)) {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.getEmptyLocaleList());
                preference.setSummary(requireContext().getString(R.string.settings_system_language));
            } else {
                LocaleListCompat appLocale = LocaleListCompat.forLanguageTags((String) newValue);
                AppCompatDelegate.setApplicationLocales(appLocale);
                preference.setSummary(Locale.forLanguageTag((String) newValue).getDisplayName());
            }
            return true;
        });
    }

    private void setVersion() {
        findPreference("version").setSummary(BuildConfig.VERSION_NAME);
    }

    private void actionLogout() {
        findPreference("logout").setOnPreferenceClickListener(preference -> {
            activity.quit();
            return true;
        });
    }

    private void actionScan() {
        findPreference("scan_library").setOnPreferenceClickListener(preference -> {
            settingViewModel.launchScan(new ScanCallback() {
                @Override
                public void onError(Exception exception) {
                    findPreference("scan_library").setSummary(exception.getMessage());
                }

                @Override
                public void onSuccess(boolean isScanning, long count) {
                    findPreference("scan_library").setSummary(getString(R.string.settings_scan_result, count));
                    if (isScanning) getScanStatus();
                }
            });

            return true;
        });
    }

    private void actionSyncStarredTracks() {
        findPreference("sync_starred_tracks_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredSyncDialog dialog = new StarredSyncDialog(() -> {
                        ((SwitchPreference)preference).setChecked(false);
                    });
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionSyncStarredAlbums() {
        findPreference("sync_starred_albums_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredAlbumSyncDialog dialog = new StarredAlbumSyncDialog(() -> {
                        ((SwitchPreference)preference).setChecked(false);
                    });
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionSyncStarredArtists() {
        findPreference("sync_starred_artists_for_offline_use").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    StarredArtistSyncDialog dialog = new StarredArtistSyncDialog(() -> {
                        ((SwitchPreference)preference).setChecked(false);
                    });
                    dialog.show(activity.getSupportFragmentManager(), null);
                }
            }
            return true;
        });
    }

    private void actionChangeStreamingCacheStorage() {
        findPreference("streaming_cache_storage").setOnPreferenceClickListener(preference -> {
            StreamingCacheStorageDialog dialog = new StreamingCacheStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_external_dialog_positive_button);
                }

                @Override
                public void onNegativeClick() {
                    findPreference("streaming_cache_storage").setSummary(R.string.streaming_cache_storage_internal_dialog_negative_button);
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionChangeDownloadStorage() {
        findPreference("download_storage").setOnPreferenceClickListener(preference -> {
            DownloadStorageDialog dialog = new DownloadStorageDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_external_dialog_positive_button);
                    checkDownloadDirectory();
                }

                @Override
                public void onNegativeClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_internal_dialog_negative_button);
                    checkDownloadDirectory();
                }

                @Override
                public void onNeutralClick() {
                    findPreference("download_storage").setSummary(R.string.download_storage_directory_dialog_neutral_button);
                    checkDownloadDirectory();
                }
            });
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionSetDownloadDirectory() {
        Preference pref = findPreference("set_download_directory");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                String current = Preferences.getDownloadDirectoryUri();

                if (current != null) {
                    Preferences.setDownloadDirectoryUri(null);
                    Preferences.setDownloadStoragePreference(0);
                    ExternalAudioReader.refreshCache();
                    Toast.makeText(requireContext(), R.string.settings_download_folder_cleared, Toast.LENGTH_SHORT).show();
                    checkStorage();
                    checkDownloadDirectory();
                } else {
                    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                            | Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    directoryPickerLauncher.launch(intent);
                }
                return true;
            });
        }
    }

    private void actionDeleteDownloadStorage() {
        findPreference("delete_download_storage").setOnPreferenceClickListener(preference -> {
            DeleteDownloadStorageDialog dialog = new DeleteDownloadStorageDialog();
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        });
    }

    private void actionMiniPlayerHeart() {
        SwitchPreference preference = findPreference("mini_shuffle_button_visibility");
        if (preference == null) {
            return;
        }

        preference.setChecked(Preferences.showShuffleInsteadOfHeart());
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (newValue instanceof Boolean) {
                Preferences.setShuffleInsteadOfHeart((Boolean) newValue);
            }
            return true;
        });
    }

    private void actionAutoDownloadLyrics() {
        SwitchPreference preference = findPreference("auto_download_lyrics");
        if (preference == null) {
            return;
        }

        preference.setChecked(Preferences.isAutoDownloadLyricsEnabled());
        preference.setOnPreferenceChangeListener((pref, newValue) -> {
            if (newValue instanceof Boolean) {
                Preferences.setAutoDownloadLyricsEnabled((Boolean) newValue);
            }
            return true;
        });
    }

    private void getScanStatus() {
        settingViewModel.getScanStatus(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                findPreference("scan_library").setSummary(exception.getMessage());
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                findPreference("scan_library").setSummary(getString(R.string.settings_scan_result, count));
                if (isScanning) getScanStatus();
            }
        });
    }

    private void actionKeepScreenOn() {
        findPreference("always_on_display").setOnPreferenceChangeListener((preference, newValue) -> {
            if (newValue instanceof Boolean) {
                if ((Boolean) newValue) {
                    activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                } else {
                    activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                }
            }
            return true;
        });
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mediaServiceBinder = (MediaService.LocalBinder) service;
            isServiceBound = true;
            checkEqualizerBands();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mediaServiceBinder = null;
            isServiceBound = false;
        }
    };

    private void bindMediaService() {
        Intent intent = new Intent(requireActivity(), MediaService.class);
        intent.setAction(MediaService.ACTION_BIND_EQUALIZER);
        requireActivity().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
        isServiceBound = true;
    }

    private void checkEqualizerBands() {
        if (mediaServiceBinder != null) {
            EqualizerManager eqManager = mediaServiceBinder.getEqualizerManager();
            short numBands = eqManager.getNumberOfBands();
            Preference appEqualizer = findPreference("app_equalizer");
            if (appEqualizer != null) {
                appEqualizer.setVisible(numBands > 0);
            }
        }
    }

    private void actionAppEqualizer() {
        Preference appEqualizer = findPreference("app_equalizer");
        if (appEqualizer != null) {
            appEqualizer.setOnPreferenceClickListener(preference -> {
                NavController navController = NavHostFragment.findNavController(this);
                NavOptions navOptions = new NavOptions.Builder()
                        .setLaunchSingleTop(true)
                        .setPopUpTo(R.id.equalizerFragment, true)
                        .build();
                activity.setBottomNavigationBarVisibility(true);
                activity.setBottomSheetVisibility(true);
                navController.navigate(R.id.equalizerFragment, null, navOptions);
                return true;
            });
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }
}
