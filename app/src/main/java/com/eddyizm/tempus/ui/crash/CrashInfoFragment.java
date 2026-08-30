package com.eddyizm.tempus.ui.crash;

import android.graphics.Typeface;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.util.BugReportUtil;

import cat.ereza.customactivityoncrash.CustomActivityOnCrash;
import cat.ereza.customactivityoncrash.config.CaocConfig;

@UnstableApi
public class CrashInfoFragment extends Fragment {

    CrashActivity activity;
    CaocConfig configFromIntent;
    private Button buttonCloseApp;
    private Button buttonRestartApp;
    private TextView textViewDeviceData;

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_crash_info, container, false);

        buttonCloseApp = view.findViewById(R.id.crashInfoButtonClose);
        buttonRestartApp = view.findViewById(R.id.crashInfoButtonRestart);
        textViewDeviceData = view.findViewById(R.id.crashInfoDeviceData);

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

        textViewDeviceData.setText(BugReportUtil.getDeviceInformation(requireContext()));
        textViewDeviceData.setTypeface(Typeface.MONOSPACE);
        textViewDeviceData.setMovementMethod(new ScrollingMovementMethod());
    }

}