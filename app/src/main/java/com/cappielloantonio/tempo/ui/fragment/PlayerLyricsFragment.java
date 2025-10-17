package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerLyricsBinding;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Line;
import com.cappielloantonio.tempo.subsonic.models.LyricsList;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.android.material.button.MaterialButton;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.List;


@OptIn(markerClass = UnstableApi.class)
public class PlayerLyricsFragment extends Fragment {
    private static final String TAG = "PlayerLyricsFragment";

    private InnerFragmentPlayerLyricsBinding bind;
    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private MediaBrowser mediaBrowser;
    private Handler syncLyricsHandler;
    private Runnable syncLyricsRunnable;
    private String currentLyrics;
    private LyricsList currentLyricsList;
    private String currentDescription;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerLyricsBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);

        initOverlay();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initPanelContent();
        observeDownloadState();
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();

    }

    @Override
    public void onResume() {
        super.onResume();
        bindMediaController();
        requireActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public void onPause() {
        super.onPause();
        releaseHandler();
        if (!Preferences.isDisplayAlwaysOn()) {
            requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    @Override
    public void onStop() {
        releaseBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
        currentLyrics = null;
        currentLyricsList = null;
        currentDescription = null;
    }

    private void initOverlay() {
        bind.syncLyricsTapButton.setOnClickListener(view -> {
            playerBottomSheetViewModel.changeSyncLyricsState();
        });

        bind.downloadLyricsButton.setOnClickListener(view -> {
            boolean saved = playerBottomSheetViewModel.downloadCurrentLyrics();
            if (getContext() != null) {
                Toast.makeText(
                        requireContext(),
                        saved ? R.string.player_lyrics_download_success : R.string.player_lyrics_download_failure,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseHandler() {
        if (syncLyricsHandler != null) {
            syncLyricsHandler.removeCallbacks(syncLyricsRunnable);
            syncLyricsHandler = null;
        }
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void bindMediaController() {
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                mediaBrowser = mediaBrowserListenableFuture.get();
                defineProgressHandler();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, MoreExecutors.directExecutor());
    }

    private void initPanelContent() {
        playerBottomSheetViewModel.getLiveLyrics().observe(getViewLifecycleOwner(), lyrics -> {
            currentLyrics = lyrics;
            updatePanelContent();
        });

        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), lyricsList -> {
            currentLyricsList = lyricsList;
            updatePanelContent();
        });

        playerBottomSheetViewModel.getLiveDescription().observe(getViewLifecycleOwner(), description -> {
            currentDescription = description;
            updatePanelContent();
        });
    }

    private void observeDownloadState() {
        playerBottomSheetViewModel.getLyricsCachedState().observe(getViewLifecycleOwner(), cached -> {
            if (bind != null) {
                MaterialButton downloadButton = (MaterialButton) bind.downloadLyricsButton;
                if (cached != null && cached) {
                    downloadButton.setIconResource(R.drawable.ic_done);
                    downloadButton.setContentDescription(getString(R.string.player_lyrics_downloaded_content_description));
                } else {
                    downloadButton.setIconResource(R.drawable.ic_download);
                    downloadButton.setContentDescription(getString(R.string.player_lyrics_download_content_description));
                }
            }
        });
    }

    private void updatePanelContent() {
        if (bind == null) {
            return;
        }

        bind.nowPlayingSongLyricsSrollView.smoothScrollTo(0, 0);

        if (hasStructuredLyrics(currentLyricsList)) {
            setSyncLirics(currentLyricsList);
            bind.nowPlayingSongLyricsTextView.setVisibility(View.VISIBLE);
            bind.emptyDescriptionImageView.setVisibility(View.GONE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.GONE);
            bind.syncLyricsTapButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setEnabled(true);
        } else if (hasText(currentLyrics)) {
            bind.nowPlayingSongLyricsTextView.setText(MusicUtil.getReadableLyrics(currentLyrics));
            bind.nowPlayingSongLyricsTextView.setVisibility(View.VISIBLE);
            bind.emptyDescriptionImageView.setVisibility(View.GONE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.GONE);
            bind.syncLyricsTapButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.VISIBLE);
            bind.downloadLyricsButton.setEnabled(true);
        } else if (hasText(currentDescription)) {
            bind.nowPlayingSongLyricsTextView.setText(MusicUtil.getReadableLyrics(currentDescription));
            bind.nowPlayingSongLyricsTextView.setVisibility(View.VISIBLE);
            bind.emptyDescriptionImageView.setVisibility(View.GONE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.GONE);
            bind.syncLyricsTapButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setEnabled(false);
        } else {
            bind.nowPlayingSongLyricsTextView.setVisibility(View.GONE);
            bind.emptyDescriptionImageView.setVisibility(View.VISIBLE);
            bind.titleEmptyDescriptionLabel.setVisibility(View.VISIBLE);
            bind.syncLyricsTapButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setVisibility(View.GONE);
            bind.downloadLyricsButton.setEnabled(false);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasStructuredLyrics(LyricsList lyricsList) {
        return lyricsList != null
                && lyricsList.getStructuredLyrics() != null
                && !lyricsList.getStructuredLyrics().isEmpty()
                && lyricsList.getStructuredLyrics().get(0) != null
                && lyricsList.getStructuredLyrics().get(0).getLine() != null
                && !lyricsList.getStructuredLyrics().get(0).getLine().isEmpty();
    }

    @SuppressLint("DefaultLocale")
    private void setSyncLirics(LyricsList lyricsList) {
        if (lyricsList.getStructuredLyrics() != null && !lyricsList.getStructuredLyrics().isEmpty() && lyricsList.getStructuredLyrics().get(0).getLine() != null) {
            StringBuilder lyricsBuilder = new StringBuilder();
            List<Line> lines = lyricsList.getStructuredLyrics().get(0).getLine();

            if (lines != null) {
                for (Line line : lines) {
                    lyricsBuilder.append(line.getValue().trim()).append("\n");
                }
            }

            bind.nowPlayingSongLyricsTextView.setText(lyricsBuilder.toString());
        }
    }

    private void defineProgressHandler() {
        playerBottomSheetViewModel.getLiveLyricsList().observe(getViewLifecycleOwner(), lyricsList -> {
            if (!hasStructuredLyrics(lyricsList)) {
                releaseHandler();
                return;
            }

            if (!lyricsList.getStructuredLyrics().get(0).getSynced()) {
                releaseHandler();
                return;
            }

            syncLyricsHandler = new Handler();
            syncLyricsRunnable = () -> {
                if (syncLyricsHandler != null) {
                    if (bind != null) {
                        displaySyncedLyrics();
                    }

                    syncLyricsHandler.postDelayed(syncLyricsRunnable, 250);
                }
            };

            syncLyricsHandler.postDelayed(syncLyricsRunnable, 250);
        });
    }

    private void displaySyncedLyrics() {
        LyricsList lyricsList = playerBottomSheetViewModel.getLiveLyricsList().getValue();
        int timestamp = (int) (mediaBrowser.getCurrentPosition());

        if (hasStructuredLyrics(lyricsList)) {
            StringBuilder lyricsBuilder = new StringBuilder();
            List<Line> lines = lyricsList.getStructuredLyrics().get(0).getLine();

            if (lines == null || lines.isEmpty()) return;

            for (Line line : lines) {
                lyricsBuilder.append(line.getValue().trim()).append("\n");
            }

            Line toHighlight = lines.stream().filter(line -> line != null && line.getStart() != null && line.getStart() < timestamp).reduce((first, second) -> second).orElse(null);

            if (toHighlight != null) {
                String lyrics = lyricsBuilder.toString();
                Spannable spannableString = new SpannableString(lyrics);

                int startingPosition = getStartPosition(lines, toHighlight);
                int endingPosition = startingPosition + toHighlight.getValue().length();

                spannableString.setSpan(new ForegroundColorSpan(requireContext().getResources().getColor(R.color.shadowsLyricsTextColor, null)), 0, lyrics.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                spannableString.setSpan(new ForegroundColorSpan(requireContext().getResources().getColor(R.color.lyricsTextColor, null)), startingPosition, endingPosition, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                bind.nowPlayingSongLyricsTextView.setText(spannableString);

                if (playerBottomSheetViewModel.getSyncLyricsState()) {
                    bind.nowPlayingSongLyricsSrollView.smoothScrollTo(0, getScroll(lines, toHighlight));
                }
            }
        }
    }

    private int getStartPosition(List<Line> lines, Line toHighlight) {
        int start = 0;

        for (Line line : lines) {
            if (line != toHighlight) {
                start = start + line.getValue().length() + 1;
            } else {
                break;
            }
        }

        return start;
    }

    private int getLineCount(List<Line> lines, Line toHighlight) {
        int start = 0;

        for (Line line : lines) {
            if (line != toHighlight) {
                bind.tempLyricsLineTextView.setText(line.getValue());
                start = start + bind.tempLyricsLineTextView.getLineCount();
            } else {
                break;
            }
        }

        return start;
    }

    private int getScroll(List<Line> lines, Line toHighlight) {
        int startIndex = getStartPosition(lines, toHighlight);
        Layout layout = bind.nowPlayingSongLyricsTextView.getLayout();
        if (layout == null) return 0;

        int line = layout.getLineForOffset(startIndex);
        int lineTop = layout.getLineTop(line);
        int lineBottom = layout.getLineBottom(line);
        int lineCenter = (lineTop + lineBottom) / 2;

        int scrollViewHeight = bind.nowPlayingSongLyricsSrollView.getHeight();
        int scroll = lineCenter - scrollViewHeight / 2;

        return Math.max(scroll, 0);
    }
}