package com.cappielloantonio.tempo.navigation;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.Window;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.navigation.NavigationView;

import org.jetbrains.annotations.Contract;

import java.util.Objects;

public class NavigationHelper {
    /* UI components */
    private BottomNavigationView bottomNavigationView;
    private FrameLayout bottomNavigationViewFrame;
    private DrawerLayout drawerLayout;

    /* Navigation components */
    private NavigationView navigationView;
    private NavHostFragment navHostFragment;

    /* States that need to be remembered */
    // -- //

    /* Private constructor */
    public NavigationHelper(@NonNull BottomNavigationView bottomNavigationView,
                            @NonNull FrameLayout bottomNavigationViewFrame,
                            @NonNull DrawerLayout drawerLayout,
                            @NonNull NavigationView navigationView,
                            @NonNull NavHostFragment navHostFragment) {
        this.bottomNavigationView = bottomNavigationView;
        this.bottomNavigationViewFrame = bottomNavigationViewFrame;
        this.drawerLayout = drawerLayout;
        this.navigationView = navigationView;
        this.navHostFragment = navHostFragment;
    }

    public void syncWithBottomSheetBehavior(@NonNull BottomSheetBehavior<View> bottomSheetBehavior,
                                            @NonNull NavController navController) {
        navController.addOnDestinationChangedListener(
                (controller, destination, arguments) -> {
                    // React to the user clicking one of these on bottom-navbar/drawer
                    boolean isTarget = isTargetDestination(destination);
                    int currentState = bottomSheetBehavior.getState();

                    if (isTarget && currentState == BottomSheetBehavior.STATE_EXPANDED) {
                        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
                    }
                });

        NavigationUI.setupWithNavController(bottomNavigationView, navController);
        NavigationUI.setupWithNavController(navigationView, navController);
    }

    @Contract(pure = true)
    private static boolean isTargetDestination(NavDestination destination) {
        int destId = destination.getId();
        return destId == R.id.homeFragment ||
                destId == R.id.libraryFragment ||
                destId == R.id.downloadFragment ||
                destId == R.id.albumCatalogueFragment ||
                destId == R.id.artistCatalogueFragment ||
                destId == R.id.genreCatalogueFragment ||
                destId == R.id.playlistCatalogueFragment;
    }

    /*
    Clean public methods
    Removes the need to invoke the activity on the fragment
     */

    public void setBottomNavigationBarVisibility(boolean visible) {
        int visibility = visible
                ? View.VISIBLE
                : View.GONE;
        bottomNavigationView.setVisibility(visibility);
        bottomNavigationViewFrame.setVisibility(visibility);
    }

    public void setNavigationDrawerLock(boolean locked) {
        int mode = locked
                ? DrawerLayout.LOCK_MODE_LOCKED_CLOSED
                : DrawerLayout.LOCK_MODE_UNLOCKED;
        drawerLayout.setDrawerLockMode(mode);
    }

    @OptIn(markerClass = UnstableApi.class)
    public void toggleNavigationDrawerLockOnOrientationChange(
            AppCompatActivity activity) {

        int orientation = activity.getResources().getConfiguration().orientation;
        boolean isLandscape = orientation == Configuration.ORIENTATION_LANDSCAPE;

        if (Preferences.getEnableDrawerOnPortrait()) {
            setNavigationDrawerLock(false);
            return;
        }
        setNavigationDrawerLock(!isLandscape);
    }

    /*
    All of these are the "backward compatible" changes that don't break the assumption
    that everything was defined on the activity and is gobally available
     */

    @NonNull
    public BottomNavigationView getBottomNavigationView() {
        return bottomNavigationView;
    }

    @NonNull
    public FrameLayout getBottomNavigationViewFrame() {
        return bottomNavigationViewFrame;
    }

    @NonNull
    public DrawerLayout getDrawerLayout() {
        return drawerLayout;
    }

    /*
    Auxiliar functions, could be moved somewhere else
     */

    @OptIn(markerClass = UnstableApi.class)
    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        WindowInsetsControllerCompat insetsController;
        Window window = activity.getWindow();
        View decorView = window.getDecorView();
        insetsController = new WindowInsetsControllerCompat(window, decorView);

        if (visibility) {
            WindowCompat.setDecorFitsSystemWindows(window, true);
            insetsController.show(WindowInsetsCompat.Type.navigationBars());
            insetsController.show(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_DEFAULT);
        } else {
            WindowCompat.setDecorFitsSystemWindows(window, false);
            insetsController.hide(WindowInsetsCompat.Type.navigationBars());
            insetsController.hide(WindowInsetsCompat.Type.statusBars());
            insetsController.setSystemBarsBehavior(
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        }
    }
}
