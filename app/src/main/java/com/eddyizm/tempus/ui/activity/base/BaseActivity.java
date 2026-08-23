package com.eddyizm.tempus.ui.activity.base;

import android.Manifest;
import android.content.ComponentName;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.view.WindowManager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.DownloadService;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.eddyizm.tempus.helper.ThemeHelper;
import com.eddyizm.tempus.service.DownloaderService;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.ui.dialog.BatteryOptimizationDialog;
import com.eddyizm.tempus.util.Flavors;
import com.eddyizm.tempus.util.Preferences;
import com.google.common.util.concurrent.ListenableFuture;

@UnstableApi
public class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";

    private String themeSignature = "";

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeHelper.enableThemeSwitch(this);
        themeSignature = generateThemeSignature();
        super.onCreate(savedInstanceState);
        Flavors.initializeCastContext(this);
        initializeDownloader();
        checkBatteryOptimization();
        checkPermission();
        checkAlwaysOnDisplay();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ThemeHelper.setNavigationBarColor(this);
        initializeBrowser();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!ThemeHelper.themeSignature().equals(themeSignature)) {
            ActivityCompat.recreate(this);
        }
    }

    @Override
    protected void onStop() {
        releaseBrowser();
        super.onStop();
    }

    private void checkBatteryOptimization() {
        if (detectBatteryOptimization() && Preferences.askForOptimization()) {
            showBatteryOptimizationDialog();
        }
    }

    private void checkPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        101);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_LOCAL_NETWORK) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.ACCESS_LOCAL_NETWORK},
                        102
                );
            }
        }
    }

    private void checkAlwaysOnDisplay() {
        if (Preferences.isDisplayAlwaysOn()) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private boolean detectBatteryOptimization() {
        String packageName = getPackageName();
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        return !powerManager.isIgnoringBatteryOptimizations(packageName);
    }

    private void showBatteryOptimizationDialog() {
        BatteryOptimizationDialog dialog = new BatteryOptimizationDialog();
        dialog.show(getSupportFragmentManager(), null);
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(this, new SessionToken(this, new ComponentName(this, MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    public ListenableFuture<MediaBrowser> getMediaBrowserListenableFuture() {
        return mediaBrowserListenableFuture;
    }

    private void initializeDownloader() {
        try {
            DownloadService.start(this, DownloaderService.class);
        } catch (IllegalStateException e) {
            DownloadService.startForeground(this, DownloaderService.class);
        }
    }

    private String generateThemeSignature() {
        String accent = (Preferences.isDynamicColorAccent())
                ? "DYNAMIC"
                : Preferences.getColorAccent();
        String theme = Preferences.getTheme();
        String black = String.valueOf(Preferences.isDarkThemeBlack());
        return theme + "|" + black + "|" + accent;
    }
}
