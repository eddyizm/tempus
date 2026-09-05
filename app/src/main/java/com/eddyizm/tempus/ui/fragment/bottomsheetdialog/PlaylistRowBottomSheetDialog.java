package com.eddyizm.tempus.ui.fragment.bottomsheetdialog;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.fragment.NavHostFragment;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.repository.PlaylistRepository;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.dialog.PlaylistEditorDialog;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.DownloadUtil;
import com.eddyizm.tempus.util.ExternalAudioWriter;
import com.eddyizm.tempus.util.LiveDataUtils;
import com.eddyizm.tempus.util.MappingUtil;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.Preferences;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@UnstableApi
public class PlaylistRowBottomSheetDialog extends BottomSheetDialogFragment {
    private final PlaylistRepository playlistRepository = new PlaylistRepository();

    private Playlist playlist;
    private AlertDialog deleteDialog;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_playlist_row_dialog, container, false);

        playlist = requireArguments().getParcelable(Constants.PLAYLIST_OBJECT);

        init(view);

        return view;
    }

    private void init(View view) {
        ImageView coverPlaylist = view.findViewById(R.id.playlist_cover_image_view);

        CustomGlideRequest.Builder
                .from(view.getContext(), playlist.getCoverArtId(), CustomGlideRequest.ResourceType.Playlist)
                .build()
                .into(coverPlaylist);

        TextView titlePlaylist = view.findViewById(R.id.playlist_title_text_view);
        titlePlaylist.setText(playlist.getName());
        titlePlaylist.setSelected(true);

        TextView countPlaylist = view.findViewById(R.id.playlist_count_text_view);
        countPlaylist.setText(view.getContext().getString(R.string.playlist_counted_tracks, playlist.getSongCount(), MusicUtil.getReadableDurationString(playlist.getDuration(), false)));

        view.findViewById(R.id.play_row).setOnClickListener(v -> withSongs((activity, songs) -> {
            MediaManager.startQueue(activity.getMediaBrowserListenableFuture(), songs, 0);
            activity.setBottomSheetInPeek(true);
        }));

        view.findViewById(R.id.play_shuffle_row).setOnClickListener(v -> withSongs((activity, songs) -> {
            List<Child> shuffled = new ArrayList<>(songs);
            Collections.shuffle(shuffled);

            MediaManager.startQueue(activity.getMediaBrowserListenableFuture(), shuffled, 0);
            activity.setBottomSheetInPeek(true);
        }));

        view.findViewById(R.id.play_next_row).setOnClickListener(v -> withSongs((activity, songs) -> {
            MediaManager.enqueue(activity.getMediaBrowserListenableFuture(), songs, true);
            activity.setBottomSheetInPeek(true);
        }));

        view.findViewById(R.id.add_to_queue_row).setOnClickListener(v -> withSongs((activity, songs) -> {
            MediaManager.enqueue(activity.getMediaBrowserListenableFuture(), songs, false);
            Toast.makeText(activity, R.string.playlist_added_to_queue, Toast.LENGTH_SHORT).show();
        }));

        view.findViewById(R.id.download_all_row).setOnClickListener(v -> withSongs((activity, songs) -> {
            if (Preferences.getDownloadDirectoryUri() == null) {
                DownloadUtil.getDownloadTracker(activity).download(
                        MappingUtil.mapDownloads(songs),
                        songs.stream().map(child -> {
                            Download toDownload = new Download(child);
                            toDownload.setPlaylistId(playlist.getId());
                            toDownload.setPlaylistName(playlist.getName());
                            return toDownload;
                        }).collect(Collectors.toList())
                );
            } else {
                songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(activity, child, playlist.getId(), playlist.getName()));
            }
        }));

        view.findViewById(R.id.edit_playlist_row).setOnClickListener(v -> {
            NavHostFragment.findNavController(this).navigate(R.id.playlistEditorFragment, playlistBundle());
            dismiss();
        });

        view.findViewById(R.id.edit_playlist_details_row).setOnClickListener(v -> {
            PlaylistEditorDialog dialog = new PlaylistEditorDialog(null);
            dialog.setArguments(playlistBundle());
            dialog.show(requireActivity().getSupportFragmentManager(), null);
            dismiss();
        });

        View pin = view.findViewById(R.id.pin_row);
        View unpin = view.findViewById(R.id.unpin_row);

        playlistRepository.getPinnedPlaylists().observe(getViewLifecycleOwner(), pinned -> {
            boolean isPinned = pinned.stream().anyMatch(each -> each.getPlaylistId().equals(playlist.getId()));

            pin.setVisibility(isPinned ? View.GONE : View.VISIBLE);
            unpin.setVisibility(isPinned ? View.VISIBLE : View.GONE);
        });

        pin.setOnClickListener(v -> setPinned(true));
        unpin.setOnClickListener(v -> setPinned(false));

        view.findViewById(R.id.delete_row).setOnClickListener(v -> deleteDialog = new MaterialAlertDialogBuilder(requireActivity())
                .setTitle(R.string.playlist_editor_dialog_neutral_button)
                .setMessage(playlist.getName())
                .setPositiveButton(R.string.playlist_editor_dialog_neutral_button, (dialog, which) -> {
                    // The sheet is dismissed at the confirmation, so the request's result is
                    // reported on the application context.
                    Context appContext = requireContext().getApplicationContext();

                    dismiss();

                    playlistRepository.deletePlaylist(playlist.getId(), new PlaylistRepository.PlaylistActionCallback() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(appContext, R.string.playlist_editor_dialog_action_delete_success, Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onFailure() {
                            Toast.makeText(appContext, R.string.playlist_editor_dialog_action_delete_failure, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton(R.string.playlist_editor_dialog_negative_button, null)
                .show());
    }

    private Bundle playlistBundle() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlist);
        return bundle;
    }

    // The sheet is dismissed at the tap and the songs arrive afterward, so the observer lives on the
    // destination fragment's view and navigating away drops the action. Browser and context come from the activity.
    private void withSongs(BiConsumer<MainActivity, List<Child>> action) {
        MainActivity activity = (MainActivity) getActivity();
        if (activity == null) return;

        // A dialog destination never takes the primary navigation fragment role, so the parent
        // manager's primary navigation fragment is the current destination fragment underneath this sheet.
        Fragment listScreen = getParentFragmentManager().getPrimaryNavigationFragment();
        if (listScreen == null || listScreen.getView() == null) return;

        dismiss();

        LiveDataUtils.observePlaylistSongsOnce(listScreen.getViewLifecycleOwner(), playlist.getId(), songs -> action.accept(activity, songs));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (deleteDialog != null && deleteDialog.isShowing()) deleteDialog.dismiss();
        deleteDialog = null;
    }

    private void setPinned(boolean isNowPinned) {
        playlistRepository.insertIfAbsent(playlist);

        if (isNowPinned) {
            playlistRepository.pin(playlist.getId());
        } else {
            playlistRepository.unpin(playlist.getId());
        }

        dismiss();
    }
}
