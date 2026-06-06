package com.cappielloantonio.tempo.ui.fragment.base;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.NavController;

import com.cappielloantonio.tempo.navigation.BottomSheetController;
import com.cappielloantonio.tempo.navigation.NavigationController;
import com.cappielloantonio.tempo.ui.activity.MainActivity;

@UnstableApi
public abstract class BaseFragment extends Fragment {

    @NonNull
    protected NavigationController getNavigationController() {
        return ((MainActivity) requireActivity()).getNavigationController();
    }

    @NonNull
    protected NavController getNavController() {
        return getNavigationController().getNavController();
    }

    @NonNull
    protected BottomSheetController getBottomSheetController() {
        return ((MainActivity) requireActivity()).getBottomSheetController();
    }

}