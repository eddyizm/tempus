package com.cappielloantonio.tempo.navigation;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;

import com.cappielloantonio.tempo.navigation.NavigationHelper;

import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class NavigationController {

    NavigationHelper helper;

    public NavigationController(@NonNull NavigationHelper helper) {
        this.helper = helper;
    }

    public void syncWithBottomSheetBehavior(BottomSheetBehavior<View> bottomSheetBehavior,
                                            NavController navController) {
        helper.syncWithBottomSheetBehavior(bottomSheetBehavior, navController);

    }

    public void setNavbarVisibility(boolean visibility) {
        helper.setBottomNavigationBarVisibility(visibility);
    }

    public void setDrawerLock(boolean visibility) {
        helper.setNavigationDrawerLock(visibility);
    }

    public void toggleDrawerLockOnOrientation(AppCompatActivity activity) {
        helper.toggleNavigationDrawerLockOnOrientationChange(activity);
    }

    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        helper.setSystemBarsVisibility(activity, visibility);
    }
}