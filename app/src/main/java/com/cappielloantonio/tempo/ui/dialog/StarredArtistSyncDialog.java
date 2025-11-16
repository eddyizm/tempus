package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogStarredArtistSyncBinding;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.StarredArtistsSyncViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

@OptIn(markerClass = UnstableApi.class)
public class StarredArtistSyncDialog extends DialogFragment {
    private StarredArtistsSyncViewModel starredArtistsSyncViewModel;

    private Runnable onCancel;

    public StarredArtistSyncDialog(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogStarredArtistSyncBinding bind = DialogStarredArtistSyncBinding.inflate(getLayoutInflater());

        starredArtistsSyncViewModel = new ViewModelProvider(requireActivity()).get(StarredArtistsSyncViewModel.class);

        return new MaterialAlertDialogBuilder(getActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.starred_artist_sync_dialog_title)
                .setPositiveButton(R.string.starred_sync_dialog_positive_button, null)
                .setNeutralButton(R.string.starred_sync_dialog_neutral_button, null)
                .setNegativeButton(R.string.starred_sync_dialog_negative_button, null)
                .create();
    }

    @Override
    public void onResume() {
        super.onResume();
        setButtonAction(requireContext());
    }

    private void setButtonAction(Context context) {
        androidx.appcompat.app.AlertDialog dialog = (androidx.appcompat.app.AlertDialog) getDialog();

        if (dialog != null) {
            Button positiveButton = dialog.getButton(Dialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(v -> {
                starredArtistsSyncViewModel.getStarredArtistSongs(requireActivity()).observe(this, allSongs -> {
                    if (allSongs != null && !allSongs.isEmpty()) {
                        DownloadUtil.getDownloadTracker(context).download(allSongs);
                    }
                    dialog.dismiss();
                });
            });

            Button neutralButton = dialog.getButton(Dialog.BUTTON_NEUTRAL);
            neutralButton.setOnClickListener(v -> {
                Preferences.setStarredArtistsSyncEnabled(true);
                dialog.dismiss();
            });

            Button negativeButton = dialog.getButton(Dialog.BUTTON_NEGATIVE);
            negativeButton.setOnClickListener(v -> {
                Preferences.setStarredArtistsSyncEnabled(false);
                if (onCancel != null) onCancel.run();
                dialog.dismiss();
            });
        }
    }
}