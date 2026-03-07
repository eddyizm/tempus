package com.cappielloantonio.tempo.ui.controller;

import android.os.Handler;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.fragment.PlayerBottomSheetFragment;
import com.cappielloantonio.tempo.viewmodel.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class BottomSheetHelper {

    BottomSheetBehavior<View> bottomSheetBehavior;
    View bottomSheetView;
    FragmentManager fragmentManager; // Of the entire activity
    PlayerBottomSheetFragment playerBottomSheetFragment;

    public void setState(int state) {
        bottomSheetBehavior.setState(state);
    }

    public BottomSheetHelper(@NonNull BottomSheetBehavior<View> bottomSheetBehavior,
                             @NonNull View bottomSheetView,
                             @NonNull FragmentManager fragmentManager) {
        this.bottomSheetBehavior = bottomSheetBehavior;
        this.bottomSheetView = bottomSheetView;
        this.fragmentManager = fragmentManager;
        this.playerBottomSheetFragment = new PlayerBottomSheetFragment();
    }

    public void addCallback(BottomSheetBehavior.BottomSheetCallback callback) {
        bottomSheetBehavior.addBottomSheetCallback(callback);
    }

    public void setStateInPeek(boolean isVisible) {
        if (isVisible) {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_HIDDEN);
        }
    }

    public void setVisibility(boolean visibility) {
        if (visibility) {
            bottomSheetView.setVisibility(View.VISIBLE);
        } else {
            bottomSheetView.setVisibility(View.GONE);
        }
    }

    public void replaceFragment(int playerBottomSheet) {
        fragmentManager
                .beginTransaction()
                .replace(
                        playerBottomSheet,
                        playerBottomSheetFragment,
                        "PlayerBottomSheet")
                .commit();
    }

    public void checkAfterStateChanged(MainViewModel mainViewModel) {
        final Handler handler = new Handler();
        final Runnable runnable = () -> setStateInPeek(mainViewModel.isQueueLoaded());
        handler.postDelayed(runnable, 100);
    }

    public void collapseDelayed() {
        final Handler handler = new Handler();
        final Runnable runnable = () -> bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        handler.postDelayed(runnable, 100);
    }

    public void setDraggable(Boolean isDraggable) {
        bottomSheetBehavior.setDraggable((isDraggable));
    }

    public int getState() {
        return bottomSheetBehavior.getState();
    }

    public void animate(float slideOffset) {
        if (playerBottomSheetFragment != null) {
            float condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f;
            playerBottomSheetFragment.getPlayerHeader().setAlpha(1 - condensedSlideOffset);
            playerBottomSheetFragment.getPlayerHeader().setVisibility(condensedSlideOffset > 0.99 ? View.GONE : View.VISIBLE);
        }
    }

    public void setPeekHeight(int peekHeight, float displayDensity) {
        int newPeekPx = (int) (peekHeight * displayDensity);
        bottomSheetBehavior.setPeekHeight(newPeekPx);
    }
}