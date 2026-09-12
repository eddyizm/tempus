package com.eddyizm.tempus.ui.dialog;

import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.DialogPlaylistEditorBinding;
import com.eddyizm.tempus.interfaces.PlaylistCallback;
import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.PlaylistEditorViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.Objects;

public class PlaylistEditorDialog extends DialogFragment {
    private DialogPlaylistEditorBinding bind;
    private PlaylistEditorViewModel playlistEditorViewModel;

    private final PlaylistCallback playlistCallback;

    private String playlistName;

    public PlaylistEditorDialog(PlaylistCallback playlistCallback) {
        this.playlistCallback = playlistCallback;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        bind = DialogPlaylistEditorBinding.inflate(getLayoutInflater());

        playlistEditorViewModel = new ViewModelProvider(requireActivity()).get(PlaylistEditorViewModel.class);

        return new MaterialAlertDialogBuilder(getActivity())
                .setView(bind.getRoot())
                .setTitle(R.string.playlist_details_dialog_title)
                .setPositiveButton(R.string.playlist_editor_dialog_positive_button, (dialog, id) -> { })
                .setNeutralButton(R.string.playlist_editor_dialog_neutral_button, (dialog, id) -> dialog.cancel())
                .setNegativeButton(R.string.playlist_editor_dialog_negative_button, (dialog, id) -> dialog.cancel())
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();

        setParameterInfo();
        setButtonAction();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void setParameterInfo() {
        if (requireArguments().getParcelableArrayList(Constants.TRACKS_OBJECT) != null) {
            playlistEditorViewModel.setSongsToAdd(requireArguments().getParcelableArrayList(Constants.TRACKS_OBJECT));
            playlistEditorViewModel.setPlaylistToEdit(null);
        } else if (requireArguments().getParcelable(Constants.PLAYLIST_OBJECT) != null) {
            playlistEditorViewModel.setSongsToAdd(null);
            playlistEditorViewModel.setPlaylistToEdit(requireArguments().getParcelable(Constants.PLAYLIST_OBJECT));

            if (playlistEditorViewModel.getPlaylistToEdit() != null) {
                bind.playlistNameTextView.setText(playlistEditorViewModel.getPlaylistToEdit().getName());
            }
        }
    }

    private void setButtonAction() {
        androidx.appcompat.app.AlertDialog alertDialog = (androidx.appcompat.app.AlertDialog) Objects.requireNonNull(getDialog());

        alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (validateInput()) {
                PlaylistRepository.PlaylistActionCallback callback = new PlaylistRepository.PlaylistActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (isAdded() && getContext() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), R.string.playlist_editor_dialog_action_save_success, Toast.LENGTH_SHORT).show();
                                if (playlistCallback != null && playlistEditorViewModel.getPlaylistToEdit() != null) {
                                    playlistCallback.onRenamed(playlistName);
                                }
                                dialogDismiss();
                            });
                        }
                    }

                    @Override
                    public void onFailure() {
                        if (isAdded() && getContext() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), R.string.playlist_editor_dialog_action_save_failure, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                };

                if (playlistEditorViewModel.getSongsToAdd() != null) {
                    playlistEditorViewModel.createPlaylist(playlistName, callback);
                } else if (playlistEditorViewModel.getPlaylistToEdit() != null) {
                    playlistEditorViewModel.renamePlaylist(playlistName, callback);
                }
            }
        });

        View neutralButton = alertDialog.getButton(androidx.appcompat.app.AlertDialog.BUTTON_NEUTRAL);
        if (playlistEditorViewModel.getPlaylistToEdit() == null) {
            neutralButton.setVisibility(View.GONE);
        } else {
            neutralButton.setVisibility(View.VISIBLE);
            neutralButton.setOnClickListener(v -> {
                playlistEditorViewModel.deletePlaylist(new PlaylistRepository.PlaylistActionCallback() {
                    @Override
                    public void onSuccess() {
                        if (isAdded() && getContext() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), R.string.playlist_editor_dialog_action_delete_success, Toast.LENGTH_SHORT).show();
                                dialogDismiss();
                            });
                        }
                    }

                    @Override
                    public void onFailure() {
                        if (isAdded() && getContext() != null) {
                            requireActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), R.string.playlist_editor_dialog_action_delete_failure, Toast.LENGTH_SHORT).show();
                            });
                        }
                    }
                });
            });
        }

        bind.playlistShareButton.setOnClickListener(view -> {
            playlistEditorViewModel.sharePlaylist().observe(requireActivity(), sharedPlaylist -> {
                ClipboardManager clipboardManager = (ClipboardManager) requireActivity().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText(getString(R.string.app_name), sharedPlaylist.getUrl());
                clipboardManager.setPrimaryClip(clipData);
            });
        });

        bind.playlistShareButton.setVisibility(Preferences.isSharingEnabled() ? View.VISIBLE : View.GONE);
    }

    private boolean validateInput() {
        playlistName = Objects.requireNonNull(bind.playlistNameTextView.getText()).toString().trim();

        if (TextUtils.isEmpty(playlistName)) {
            bind.playlistNameTextView.setError(getString(R.string.error_required));
            return false;
        }

        return true;
    }

    private void dialogDismiss() {
        Dialog dialog = getDialog();
        if (dialog != null) {
            dialog.dismiss();
        }
    }
}
