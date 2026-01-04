package com.cappielloantonio.tempo.ui.fragment.bottomsheetdialog;

import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;
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
import com.cappielloantonio.tempo.interfaces.MediaCallback;
import com.cappielloantonio.tempo.repository.ArtistRepository;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.viewmodel.ArtistBottomSheetViewModel;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class ArtistBottomSheetDialog extends BottomSheetDialogFragment implements View.OnClickListener {
    private static final String TAG = "AlbumBottomSheetDialog";

    private ArtistBottomSheetViewModel artistBottomSheetViewModel;
    private ArtistID3 artist;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private boolean playbackStarted = false;
    private boolean dismissalScheduled = false;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_artist_dialog, container, false);

        artist = this.requireArguments().getParcelable(Constants.ARTIST_OBJECT);

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
            Log.d(TAG, "Artist instant mix clicked");
            Toast.makeText(requireContext(), R.string.bottom_sheet_generating_instant_mix, Toast.LENGTH_SHORT).show();
            playbackStarted = false;
            dismissalScheduled = false;
            final Runnable failsafeTimeout = () -> {
                if (!playbackStarted && !dismissalScheduled) {
                    Log.w(TAG, "No response received within 3 seconds");
                    if (isAdded() && getActivity() != null) {
                        Toast.makeText(getContext(), 
                            R.string.bottom_sheet_problem_generating_instant_mix, 
                            Toast.LENGTH_SHORT).show();
                        dismissBottomSheet();
                    }
                }
            };
            view.postDelayed(failsafeTimeout, 3000);

            new ArtistRepository().getInstantMix(artist, 20, new MediaCallback() {
                @Override
                public void onError(Exception exception) {
                    view.removeCallbacks(failsafeTimeout);
                    Log.e(TAG, "Error: " + exception.getMessage());
                    if (isAdded() && getActivity() != null) {
                        String message = isOffline(exception) ? 
                            "You're offline" : "Network error";
                        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
                    }
                    if (!playbackStarted && !dismissalScheduled) {
                        scheduleDelayedDismissal(v);
                    }
                }

                @Override
                public void onLoadMedia(List<?> media) {
                    view.removeCallbacks(failsafeTimeout);
                    if (!isAdded() || getActivity() == null) {
                        return;
                    }

                    Log.d(TAG, "Received " + media.size() + " songs for artist");

                    MusicUtil.ratingFilter((ArrayList<Child>) media);

                    if (!media.isEmpty()) {
                        boolean isFirstBatch = !playbackStarted;
                        MediaManager.startQueue(mediaBrowserListenableFuture, (ArrayList<Child>) media, 0);
                        playbackStarted = true;

                        if (getActivity() instanceof MainActivity) {
                            ((MainActivity) getActivity()).setBottomSheetInPeek(true);
                        }
                        if (isFirstBatch && !dismissalScheduled) {
                            scheduleDelayedDismissal(v);
                        }
                    } else {
                        Toast.makeText(getContext(), 
                            R.string.bottom_sheet_problem_generating_instant_mix, 
                            Toast.LENGTH_SHORT).show();
                        if (!playbackStarted && !dismissalScheduled) {
                            scheduleDelayedDismissal(v);
                        }
                    }
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

    private void scheduleDelayedDismissal(View view) {
        if (dismissalScheduled) return;
        dismissalScheduled = true;

        view.postDelayed(() -> {
            try {
                if (mediaBrowserListenableFuture.isDone()) {
                    MediaBrowser browser = mediaBrowserListenableFuture.get();
                    if (browser != null && browser.isPlaying()) {
                        dismissBottomSheet();
                        return;
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error checking playback: " + e.getMessage());
            }
            view.postDelayed(() -> dismissBottomSheet(), 200);
        }, 300);
    }

    private boolean isOffline(Exception exception) {
        return exception != null && exception.getMessage() != null && 
            (exception.getMessage().contains("Network") || 
                exception.getMessage().contains("timeout") ||
                exception.getMessage().contains("offline"));
    }
}
