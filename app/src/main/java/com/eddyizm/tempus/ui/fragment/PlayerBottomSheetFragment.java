package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;
import androidx.viewpager2.widget.ViewPager2;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentPlayerBottomSheetBinding;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.PlayQueue;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.fragment.pager.PlayerControllerVerticalPager;
import com.bumptech.glide.Glide;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.util.MusicUtil;
import com.eddyizm.tempus.util.RadioCoverArtDownloader;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.PlayerBottomSheetViewModel;
import com.google.android.material.elevation.SurfaceColors;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.Objects;
import java.util.stream.IntStream;

@OptIn(markerClass = UnstableApi.class)
public class PlayerBottomSheetFragment extends Fragment {
    private boolean remoteVisible = false;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener lanSwitchListener = (prefs, key) -> {
        if (Preferences.LAN_REMOTE_ENABLED.equals(key)) renderRemote(com.eddyizm.tempus.lan.LanRemoteSession.current());
    };
    private String remoteCover = null;
    private String remoteMediaSignature = null;
    private FragmentPlayerBottomSheetBinding bind;

    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private Handler progressBarHandler;
    private Runnable progressBarRunnable;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        bind = FragmentPlayerBottomSheetBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        customizeBottomSheetAction();
        initViewPager();
        setHeaderBookmarksButton();
        com.eddyizm.tempus.lan.LanRemoteSession.state().observe(getViewLifecycleOwner(), this::renderRemote);
        // Settings is a fragment in this activity, so a flipped switch is heard here rather than on resume.
        com.eddyizm.tempus.App.getInstance().getPreferences().registerOnSharedPreferenceChangeListener(lanSwitchListener);

        if (getActivity() instanceof MainActivity) {
            MainActivity activity = (MainActivity) getActivity();
            if (activity.isBottomSheetExpanded()) {
                bind.playerHeaderLayout.getRoot().setAlpha(0f);
                bind.playerHeaderLayout.getRoot().setVisibility(View.GONE);
            }
        }

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
        bindMediaController();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // #777: cancel the self-reposting progress updater. Otherwise its pending
        // Handler message keeps this destroyed fragment alive — and, via the
        // MediaBrowser the runnable captures, the whole MainActivity and its
        // cover-art bitmaps — leaking ~3MB per Activity recreation until OOM.
        if (progressBarHandler != null) {
            progressBarHandler.removeCallbacks(progressBarRunnable);
        }
        com.eddyizm.tempus.App.getInstance().getPreferences().unregisterOnSharedPreferenceChangeListener(lanSwitchListener);
        remoteVisible = false;
        remoteCover = null;
        remoteMediaSignature = null;
        bind = null;
    }

    private void customizeBottomSheetBackground() {
        bind.playerHeaderLayout.getRoot().setBackgroundColor(SurfaceColors.getColorForElevation(requireContext(), 8));
    }

    private void customizeBottomSheetAction() {
        bind.playerHeaderLayout.playerHeaderRemoteButton.setOnClickListener(v -> com.eddyizm.tempus.lan.LanDevicePicker.toggle(requireActivity()));
        bind.playerHeaderLayout.getRoot().setOnClickListener(view -> ((MainActivity) requireActivity()).expandBottomSheet());
    }

    private void initViewPager() {
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setOrientation(ViewPager2.ORIENTATION_VERTICAL);
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setAdapter(new PlayerControllerVerticalPager(this));
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaController.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();

                mediaBrowser.setShuffleModeEnabled(Preferences.isShuffleModeEnabled());
                mediaBrowser.setRepeatMode(Preferences.getRepeatMode());

                setMediaControllerListener(mediaBrowser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void setMediaControllerListener(MediaBrowser mediaBrowser) {
        defineProgressBarHandler(mediaBrowser);
        setMediaControllerUI(mediaBrowser);
        setMetadata(mediaBrowser.getMediaMetadata());
        setContentDuration(mediaBrowser.getContentDuration());
        setPlayingState(mediaBrowser.isPlaying());
        setHeaderMediaController();
        setHeaderNextButtonState(mediaBrowser.hasNextMediaItem());

        mediaBrowser.addListener(new Player.Listener() {
            @Override
            public void onMediaMetadataChanged(@NonNull MediaMetadata mediaMetadata) {
                setMediaControllerUI(mediaBrowser);
                setMetadata(mediaMetadata);
                setContentDuration(mediaBrowser.getContentDuration());
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                setPlayingState(isPlaying);
            }

            @Override
            public void onSkipSilenceEnabledChanged(boolean skipSilenceEnabled) {
                Player.Listener.super.onSkipSilenceEnabledChanged(skipSilenceEnabled);
            }

            @Override
            public void onEvents(Player player, Player.Events events) {
                setHeaderNextButtonState(mediaBrowser.hasNextMediaItem());
            }

            @Override
            public void onShuffleModeEnabledChanged(boolean shuffleModeEnabled) {
                Preferences.setShuffleModeEnabled(shuffleModeEnabled);
            }

            @Override
            public void onRepeatModeChanged(int repeatMode) {
                Preferences.setRepeatMode(repeatMode);
            }
        });
    }

    private void setMetadata(MediaMetadata mediaMetadata) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive() || bind == null) return;
        if (mediaMetadata.extras != null) {
            playerBottomSheetViewModel.setLiveMedia(getViewLifecycleOwner(), mediaMetadata.extras.getString("type"), mediaMetadata.extras.getString("id"));
            playerBottomSheetViewModel.setLiveAlbum(getViewLifecycleOwner(), mediaMetadata.extras.getString("type"), mediaMetadata.extras.getString("albumId"));
            playerBottomSheetViewModel.setLiveArtist(getViewLifecycleOwner(), mediaMetadata.extras.getString("type"), mediaMetadata.extras.getString("artistId"));
            playerBottomSheetViewModel.setLiveDescription(mediaMetadata.extras.getString("description", null));

            String type = mediaMetadata.extras.getString("type");

            if (Objects.equals(type, Constants.MEDIA_TYPE_RADIO)) {
                // For radio: keep header consistent with full player
                String stationName = mediaMetadata.extras.getString(
                        "stationName",
                        mediaMetadata.artist != null ? String.valueOf(mediaMetadata.artist) : ""
                );

                String artist = mediaMetadata.extras.getString("radioArtist", "");
                String title = mediaMetadata.extras.getString("radioTitle", "");

                String mainTitle;
                if (!TextUtils.isEmpty(artist) && !TextUtils.isEmpty(title)) {
                    mainTitle = artist + " - " + title;
                } else if (!TextUtils.isEmpty(title)) {
                    mainTitle = title;
                } else if (!TextUtils.isEmpty(artist)) {
                    mainTitle = artist;
                } else {
                    mainTitle = stationName;
                }

                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setText(mainTitle);
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setText(stationName);

                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setVisibility(!TextUtils.isEmpty(mainTitle) ? View.VISIBLE : View.GONE);
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setVisibility(!TextUtils.isEmpty(stationName) ? View.VISIBLE : View.GONE);
            } else {
                // Default (music, podcast, etc.)
                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setText(mediaMetadata.extras.getString("title"));
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setText(
                        mediaMetadata.artist != null
                                ? mediaMetadata.artist
                                : ""
                );

                bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setVisibility(mediaMetadata.extras.getString("title") != null && !Objects.equals(mediaMetadata.extras.getString("title"), "") ? View.VISIBLE : View.GONE);
                bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setVisibility(
                        mediaMetadata.extras.getString("artist") != null && !Objects.equals(mediaMetadata.extras.getString("artist"), "")
                                ? View.VISIBLE
                                : View.GONE);
            }

            if (Constants.MEDIA_TYPE_RADIO.equals(mediaMetadata.extras.getString("type"))) {
                com.bumptech.glide.RequestBuilder<android.graphics.drawable.Drawable> request = Glide.with(requireContext())
                        .load(mediaMetadata.artworkUri)
                        .apply(CustomGlideRequest.createRequestOptions(requireContext(),
                                mediaMetadata.extras.getString("coverArtId"), CustomGlideRequest.ResourceType.Radio));
                request = RadioCoverArtDownloader.applyLocalFileSignature(request, mediaMetadata.artworkUri);
                request.into(bind.playerHeaderLayout.playerHeaderMediaCoverImage);
            } else {
                CustomGlideRequest.Builder
                        .from(requireContext(), mediaMetadata.extras.getString("coverArtId"), CustomGlideRequest.ResourceType.Song)
                        .build()
                        .into(bind.playerHeaderLayout.playerHeaderMediaCoverImage);
            }
        }
    }


    private void setMediaControllerUI(MediaBrowser mediaBrowser) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive() || bind == null) return;
        if (mediaBrowser.getMediaMetadata().extras != null) {
            switch (mediaBrowser.getMediaMetadata().extras.getString("type", Constants.MEDIA_TYPE_MUSIC)) {
                case Constants.MEDIA_TYPE_PODCAST:
                    bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setVisibility(View.VISIBLE);
                    bind.playerHeaderLayout.playerHeaderRewindMediaButton.setVisibility(View.VISIBLE);
                    bind.playerHeaderLayout.playerHeaderNextMediaButton.setVisibility(View.GONE);
                    break;
                case Constants.MEDIA_TYPE_MUSIC:
                default:
                    bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setVisibility(View.GONE);
                    bind.playerHeaderLayout.playerHeaderRewindMediaButton.setVisibility(View.GONE);
                    bind.playerHeaderLayout.playerHeaderNextMediaButton.setVisibility(View.VISIBLE);
                    break;
            }
        }
    }

    private void setContentDuration(long duration) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive() || bind == null) return;
        bind.playerHeaderLayout.playerHeaderSeekBar.setMax((int) (duration / 1000));
    }

    private void setProgress(MediaBrowser mediaBrowser) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) {
            if (bind != null) bind.playerHeaderLayout.playerHeaderSeekBar.setProgress((int)
                    (com.eddyizm.tempus.lan.LanRemoteSession.current().positionAt(android.os.SystemClock.elapsedRealtime()) / 1000), true);
            return;
        }
        if (bind != null)
            bind.playerHeaderLayout.playerHeaderSeekBar.setProgress((int) (mediaBrowser.getCurrentPosition() / 1000), true);
    }

    private void setPlayingState(boolean isPlaying) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive() || bind == null) return;
        bind.playerHeaderLayout.playerHeaderButton.setChecked(isPlaying);
        runProgressBarHandler(isPlaying);
    }

    private void setHeaderMediaController() {
        bind.playerHeaderLayout.playerHeaderButton.setOnClickListener(view -> {
            if (com.eddyizm.tempus.lan.LanRemoteSession.isActive())
                com.eddyizm.tempus.lan.LanRemoteSession.command(com.eddyizm.tempus.lan.LanRemoteSession.current().getPlayWhenReady() ? "pause" : "play");
            else bind.getRoot().findViewById(R.id.exo_play_pause).performClick();
        });
        bind.playerHeaderLayout.playerHeaderNextMediaButton.setOnClickListener(view -> {
            if (com.eddyizm.tempus.lan.LanRemoteSession.isActive()) com.eddyizm.tempus.lan.LanRemoteSession.command("next");
            else bind.getRoot().findViewById(R.id.exo_next).performClick();
        });
        bind.playerHeaderLayout.playerHeaderRewindMediaButton.setOnClickListener(view -> bind.getRoot().findViewById(R.id.exo_rew).performClick());
        bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setOnClickListener(view -> bind.getRoot().findViewById(R.id.exo_ffwd).performClick());
    }

    private void setHeaderNextButtonState(boolean isEnabled) {
        if (com.eddyizm.tempus.lan.LanRemoteSession.isActive() || bind == null) return;
        bind.playerHeaderLayout.playerHeaderNextMediaButton.setEnabled(isEnabled);
        bind.playerHeaderLayout.playerHeaderNextMediaButton.setAlpha(isEnabled ? (float) 1.0 : (float) 0.3);
    }

    public View getPlayerHeader() {
        return bind != null ? bind.playerHeaderLayout.getRoot() : null;
    }

    private void renderRemote(com.eddyizm.tempus.lan.LanRemoteState state) {
        if (bind == null) return;
        bind.playerHeaderLayout.playerHeaderRemoteButton.setVisibility(
                com.eddyizm.tempus.lan.LanDevicePicker.isOffered() ? View.VISIBLE : View.GONE);
        bind.playerHeaderLayout.playerHeaderRemoteButton.setSelected(state.getActive());
        bind.playerHeaderLayout.playerHeaderRemoteButton.setContentDescription(
                state.getActive() ? getString(R.string.lan_controlling, state.getName()) : getString(R.string.lan_play_on));
        androidx.appcompat.widget.TooltipCompat.setTooltipText(bind.playerHeaderLayout.playerHeaderRemoteButton,
                bind.playerHeaderLayout.playerHeaderRemoteButton.getContentDescription());
        boolean wasRemote = remoteVisible;
        remoteVisible = state.getActive();
        if (remoteVisible) {
            if (!wasRemote) ((MainActivity) requireActivity()).setBottomSheetInPeek(true);
            com.eddyizm.tempus.subsonic.models.Child song = com.eddyizm.tempus.lan.LanRemoteSession.currentSong();
            String signature = song.getId() + ":" + song.getAlbumId() + ":" + song.getArtistId();
            if (!Objects.equals(remoteMediaSignature, signature)) {
                remoteMediaSignature = signature;
                String type = song.getId().isEmpty() ? null : Constants.MEDIA_TYPE_MUSIC;
                playerBottomSheetViewModel.setLiveMedia(getViewLifecycleOwner(), type, song.getId());
                playerBottomSheetViewModel.setLiveAlbum(getViewLifecycleOwner(), TextUtils.isEmpty(song.getAlbumId()) ? null : type, song.getAlbumId());
                playerBottomSheetViewModel.setLiveArtist(getViewLifecycleOwner(), TextUtils.isEmpty(song.getArtistId()) ? null : type, song.getArtistId());
                playerBottomSheetViewModel.setLiveDescription(null);
            }
            bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setText(state.getTitle().isEmpty() ? getString(R.string.lan_no_track) : state.getTitle());
            bind.playerHeaderLayout.playerHeaderMediaTitleLabel.setVisibility(View.VISIBLE);
            bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setText(getString(R.string.lan_device_status, state.getName(),
                    getString(state.getError() != 0 ? state.getError() : state.getConnected() ? R.string.lan_remote_active : R.string.lan_connecting)));
            bind.playerHeaderLayout.playerHeaderMediaArtistLabel.setVisibility(View.VISIBLE);
            bind.playerHeaderLayout.playerHeaderButton.setChecked(state.getPlayWhenReady());
            bind.playerHeaderLayout.playerHeaderButton.setEnabled(state.getConnected() && state.getCount() > 0);
            bind.playerHeaderLayout.playerHeaderNextMediaButton.setVisibility(View.VISIBLE);
            bind.playerHeaderLayout.playerHeaderNextMediaButton.setEnabled(state.getConnected() && state.getHasNext());
            bind.playerHeaderLayout.playerHeaderNextMediaButton.setAlpha(state.getConnected() ? 1f : .3f);
            bind.playerHeaderLayout.playerHeaderRewindMediaButton.setVisibility(View.GONE);
            bind.playerHeaderLayout.playerHeaderFastForwardMediaButton.setVisibility(View.GONE);
            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
            bind.playerHeaderLayout.playerHeaderSeekBar.setMax((int) (state.getDuration() / 1000));
            bind.playerHeaderLayout.playerHeaderSeekBar.setProgress((int) (state.getPosition() / 1000));
            if (!Objects.equals(remoteCover, state.getCoverId())) {
                remoteCover = state.getCoverId();
                CustomGlideRequest.Builder.from(requireContext(), remoteCover.isEmpty() ? null : remoteCover,
                        CustomGlideRequest.ResourceType.Song).build().into(bind.playerHeaderLayout.playerHeaderMediaCoverImage);
            }
        } else if (wasRemote) {
            remoteCover = null;
            remoteMediaSignature = null;
            bind.playerHeaderLayout.playerHeaderButton.setEnabled(true);
            if (mediaBrowserListenableFuture != null && mediaBrowserListenableFuture.isDone()) {
                try {
                    MediaBrowser player = mediaBrowserListenableFuture.get();
                    setMediaControllerUI(player); setMetadata(player.getMediaMetadata());
                    setPlayingState(player.isPlaying()); setContentDuration(player.getContentDuration());
                    setHeaderNextButtonState(player.hasNextMediaItem()); setProgress(player);
                    if (player.getMediaItemCount() == 0) ((MainActivity) requireActivity()).setBottomSheetInPeek(false);
                } catch (Exception ignored) { }
            }
        }
    }

    public void goBackToFirstPage() {
        if (getContext() == null || !isAdded() || getView() == null) return;
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setCurrentItem(0, false);
        goToControllerPage();
    }

    public void goToControllerPage() {
        if (getContext() == null || !isAdded() || getView() == null) return;
        PlayerControllerVerticalPager playerControllerVerticalPager = (PlayerControllerVerticalPager) bind.playerBodyLayout.playerBodyBottomSheetViewPager.getAdapter();
        if (playerControllerVerticalPager != null) {
            PlayerControllerFragment playerControllerFragment = (PlayerControllerFragment) playerControllerVerticalPager.getRegisteredFragment(0);
            if (playerControllerFragment != null) {
                playerControllerFragment.goToControllerPage();
            }
        }
    }

    public void goToLyricsPage() {
        if (getContext() == null || !isAdded() || getView() == null) return;
        PlayerControllerVerticalPager playerControllerVerticalPager = (PlayerControllerVerticalPager) bind.playerBodyLayout.playerBodyBottomSheetViewPager.getAdapter();
        if (playerControllerVerticalPager != null) {
            PlayerControllerFragment playerControllerFragment = (PlayerControllerFragment) playerControllerVerticalPager.getRegisteredFragment(0);
            if (playerControllerFragment != null) {
                playerControllerFragment.goToLyricsPage();
            }
        }
    }

    public void goToQueuePage() {
        if (getContext() == null || !isAdded() || getView() == null) return;
        bind.playerBodyLayout.playerBodyBottomSheetViewPager.setCurrentItem(1, true);
    }

    public void setPlayerControllerVerticalPagerDraggableState(Boolean isDraggable) {
        ViewPager2 playerControllerVerticalPager = (ViewPager2) bind.playerBodyLayout.playerBodyBottomSheetViewPager;
        playerControllerVerticalPager.setUserInputEnabled(isDraggable);
    }

    private void defineProgressBarHandler(MediaBrowser mediaBrowser) {
        progressBarHandler = new Handler();
        progressBarRunnable = () -> {
            setProgress(mediaBrowser);
            progressBarHandler.postDelayed(progressBarRunnable, 1000);
        };
    }

    private void runProgressBarHandler(boolean isPlaying) {
        if (isPlaying) {
            progressBarHandler.postDelayed(progressBarRunnable, 1000);
        } else {
            progressBarHandler.removeCallbacks(progressBarRunnable);
        }
    }

    private void setHeaderBookmarksButton() {
        if (Preferences.isSyncronizationEnabled()) {
            playerBottomSheetViewModel.getPlayQueue().observeForever(new Observer<PlayQueue>() {
                @Override
                public void onChanged(PlayQueue playQueue) {
                    playerBottomSheetViewModel.getPlayQueue().removeObserver(this);

                    if (bind == null || com.eddyizm.tempus.lan.LanRemoteSession.isActive()) return;

                    if (playQueue != null && playQueue.getEntries() != null && !playQueue.getEntries().isEmpty()) {
                        int index = IntStream.range(0, playQueue.getEntries().size()).filter(ix -> playQueue.getEntries().get(ix).getId().equals(playQueue.getCurrent())).findFirst().orElse(-1);

                        if (index != -1) {
                            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.VISIBLE);
                            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setOnClickListener(v -> {
                                MediaManager.startQueue(mediaBrowserListenableFuture, playQueue.getEntries(), index);
                                bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
                            });
                        }
                    } else {
                        bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
                        bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setOnClickListener(null);
                    }
                }
            });

            bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setOnLongClickListener(v -> {
                bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
                return true;
            });

            new Handler().postDelayed(() -> {
                if (bind != null)
                    bind.playerHeaderLayout.playerHeaderBookmarkMediaButton.setVisibility(View.GONE);
            }, Preferences.getSyncCountdownTimer() * 1000L);
        }
    }
}
