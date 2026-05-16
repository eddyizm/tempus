package com.cappielloantonio.tempo.ui.fragment;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.activity.CrashActivity;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import cat.ereza.customactivityoncrash.config.CaocConfig;

@UnstableApi
public class CrashInfoFragment extends Fragment {

    CrashActivity activity;
    CaocConfig configFromIntent;
    private Button buttonCloseApp;
    private Button buttonRestartApp;

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_crash_info, container, false);

        buttonCloseApp = view.findViewById(R.id.crashInfoButtonClose);
        buttonRestartApp = view.findViewById(R.id.crashInfoButtonRestart);

        return view;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        activity = (CrashActivity) getActivity();
        configFromIntent = activity.getConfigFromIntent();

        buttonCloseApp.setOnClickListener(v -> CustomActivityOnCrash.closeApplication(getActivity(), configFromIntent));
        buttonRestartApp.setOnClickListener(v -> CustomActivityOnCrash.restartApplication(getActivity(), configFromIntent));
    }


}