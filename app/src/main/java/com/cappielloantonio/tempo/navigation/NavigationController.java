package com.cappielloantonio.tempo.navigation;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;

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

    public boolean isNavigationDrawerLocked() {
        return helper.isNavigationDrawerLocked();
    }

    public void toggleDrawerLockOnOrientation(AppCompatActivity activity) {
        helper.toggleNavigationDrawerLockOnOrientationChange(activity);
    }

    public void setSystemBarsVisibility(AppCompatActivity activity, boolean visibility) {
        helper.setSystemBarsVisibility(activity, visibility);
    }
}