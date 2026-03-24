package com.cappielloantonio.tempo.ui.controller;

import androidx.annotation.NonNull;

import com.cappielloantonio.tempo.viewmodel.MainViewModel;
import com.google.android.material.bottomsheet.BottomSheetBehavior;

public class BottomSheetController {

    BottomSheetHelper helper;

    public BottomSheetController(@NonNull BottomSheetHelper bottomSheetPlayerHelper) {
        this.helper = bottomSheetPlayerHelper;
    }

    public void expand() {
        helper.setState(BottomSheetBehavior.STATE_EXPANDED);
    }

    public void hide() {
        helper.setState(BottomSheetBehavior.STATE_HIDDEN);
    }

    public void setStateInPeek(boolean isVisible) {
        helper.setStateInPeek(isVisible);
    }

    public void setVisibility(boolean visibility) {
        helper.setVisibility(visibility);
    }

    public void addCallback(BottomSheetBehavior.BottomSheetCallback callback) {
        helper.addCallback(callback);
    }

    public void replaceFragment(int playerBottomSheet) {
        helper.replaceFragment(playerBottomSheet);
    }

    public void checkAfterStateChanged(MainViewModel mainViewModel) {
        helper.checkAfterStateChanged(mainViewModel);
    }

    public void collapseDelayed() {
        helper.collapseDelayed();
    }

    public void setDraggable(Boolean isDraggable) {
        helper.setDraggable(isDraggable);
    }

    public int getState() {
        return helper.getState();
    }

    public void animate(float slideOffset) {
        helper.animate(slideOffset);
    }

    public void setPeekHeight(int peekHeight, float displayDensity) {
        helper.setPeekHeight(peekHeight, displayDensity);
    }
}
