package com.cappielloantonio.tempo.ui.fragment.bottomsheetdialog;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.dialog.PlaylistChooserDialog;
import java.util.ArrayList;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.viewmodel.ArtistBottomSheetViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.common.util.concurrent.ListenableFuture;


@UnstableApi
public class ArtistBottomSheetDialog extends BottomSheetDialogFragment implements View.OnClickListener {
    private static final String TAG = "AlbumBottomSheetDialog";

    private ArtistBottomSheetViewModel artistBottomSheetViewModel;
    private ArtistID3 artist;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private boolean isFirstBatch = true;
    // Guard to prevent concurrent taps that register multiple observers on the cached LiveData
    private boolean isLoadingTracks = false;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_artist_dialog, container, false);

        artist = this.requireArguments().getParcelable(Constants.ARTIST_OBJECT);
        if (artist != null) {
            artist = artist.strippedForNav();
        }

        artistBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(ArtistBottomSheetViewModel.class);
        artistBottomSheetViewModel.setArtist(artist);

        init(view);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    // TODO Use the viewmodel as a conduit and avoid direct calls
    private void init(View view) {
        ImageView coverArtist = view.findViewById(R.id.artist_cover_image_view);
        CustomGlideRequest.Builder
                .from(requireContext(), artistBottomSheetViewModel.getArtist().getCoverArtId(), CustomGlideRequest.ResourceType.Artist)
                .build()
                .into(coverArtist);

        TextView nameArtist = view.findViewById(R.id.song_title_text_view);
        nameArtist.setText(artistBottomSheetViewModel.getArtist().getName());
        nameArtist.setSelected(true);

        ToggleButton favoriteToggle = view.findViewById(R.id.button_favorite);
        favoriteToggle.setChecked(artistBottomSheetViewModel.getArtist().getStarred() != null);
        favoriteToggle.setOnClickListener(v -> {
            artistBottomSheetViewModel.setFavorite(requireContext());
        });

        TextView playRadio = view.findViewById(R.id.play_radio_text_view);
        playRadio.setOnClickListener(v -> {
            MainActivity activity = (MainActivity) getActivity();
            if (activity == null) return;

            ListenableFuture<MediaBrowser> activityBrowserFuture = activity.getMediaBrowserListenableFuture();
            if (activityBrowserFuture == null) return;

            isFirstBatch = true;
            Toast.makeText(requireContext(), R.string.bottom_sheet_generating_instant_mix, Toast.LENGTH_SHORT).show();

            artistBottomSheetViewModel.getArtistInstantMix(activity, artist).observe(activity, media -> {
                if (media == null || media.isEmpty()) return;
                if (getActivity() == null) return;

                MusicUtil.ratingFilter(media);

                if (isFirstBatch) {
                    isFirstBatch = false;
                    MediaManager.startQueue(activityBrowserFuture, media, 0);
                    activity.setBottomSheetInPeek(true);
                    if (isAdded()) {
                        dismissBottomSheet();
                    }
                } else {
                    MediaManager.enqueue(activityBrowserFuture, media, true);
                }
            });
        });

        TextView playRandom = view.findViewById(R.id.play_random_text_view);
        playRandom.setOnClickListener(v -> {
            ArtistRepository artistRepository = new ArtistRepository();
            artistRepository.getRandomSong(artist, 50).observe(getViewLifecycleOwner(), songs -> {
                MusicUtil.ratingFilter(songs);
                if (!songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
                }
                dismissBottomSheet();
            });
        });

        TextView playNext = view.findViewById(R.id.play_next_text_view);
        TextView addToQueue = view.findViewById(R.id.add_to_queue_text_view);
        TextView addToPlaylist = view.findViewById(R.id.add_to_playlist_text_view);

        // Helper to restore UI state after loading completes or fails
        Runnable restoreButtons = () -> {
            isLoadingTracks = false;
            if (playNext != null) playNext.setEnabled(true);
            if (addToQueue != null) addToQueue.setEnabled(true);
            if (addToPlaylist != null) addToPlaylist.setEnabled(true);
        };

        playNext.setOnClickListener(v -> {
            if (isLoadingTracks) return;
            isLoadingTracks = true;
            if (playNext != null) playNext.setEnabled(false);
            if (addToQueue != null) addToQueue.setEnabled(false);
            if (addToPlaylist != null) addToPlaylist.setEnabled(false);

            Toast.makeText(requireContext(), R.string.artist_bottom_sheet_loading_tracks, Toast.LENGTH_SHORT).show();

            androidx.lifecycle.Observer<java.util.List<com.cappielloantonio.tempo.subsonic.models.Child>> observer = new androidx.lifecycle.Observer<java.util.List<com.cappielloantonio.tempo.subsonic.models.Child>>() {
                @Override
                public void onChanged(java.util.List<com.cappielloantonio.tempo.subsonic.models.Child> songs) {
                    // Remove observer immediately to avoid duplicate handling
                    artistBottomSheetViewModel.getArtistAllTracks().removeObserver(this);
                    try {
                        if (songs == null || songs.isEmpty()) return;
                        MusicUtil.ratingFilter(songs);
                        MediaManager.enqueue(mediaBrowserListenableFuture, songs, true);
                        ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
                        dismissBottomSheet();
                    } finally {
                        restoreButtons.run();
                    }
                }
            };

            artistBottomSheetViewModel.getArtistAllTracks().observe(getViewLifecycleOwner(), observer);
        });

        addToQueue.setOnClickListener(v -> {
            if (isLoadingTracks) return;
            isLoadingTracks = true;
            if (playNext != null) playNext.setEnabled(false);
            if (addToQueue != null) addToQueue.setEnabled(false);
            if (addToPlaylist != null) addToPlaylist.setEnabled(false);

            Toast.makeText(requireContext(), R.string.artist_bottom_sheet_loading_tracks, Toast.LENGTH_SHORT).show();

            androidx.lifecycle.Observer<java.util.List<com.cappielloantonio.tempo.subsonic.models.Child>> observer = new androidx.lifecycle.Observer<java.util.List<com.cappielloantonio.tempo.subsonic.models.Child>>() {
                @Override
                public void onChanged(java.util.List<com.cappielloantonio.tempo.subsonic.models.Child> songs) {
                    artistBottomSheetViewModel.getArtistAllTracks().removeObserver(this);
                    try {
                        if (songs == null || songs.isEmpty()) return;
                        MusicUtil.ratingFilter(songs);
                        MediaManager.enqueue(mediaBrowserListenableFuture, songs, false);
                        ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
                        dismissBottomSheet();
                    } finally {
                        restoreButtons.run();
                    }
                }
            };

            artistBottomSheetViewModel.getArtistAllTracks().observe(getViewLifecycleOwner(), observer);
        });

        addToPlaylist.setOnClickListener(v -> {
            if (isLoadingTracks) return;
            isLoadingTracks = true;
            if (playNext != null) playNext.setEnabled(false);
            if (addToQueue != null) addToQueue.setEnabled(false);
            if (addToPlaylist != null) addToPlaylist.setEnabled(false);

            Toast.makeText(requireContext(), R.string.artist_bottom_sheet_loading_tracks, Toast.LENGTH_SHORT).show();

            androidx.lifecycle.Observer<java.util.List<com.cappielloantonio.tempo.subsonic.models.Child>> observer = new androidx.lifecycle.Observer<java.util.List<com.cappielloantonio.tempo.subsonic.models.Child>>() {
                @Override
                public void onChanged(java.util.List<com.cappielloantonio.tempo.subsonic.models.Child> songs) {
                    artistBottomSheetViewModel.getArtistAllTracks().removeObserver(this);
                    try {
                        if (songs == null || songs.isEmpty()) return;
                        Bundle bundle = new Bundle();
                        bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(songs));

                        PlaylistChooserDialog dialog = new PlaylistChooserDialog();
                        dialog.setArguments(bundle);
                        dialog.show(requireActivity().getSupportFragmentManager(), null);

                        dismissBottomSheet();
                    } finally {
                        restoreButtons.run();
                    }
                }
            };

            artistBottomSheetViewModel.getArtistAllTracks().observe(getViewLifecycleOwner(), observer);
        });
    }

    @Override
    public void onClick(View v) {
        dismissBottomSheet();
    }

    private void dismissBottomSheet() {
        dismiss();
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

}
