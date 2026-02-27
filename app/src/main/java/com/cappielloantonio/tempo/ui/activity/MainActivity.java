package com.cappielloantonio.tempo.ui.activity;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.core.splashscreen.SplashScreen;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.broadcast.receiver.ConnectivityStatusBroadcastReceiver;
import com.cappielloantonio.tempo.databinding.ActivityMainBinding;
import com.cappielloantonio.tempo.github.utils.UpdateUtil;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.ui.activity.base.BaseActivity;
import com.cappielloantonio.tempo.ui.dialog.ConnectionAlertDialog;
import com.cappielloantonio.tempo.ui.dialog.GithubTempoUpdateDialog;
import com.cappielloantonio.tempo.ui.dialog.ServerUnreachableDialog;
import com.cappielloantonio.tempo.ui.fragment.PlayerBottomSheetFragment;
import com.cappielloantonio.tempo.util.AssetLinkNavigator;
import com.cappielloantonio.tempo.util.AssetLinkUtil;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.MainViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.navigation.NavigationView;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.concurrent.ExecutionException;

@UnstableApi
public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivityLogs";

    public ActivityMainBinding bind;
    private MainViewModel mainViewModel;

    private FragmentManager fragmentManager;
    private NavHostFragment navHostFragment;
    private BottomNavigationView bottomNavigationView;
    private FrameLayout bottomNavigationViewFrame;
    public NavController navController;
    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private BottomSheetBehavior bottomSheetBehavior;
    public boolean isLandscape = false;
    private AssetLinkNavigator assetLinkNavigator;
    private AssetLinkUtil.AssetLink pendingAssetLink;

    ConnectivityStatusBroadcastReceiver connectivityStatusBroadcastReceiver;
    private Intent pendingDownloadPlaybackIntent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SplashScreen.installSplashScreen(this);
        DynamicColors.applyToActivityIfAvailable(this);

        super.onCreate(savedInstanceState);

        bind = ActivityMainBinding.inflate(getLayoutInflater());
        View view = bind.getRoot();
        setContentView(view);

        mainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        assetLinkNavigator = new AssetLinkNavigator(this);

        connectivityStatusBroadcastReceiver = new ConnectivityStatusBroadcastReceiver(this);
        connectivityStatusReceiverManager(true);

        isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        init();
        checkConnectionType();
        getOpenSubsonicExtensions();
        checkTempoUpdate();

        maybeSchedulePlaybackIntent(getIntent());
    }

    @Override
    protected void onStart() {
        super.onStart();
        pingServer();
        initService();
        consumePendingPlaybackIntent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        pingServer();
        toggleNavigationDrawerLockOnOrientationChange();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        connectivityStatusReceiverManager(false);
        bind = null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        maybeSchedulePlaybackIntent(intent);
        consumePendingPlaybackIntent();
    }

    @Override
    public void onBackPressed() {
        if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED)
            collapseBottomSheetDelayed();
        else
            super.onBackPressed();
    }

    public void init() {
        fragmentManager = getSupportFragmentManager();

        initBottomSheet();
        initNavigation();

        if (Preferences.getPassword() != null || (Preferences.getToken() != null && Preferences.getSalt() != null)) {
            goFromLogin();
        } else {
            goToLogin();
        }

        toggleNavigationDrawerLockOnOrientationChange();

    }

    // BOTTOM SHEET/NAVIGATION
    private void initBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(findViewById(R.id.player_bottom_sheet));
        bottomSheetBehavior.addBottomSheetCallback(bottomSheetCallback);
        fragmentManager.beginTransaction().replace(R.id.player_bottom_sheet, new PlayerBottomSheetFragment(), "PlayerBottomSheet").commit();

        checkBottomSheetAfterStateChanged();
    }

    public void setBottomSheetInPeek(Boolean isVisible) {
        if (isVisible) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    public void setBottomSheetVisibility(boolean visibility) {
        if (visibility) {
            findViewById(R.id.player_bottom_sheet).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.player_bottom_sheet).setVisibility(View.GONE);
        }
    }

    private void checkBottomSheetAfterStateChanged() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> setBottomSheetInPeek(mainViewModel.isQueueLoaded());
        handler.postDelayed(runnable, 100);
    }

    public void collapseBottomSheetDelayed() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        handler.postDelayed(runnable, 100);
    }

    public void expandBottomSheet() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void setBottomSheetDraggableState(Boolean isDraggable) {
        bottomSheetBehavior.setDraggable(isDraggable);
    }

    private final BottomSheetBehavior.BottomSheetCallback bottomSheetCallback =
            new BottomSheetBehavior.BottomSheetCallback() {
                int navigationHeight;

                @Override
                public void onStateChanged(@NonNull View view, int state) {
                    PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");

                    switch (state) {
                        case BottomSheetBehavior.STATE_HIDDEN:
                            resetMusicSession();
                            break;
                        case BottomSheetBehavior.STATE_COLLAPSED:
                            if (playerBottomSheetFragment != null)
                                playerBottomSheetFragment.goBackToFirstPage();
                            break;
                        case BottomSheetBehavior.STATE_SETTLING:
                        case BottomSheetBehavior.STATE_EXPANDED:
                        case BottomSheetBehavior.STATE_DRAGGING:
                        case BottomSheetBehavior.STATE_HALF_EXPANDED:
                            break;
                    }
                }

                @Override
                public void onSlide(@NonNull View view, float slideOffset) {
                    animateBottomSheet(slideOffset);
                    if (!isLandscape) {
                         animateBottomNavigation(slideOffset, navigationHeight);
                    }
                }
            };

    private void animateBottomSheet(float slideOffset) {
        PlayerBottomSheetFragment playerBottomSheetFragment = (PlayerBottomSheetFragment) getSupportFragmentManager().findFragmentByTag("PlayerBottomSheet");
        if (playerBottomSheetFragment != null) {
            float condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f;
            playerBottomSheetFragment.getPlayerHeader().setAlpha(1 - condensedSlideOffset);
            playerBottomSheetFragment.getPlayerHeader().setVisibility(condensedSlideOffset > 0.99 ? View.GONE : View.VISIBLE);
        }
    }

    private void animateBottomNavigation(float slideOffset, int navigationHeight) {
        if (slideOffset < 0) return;

        if (navigationHeight == 0) {
            navigationHeight = bind.bottomNavigation.getHeight();
        }

        float slideY = navigationHeight - navigationHeight * (1 - slideOffset);

        bind.bottomNavigation.setTranslationY(slideY);
    }

    private void initNavigation() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationViewFrame = findViewById(R.id.bottom_navigation_frame);
        navHostFragment = (NavHostFragment) fragmentManager.findFragmentById(R.id.nav_host_fragment);
        navController = Objects.requireNonNull(navHostFragment).getNavController();
        // This is the lateral slide-in drawer
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);

        /*
         * In questo modo intercetto il cambio schermata tramite navbar e se il bottom sheet è aperto,
         * lo chiudo
         */
        navController.addOnDestinationChangedListener((controller, destination, arguments) -> {
            if (bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED && (
                    destination.getId() == R.id.homeFragment ||
                            destination.getId() == R.id.libraryFragment ||
                            destination.getId() == R.id.downloadFragment)
            ) {
                bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
            }
        });

        NavigationUI.setupWithNavController(bottomNavigationView, navController);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    public void setBottomNavigationBarVisibility(boolean visibility) {
        if (visibility) {
            bottomNavigationView.setVisibility(View.VISIBLE);
            bottomNavigationViewFrame.setVisibility(View.VISIBLE);
        } else {
            bottomNavigationView.setVisibility(View.GONE);
            bottomNavigationViewFrame.setVisibility(View.GONE);
        }
    }

    public void toggleBottomNavigationBarVisibilityOnOrientationChange() {
        // Ignore orientation change, bottom navbar always hidden
        if (Preferences.getHideBottomNavbarOnPortrait()) {
            setBottomNavigationBarVisibility(false);
            setPortraitPlayerBottomSheetPeekHeight(56);
            setSystemBarsVisibility(!isLandscape);
            return;
        }

        if (!isLandscape) {
            // Show app navbar + show system bars
            setPortraitPlayerBottomSheetPeekHeight(136);
            setBottomNavigationBarVisibility(true);
            setSystemBarsVisibility(true);
        } else {
            // Hide app navbar + hide system bars
            setPortraitPlayerBottomSheetPeekHeight(56);
            setBottomNavigationBarVisibility(false);
            setSystemBarsVisibility(false);
        }
    }

    public void setNavigationDrawerLock(boolean locked) {
        int mode = locked
                ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                : DrawerLayout.LOCK_MODE_UNLOCKED;
        drawerLayout.setDrawerLockMode(mode);
    }

    public void toggleNavigationDrawerLockOnOrientationChange() {
        // Ignore orientation check, drawer always unlocked
        if (Preferences.getEnableDrawerOnPortrait()) {
            setNavigationDrawerLock(false);
            return;
        }
        if (!isLandscape) {
            setNavigationDrawerLock(true);
        } else {
            setNavigationDrawerLock(false);
        }
    }

    public void setSystemBarsVisibility(boolean visibility) {
        WindowInsetsControllerCompat insetsController;
        View decorView = getWindow().getDecorView();
        insetsController = new WindowInsetsControllerCompat(getWindow(), decorView);

        if (visibility) {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
            insetsController.show(WindowInsetsCompat.Type.navigationBars());
            insetsController.show(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        } else {
            WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
            insetsController.hide(WindowInsetsCompat.Type.navigationBars());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }

    private void setPortraitPlayerBottomSheetPeekHeight(int peekHeight) {
        FrameLayout bottomSheet = findViewById(R.id.player_bottom_sheet);
        BottomSheetBehavior<FrameLayout> behavior =
                BottomSheetBehavior.from(bottomSheet);

        int newPeekPx = (int) (peekHeight * getResources().getDisplayMetrics().density);
        behavior.setPeekHeight(newPeekPx);
    }

    private void initService() {
        MediaManager.check(getMediaBrowserListenableFuture());

        getMediaBrowserListenableFuture().addListener(() -> {
            try {
                getMediaBrowserListenableFuture().get().addListener(new Player.Listener() {
                    @Override
                    public void onIsPlayingChanged(boolean isPlaying) {
                        if (isPlaying && bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_HIDDEN) {
                            setBottomSheetInPeek(true);
                        }
                    }
                });
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void goToLogin() {
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        setBottomNavigationBarVisibility(false);
        setBottomSheetVisibility(false);

        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.landingFragment) {
            navController.navigate(R.id.action_landingFragment_to_loginFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.settingsFragment) {
            navController.navigate(R.id.action_settingsFragment_to_loginFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.homeFragment) {
            navController.navigate(R.id.action_homeFragment_to_loginFragment);
        }
    }

    private void goToHome() {
        bottomNavigationView.setVisibility(View.VISIBLE);

        if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.landingFragment) {
            navController.navigate(R.id.action_landingFragment_to_homeFragment);
        } else if (Objects.requireNonNull(navController.getCurrentDestination()).getId() == R.id.loginFragment) {
            navController.navigate(R.id.action_loginFragment_to_homeFragment);
        }
    }

    public void goFromLogin() {
        setBottomSheetInPeek(mainViewModel.isQueueLoaded());
        goToHome();
        consumePendingAssetLink();
    }

    public void openAssetLink(@NonNull AssetLinkUtil.AssetLink assetLink) {
        openAssetLink(assetLink, true);
    }

    public void openAssetLink(@NonNull AssetLinkUtil.AssetLink assetLink, boolean collapsePlayer) {
        if (!isUserAuthenticated()) {
            pendingAssetLink = assetLink;
            return;
        }
        if (collapsePlayer) {
            setBottomSheetInPeek(true);
        }
        if (assetLinkNavigator != null) {
            assetLinkNavigator.open(assetLink);
        }
    }

    public void quit() {
        resetUserSession();
        resetMusicSession();
        resetViewModel();
        goToLogin();
    }

    private void resetUserSession() {
        Preferences.setServerId(null);
        Preferences.setSalt(null);
        Preferences.setToken(null);
        Preferences.setPassword(null);
        Preferences.setServer(null);
        Preferences.setLocalAddress(null);
        Preferences.setUser(null);
        Preferences.setClientCert(null);

        // TODO Enter all settings to be reset
        Preferences.setOpenSubsonic(false);
        Preferences.setPlaybackSpeed(1.0f);
        Preferences.setSkipSilenceMode(false);
        Preferences.setDataSavingMode(false);
        Preferences.setStarredSyncEnabled(false);
        Preferences.setStarredAlbumsSyncEnabled(false);
    }

    private void resetMusicSession() {
        MediaManager.reset(getMediaBrowserListenableFuture());
    }

    private void hideMusicSession() {
        MediaManager.hide(getMediaBrowserListenableFuture());
    }

    private void resetViewModel() {
        this.getViewModelStore().clear();
    }

    // CONNECTION
    private void connectivityStatusReceiverManager(boolean isActive) {
        if (isActive) {
            IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
            registerReceiver(connectivityStatusBroadcastReceiver, filter);
        } else {
            unregisterReceiver(connectivityStatusBroadcastReceiver);
        }
    }

    private void pingServer() {
        if (Preferences.getToken() == null && Preferences.getPassword() == null) return;

        if (Preferences.isInUseServerAddressLocal()) {
            mainViewModel.ping().observe(this, subsonicResponse -> {
                if (subsonicResponse == null) {
                    Preferences.setServerSwitchableTimer();
                    Preferences.switchInUseServerAddress();
                    App.refreshSubsonicClient();
                    pingServer();
                    resetView();
                } else {
                    Preferences.setOpenSubsonic(subsonicResponse.getOpenSubsonic() != null && subsonicResponse.getOpenSubsonic());
                }
            });
        } else {
            if (Preferences.isServerSwitchable()) {
                Preferences.setServerSwitchableTimer();
                Preferences.switchInUseServerAddress();
                App.refreshSubsonicClient();
                pingServer();
                resetView();
            } else {
                mainViewModel.ping().observe(this, subsonicResponse -> {
                    if (subsonicResponse == null) {
                        if (Preferences.showServerUnreachableDialog()) {
                            ServerUnreachableDialog dialog = new ServerUnreachableDialog();
                            dialog.show(getSupportFragmentManager(), null);
                        }
                    } else {
                        Preferences.setOpenSubsonic(subsonicResponse.getOpenSubsonic() != null && subsonicResponse.getOpenSubsonic());
                    }
                });
            }
        }
    }

    private void resetView() {
        resetViewModel();
        int id = Objects.requireNonNull(navController.getCurrentDestination()).getId();
        navController.popBackStack(id, true);
        navController.navigate(id);
    }

    private void getOpenSubsonicExtensions() {
        if (Preferences.getToken() != null || Preferences.getPassword() != null) {
            mainViewModel.getOpenSubsonicExtensions().observe(this, openSubsonicExtensions -> {
                if (openSubsonicExtensions != null) {
                    Preferences.setOpenSubsonicExtensions(openSubsonicExtensions);
                }
            });
        }
    }

    private void checkTempoUpdate() {
        if (BuildConfig.FLAVOR.equals("tempus") && Preferences.isGithubUpdateEnabled() && Preferences.showTempusUpdateDialog()) {
            mainViewModel.checkTempoUpdate().observe(this, latestRelease -> {
                if (latestRelease != null && UpdateUtil.showUpdateDialog(latestRelease)) {
                    GithubTempoUpdateDialog dialog = new GithubTempoUpdateDialog(latestRelease);
                    dialog.show(getSupportFragmentManager(), null);
                }
            });
        }
    }

    private void checkConnectionType() {
        if (Preferences.isWifiOnly()) {
            ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

            if (networkInfo != null && networkInfo.getType() != ConnectivityManager.TYPE_WIFI) {
                ConnectionAlertDialog dialog = new ConnectionAlertDialog();
                dialog.show(getSupportFragmentManager(), null);
            }
        }
    }

    private void maybeSchedulePlaybackIntent(Intent intent) {
        if (intent == null) return;
        if (Constants.ACTION_PLAY_EXTERNAL_DOWNLOAD.equals(intent.getAction())
                || intent.hasExtra(Constants.EXTRA_DOWNLOAD_URI)) {
            pendingDownloadPlaybackIntent = new Intent(intent);
        }
        handleAssetLinkIntent(intent);
    }

    private void consumePendingPlaybackIntent() {
        if (pendingDownloadPlaybackIntent == null) return;
        Intent intent = pendingDownloadPlaybackIntent;
        pendingDownloadPlaybackIntent = null;
        playDownloadedMedia(intent);
    }

    private void handleAssetLinkIntent(Intent intent) {
        AssetLinkUtil.AssetLink assetLink = AssetLinkUtil.parse(intent);
        if (assetLink == null) {
            return;
        }
        if (!isUserAuthenticated()) {
            pendingAssetLink = assetLink;
            intent.setData(null);
            return;
        }
        if (assetLinkNavigator != null) {
            assetLinkNavigator.open(assetLink);
        }
        intent.setData(null);
    }

    private boolean isUserAuthenticated() {
        return Preferences.getPassword() != null
                || (Preferences.getToken() != null && Preferences.getSalt() != null);
    }

    private void consumePendingAssetLink() {
        if (pendingAssetLink == null || assetLinkNavigator == null) {
            return;
        }
        assetLinkNavigator.open(pendingAssetLink);
        pendingAssetLink = null;
    }

    private void playDownloadedMedia(Intent intent) {
        String uriString = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_URI);
        if (TextUtils.isEmpty(uriString)) {
            return;
        }

        Uri uri = Uri.parse(uriString);
        String mediaId = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_MEDIA_ID);
        if (TextUtils.isEmpty(mediaId)) {
            mediaId = uri.toString();
        }

        String title = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_TITLE);
        String artist = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_ARTIST);
        String album = intent.getStringExtra(Constants.EXTRA_DOWNLOAD_ALBUM);
        int duration = intent.getIntExtra(Constants.EXTRA_DOWNLOAD_DURATION, 0);

        Bundle extras = new Bundle();
        extras.putString("id", mediaId);
        extras.putString("title", title);
        extras.putString("artist", artist);
        extras.putString("album", album);
        extras.putString("uri", uri.toString());
        extras.putString("type", Constants.MEDIA_TYPE_MUSIC);
        extras.putInt("duration", duration);

        MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                .setExtras(extras)
                .setIsBrowsable(false)
                .setIsPlayable(true);

        if (!TextUtils.isEmpty(title)) metadataBuilder.setTitle(title);
        if (!TextUtils.isEmpty(artist)) metadataBuilder.setArtist(artist);
        if (!TextUtils.isEmpty(album)) metadataBuilder.setAlbumTitle(album);

        MediaItem mediaItem = new MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(metadataBuilder.build())
                .setUri(uri)
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setRequestMetadata(new MediaItem.RequestMetadata.Builder()
                        .setMediaUri(uri)
                        .setExtras(extras)
                        .build())
                .build();

        MediaManager.playDownloadedMediaItem(getMediaBrowserListenableFuture(), mediaItem);
    }
}