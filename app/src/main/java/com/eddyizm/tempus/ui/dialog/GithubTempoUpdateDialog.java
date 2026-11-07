package com.eddyizm.tempus.ui.dialog;

import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.DialogGithubTempoUpdateBinding;
import com.eddyizm.tempus.github.models.LatestRelease;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class GithubTempoUpdateDialog extends DialogFragment {
    private static final String KEY_HTML_URL = "html_url";
    private LatestRelease latestRelease;
    private String htmlUrl;

    public GithubTempoUpdateDialog() {
        this.latestRelease = null;
    }

    public GithubTempoUpdateDialog(LatestRelease latestRelease) {
        this.latestRelease = latestRelease;
        if (latestRelease != null) {
            this.htmlUrl = latestRelease.getHtmlUrl();
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            htmlUrl = savedInstanceState.getString(KEY_HTML_URL);
        } else if (latestRelease != null) {
            htmlUrl = latestRelease.getHtmlUrl();
        }

        DialogGithubTempoUpdateBinding bind = DialogGithubTempoUpdateBinding.inflate(getLayoutInflater());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.github_update_dialog_title)
                .setPositiveButton(R.string.github_update_dialog_positive_button, (dialog, id) -> { })
                .setNegativeButton(R.string.github_update_dialog_negative_button, (dialog, id) -> { })
                .setNeutralButton(R.string.github_update_dialog_neutral_button, (dialog, id) -> { });

        return builder.create();
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_HTML_URL, htmlUrl != null ? htmlUrl : (latestRelease != null ? latestRelease.getHtmlUrl() : null));
    }

    @Override
    public void onStart() {
        super.onStart();

        setButtonAction();
    }

    private void setButtonAction() {
        AlertDialog alertDialog = (AlertDialog) Objects.requireNonNull(getDialog());

        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String url = htmlUrl != null ? htmlUrl : (latestRelease != null ? latestRelease.getHtmlUrl() : null);
            if (url != null) {
                openLink(url);
            }
            if (getDialog() != null) {
                getDialog().dismiss();
            }
        });

        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(v -> {
            Preferences.setTempusUpdateReminder();
            if (getDialog() != null) {
                getDialog().dismiss();
            }
        });

        alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            openLink(getString(R.string.support_url));
            if (getDialog() != null) {
                getDialog().dismiss();
            }
        });
    }

    private void openLink(String link) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}