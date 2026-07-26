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
import com.cappielloantonio.tempo.databinding.DialogStarredAlbumSyncBinding;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.StarredAlbumsSyncViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.stream.Collectors;

@OptIn(markerClass = UnstableApi.class)
public class StarredAlbumSyncDialog extends DialogFragment {
    private StarredAlbumsSyncViewModel starredAlbumsSyncViewModel;

    private Runnable onCancel;

    public StarredAlbumSyncDialog(Runnable onCancel) {
        this.onCancel = onCancel;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogStarredAlbumSyncBinding bind = DialogStarredAlbumSyncBinding.inflate(getLayoutInflater());

        starredAlbumsSyncViewModel = new ViewModelProvider(requireActivity()).get(StarredAlbumsSyncViewModel.class);

        return new MaterialAlertDialogBuilder(getActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.starred_album_sync_dialog_title)
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
                starredAlbumsSyncViewModel.getStarredAlbumSongs(requireActivity()).observe(this, allSongs -> {
                    if (allSongs != null && !allSongs.isEmpty()) {
                        DownloadUtil.getDownloadTracker(context).download(
                                MappingUtil.mapDownloads(allSongs),
                                allSongs.stream().map(Download::new).collect(Collectors.toList())
                        );
                    }
                    dialog.dismiss();
                });
            });

            Button neutralButton = dialog.getButton(Dialog.BUTTON_NEUTRAL);
            neutralButton.setOnClickListener(v -> {
                Preferences.setStarredAlbumsSyncEnabled(true);
                dialog.dismiss();
            });

            Button negativeButton = dialog.getButton(Dialog.BUTTON_NEGATIVE);
            negativeButton.setOnClickListener(v -> {
                Preferences.setStarredAlbumsSyncEnabled(false);
                if (onCancel != null) onCancel.run();
                dialog.dismiss();
            });
        }
    }
}