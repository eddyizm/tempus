package com.eddyizm.tempus.navigation;

import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;

import com.eddyizm.tempus.ui.fragment.PlayerBottomSheetFragment;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class BottomSheetHelper {

    BottomSheetBehavior<View> bottomSheetBehavior;
    View bottomSheetView;
    FragmentManager fragmentManager; // Of the entire activity
    PlayerBottomSheetFragment playerBottomSheetFragment;
    // Tracks whether an expansion is being driven programmatically (e.g. from notification intent),
    // preventing premature collapse from async initialization timers until user explicitly collapses.
    private boolean isTargetExpanded = false;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable pendingStateRunnable;

    public void setState(int state) {
        if (state == BottomSheetBehavior.STATE_EXPANDED) {
            expand();
            return;
        } else if (state == BottomSheetBehavior.STATE_COLLAPSED || state == BottomSheetBehavior.STATE_HIDDEN) {
            isTargetExpanded = false;
        }
        bottomSheetBehavior.setState(state);
    }

    public void expand() {
        isTargetExpanded = true;
        if (pendingStateRunnable != null) {
            handler.removeCallbacks(pendingStateRunnable);
            pendingStateRunnable = null;
        }
        animate(1.0f);
        bottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public boolean isExpanded() {
        return isTargetExpanded
                || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_EXPANDED
                || bottomSheetBehavior.getState() == BottomSheetBehavior.STATE_SETTLING;
    }

    public BottomSheetHelper(@NonNull BottomSheetBehavior<View> bottomSheetBehavior,
                             @NonNull View bottomSheetView,
                             @NonNull FragmentManager fragmentManager) {
        this.bottomSheetBehavior = bottomSheetBehavior;
        this.bottomSheetView = bottomSheetView;
        this.fragmentManager = fragmentManager;
        this.playerBottomSheetFragment = new PlayerBottomSheetFragment();
        this.bottomSheetBehavior.addBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(@NonNull View bottomSheet, int newState) {
                if (newState == BottomSheetBehavior.STATE_COLLAPSED || newState == BottomSheetBehavior.STATE_HIDDEN) {
                    isTargetExpanded = false;
                }
            }

            @Override
            public void onSlide(@NonNull View bottomSheet, float slideOffset) {}
        });
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
        if (isExpanded()) return;
        if (pendingStateRunnable != null) {
            handler.removeCallbacks(pendingStateRunnable);
        }
        pendingStateRunnable = () -> {
            pendingStateRunnable = null;
            setStateInPeek(mainViewModel.isQueueLoaded());
        };
        handler.postDelayed(pendingStateRunnable, 100);
    }

    public void collapseDelayed() {
        if (pendingStateRunnable != null) {
            handler.removeCallbacks(pendingStateRunnable);
        }
        pendingStateRunnable = () -> {
            pendingStateRunnable = null;
            bottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        };
        handler.postDelayed(pendingStateRunnable, 100);
    }

    public void setDraggable(Boolean isDraggable) {
        bottomSheetBehavior.setDraggable((isDraggable));
    }

    public int getState() {
        return bottomSheetBehavior.getState();
    }

    public void animate(float slideOffset) {
        if (playerBottomSheetFragment != null) {
            View playerHeader = playerBottomSheetFragment.getPlayerHeader();
            if (playerHeader != null) {
                float condensedSlideOffset = Math.max(0.0f, Math.min(0.2f, slideOffset - 0.2f)) / 0.2f;
                playerHeader.setAlpha(1 - condensedSlideOffset);
                playerHeader.setVisibility(condensedSlideOffset > 0.99 ? View.GONE : View.VISIBLE);
            }
        }
    }

    public void setPeekHeight(int peekHeight, float displayDensity) {
        int newPeekPx = (int) (peekHeight * displayDensity);
        bottomSheetBehavior.setGestureInsetBottomIgnored(false);
        bottomSheetBehavior.setPeekHeight(newPeekPx);
    }
}