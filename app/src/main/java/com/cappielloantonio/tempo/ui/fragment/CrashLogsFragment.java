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
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.ui.activity.CrashActivity;

public class CrashLogsFragment extends Fragment {


    private TextView crashLogsText;
    private Button buttonCopy;
    private Button buttonShare;

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_crash_logs, container, false);

        crashLogsText = view.findViewById(R.id.crashLogsText);

        return view;
    }


    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CrashActivity activity = (CrashActivity) getActivity();
        String stackTrace = activity.getStackTrace();

        crashLogsText.setText(stackTrace);
    }

}