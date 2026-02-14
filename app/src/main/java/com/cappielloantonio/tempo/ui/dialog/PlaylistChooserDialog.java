package com.cappielloantonio.tempo.ui.dialog;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.DialogPlaylistChooserBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.ui.adapter.PlaylistDialogHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.PlaylistChooserViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class PlaylistChooserDialog extends DialogFragment implements ClickCallback {
    private DialogPlaylistChooserBinding bind;
    private PlaylistChooserViewModel playlistChooserViewModel;
    private PlaylistDialogHorizontalAdapter playlistDialogHorizontalAdapter;

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        DialogPlaylistChooserBinding.inflate(getLayoutInflater());
        bind = DialogPlaylistChooserBinding.inflate(getLayoutInflater());

        playlistChooserViewModel = new ViewModelProvider(requireActivity()).get(PlaylistChooserViewModel.class);

        bind.playlistDialogChooserVisibilitySwitch.setOnCheckedChangeListener(
                (buttonView,
                 isChecked) -> playlistChooserViewModel.setIsPlaylistPublic(isChecked)
        );
        bind.playlistChooserDialogCreateButton.setOnClickListener(v -> launchPlaylistEditor());
        bind.playlistChooserDialogCancelButton.setOnClickListener(v -> dismiss());

        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(requireContext())
                .setView(bind.getRoot())
                .setTitle(R.string.playlist_chooser_dialog_title);
        return builder.create();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    @Override
    public void onStart() {
        super.onStart();

        initPlaylistView();
        setSongInfo();
    }

    private void setSongInfo() {
        playlistChooserViewModel.setSongsToAdd(requireArguments().getParcelableArrayList(Constants.TRACKS_OBJECT));
    }

    private void launchPlaylistEditor() {
        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(
                Constants.TRACKS_OBJECT,
                playlistChooserViewModel.getSongsToAdd()
        );

        PlaylistEditorDialog editorDialog = new PlaylistEditorDialog(null);
        editorDialog.setArguments(bundle);
        editorDialog.show(
                requireActivity().getSupportFragmentManager(),
                null);

        dismiss();
    }

    private void initPlaylistView() {
        bind.playlistDialogRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistDialogRecyclerView.setHasFixedSize(true);

        playlistDialogHorizontalAdapter = new PlaylistDialogHorizontalAdapter(this);
        bind.playlistDialogRecyclerView.setAdapter(playlistDialogHorizontalAdapter);

        playlistChooserViewModel.getPlaylistList(requireActivity()).observe(requireActivity(), playlists -> {
            if (playlists != null) {
                if (!playlists.isEmpty()) {
                    if (bind != null) bind.noPlaylistsCreatedTextView.setVisibility(View.GONE);
                    if (bind != null) bind.playlistDialogRecyclerView.setVisibility(View.VISIBLE);
                    playlistDialogHorizontalAdapter.setItems(playlists);
                } else {
                    if (bind != null) bind.noPlaylistsCreatedTextView.setVisibility(View.VISIBLE);
                    if (bind != null) bind.playlistDialogRecyclerView.setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        if (playlistChooserViewModel.getSongsToAdd() != null && !playlistChooserViewModel.getSongsToAdd().isEmpty()) {
            Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
            playlistChooserViewModel.addSongsToPlaylist(this, getDialog(), playlist.getId());
        } else {
            Toast.makeText(requireContext(), R.string.playlist_chooser_dialog_toast_add_failure, Toast.LENGTH_SHORT).show();
        }
    }
}
