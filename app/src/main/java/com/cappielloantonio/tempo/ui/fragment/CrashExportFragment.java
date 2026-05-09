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

public class CrashExportFragment extends Fragment {


    private Button buttonCopy;
    private Button buttonShare;

    @Nullable
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_crash_export, container, false);

        buttonCopy = view.findViewById(R.id.crashExportButtonCopy);
        buttonShare = view.findViewById(R.id.crashExportButtonShare);

        return view;
    }


    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        CrashActivity activity = (CrashActivity) getActivity();
        String stackTrace = activity.getStackTrace();

        buttonCopy.setOnClickListener(v -> copyToClipboard(stackTrace));
        buttonShare.setOnClickListener(v -> shareText(stackTrace));
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard =
                (ClipboardManager) requireContext()
                        .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("SimpleText", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(requireContext(), R.string.ca_export_toast_log_copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }

    private void shareText(String text) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, text);
        Intent chooser = Intent.createChooser(shareIntent, "Share via");
        startActivity(chooser);
    }

}