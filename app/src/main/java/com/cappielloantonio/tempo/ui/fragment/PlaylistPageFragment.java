package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.SearchView;
import android.widget.Toast;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.load.resource.bitmap.GranularRoundedCorners;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentPlaylistPageBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaylistPageViewModel;
import com.cappielloantonio.tempo.ui.dialog.PlaylistEditorDialog;
import com.cappielloantonio.tempo.interfaces.PlaylistCallback;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import kotlin.collections.ArrayDeque;

@UnstableApi
public class PlaylistPageFragment extends Fragment implements ClickCallback {
    private FragmentPlaylistPageBinding bind;
    private MainActivity activity;
    private PlaylistPageViewModel playlistPageViewModel;
    private PlaybackViewModel playbackViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.playlist_page_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchView.clearFocus();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                songHorizontalAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);

        initMenuOption(menu);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistPageViewModel = new ViewModelProvider(requireActivity()).get(PlaylistPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        Bundle args = getArguments();
        Playlist playlistArg = args != null ? args.getParcelable(Constants.PLAYLIST_OBJECT) : null;
        if (playlistArg == null) {
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        init(playlistArg);
        initAppBar();
        initMusicButton();
        initBackCover();
        initSongsView();
        
        playlistPageViewModel.getPlaylistMissingEvent().observe(getViewLifecycleOwner(), isMissing -> {
            if (isMissing && getContext() != null) {
                new androidx.appcompat.app.AlertDialog.Builder(getContext())
                    .setTitle(R.string.playlist_error_not_found_title)
                    .setMessage(R.string.playlist_error_not_found_message)
                    .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                        playlistPageViewModel.clearPlaylistMissingEvent();
                        if (getContext() != null) {
                            Toast.makeText(getContext(), R.string.playlist_error_not_found_toast, Toast.LENGTH_SHORT).show();
                        }
                        if (activity != null && activity.navController != null) activity.navController.navigateUp();
                    })
                    .setCancelable(false)
                    .show();
            }
        });

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();

        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (songHorizontalAdapter != null) setMediaBrowserListenableFuture();
    }

    @Override
    public void onStop() {
        releaseMediaBrowser();
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download_playlist) {
            String _playListID = playlistPageViewModel.getPlaylist().getId();
            String _playListName = playlistPageViewModel.getPlaylist().getName();
            playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                if (isVisible() && getActivity() != null) {
                    if (Preferences.getDownloadDirectoryUri() == null) {
                        DownloadUtil.getDownloadTracker(requireContext()).download(
                            MappingUtil.mapDownloads(songs),
                            songs.stream().map(child -> {
                                Download toDownload = new Download(child);
                                toDownload.setPlaylistId(_playListID);
                                toDownload.setPlaylistName(_playListName);
                                return toDownload;
                            }).collect(Collectors.toList())
                        );
                    } else {
                        songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(requireContext(), child, _playListID, _playListName));
                    }
                }
            });
            return true;
        } else if (item.getItemId() == R.id.action_pin_playlist) {
            playlistPageViewModel.setPinned(true);
            return true;
        } else if (item.getItemId() == R.id.action_unpin_playlist) {
            playlistPageViewModel.setPinned(false);
            return true;
        } else if (item.getItemId() == R.id.action_add_to_queue) {
            List<Child> songs = playlistPageViewModel.getPlaylistSongLiveList().getValue();
            if (isVisible() && getActivity() != null && songs != null && !songs.isEmpty()) {
                MediaManager.enqueue(mediaBrowserListenableFuture, songs, false);
            }
            return true;
        } else if (item.getItemId() == R.id.action_edit_playlist) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(Constants.PLAYLIST_OBJECT, playlistPageViewModel.getPlaylist());
            PlaylistEditorDialog dialog = new PlaylistEditorDialog(new PlaylistCallback() {
                @Override
                public void onDismiss() {
                    // Refresh?
                }
            });
            dialog.setArguments(bundle);
            dialog.show(activity.getSupportFragmentManager(), null);
            return true;
        }

        return false;
    }

    private void init(Playlist playlist) {
        playlistPageViewModel.setPlaylist(playlist);
        playlistPageViewModel.updateLastPlayed(playlist.getId());
    }

    private void initMenuOption(Menu menu) {
        playlistPageViewModel.isPinned(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), isPinned -> {
            menu.findItem(R.id.action_unpin_playlist).setVisible(isPinned);
            menu.findItem(R.id.action_pin_playlist).setVisible(!isPinned);
        });
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.animToolbar.setTitle(playlistPageViewModel.getPlaylist().getName());

        bind.playlistNameLabel.setText(playlistPageViewModel.getPlaylist().getName());
        bind.playlistSongCountLabel.setText(getString(R.string.playlist_song_count, playlistPageViewModel.getPlaylist().getSongCount()));
        bind.playlistDurationLabel.setText(getString(R.string.playlist_duration, MusicUtil.getReadableDurationString(playlistPageViewModel.getPlaylist().getDuration(), false)));

        bind.animToolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });

        Objects.requireNonNull(bind.animToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void initMusicButton() {

        playlistPageViewModel.getPlaylistSongLiveList()
                        .observe(getViewLifecycleOwner(), songs -> {
                            if (songs != null) {

                                bind.playlistPagePlayButton.setEnabled(true);
                                bind.playlistPageShuffleButton.setEnabled(true);

                                if (bind.songRecyclerViewPlaceholder != null) {
                                    bind.songRecyclerViewPlaceholder.setVisibility(View.GONE);
                                }

                                bind.playlistPagePlayButton.setOnClickListener(v -> {
                                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                                    activity.setBottomSheetInPeek(true);
                                });
                                bind.playlistPageShuffleButton.setOnClickListener(v -> {
                                    List<Child> shuffled = new ArrayList<>(songs);
                                    Collections.shuffle(shuffled);
                                    MediaManager.startQueue(mediaBrowserListenableFuture, shuffled, 0);
                                    activity.setBottomSheetInPeek(true);
                                });
                            }
                        });
    }

    private void initBackCover() {
        // Observe with the view lifecycle, not the activity. The view model is
        // activity-scoped, so observing its shared LiveData with requireActivity()
        // left one observer per opened playlist registered for the whole session,
        // each retaining this fragment's view tree (the cover ImageViews) — the
        // heap climbed with every playlist opened until OOM. getViewLifecycleOwner()
        // removes the observer at onDestroyView so the view tree can be collected.
        // See issue #696.
        Playlist playlist = playlistPageViewModel.getPlaylist();

        if (playlist == null || bind == null) return;

        String playlistCoverId = playlist.getCoverArtId();

        // Retrieve the parent container holding the cover views
        ViewGroup coverContainer = (ViewGroup) bind.playlistCoverImageViewTopLeft.getParent();

        // Look for an existing single cover view dynamically added previously
        ImageView singleCoverView = coverContainer.findViewWithTag("SINGLE_PLAYLIST_COVER");

        // Loads the playlist's own explicit custom cover image
        if (playlistCoverId != null && !playlistCoverId.trim().isEmpty()) {

            // Dynamically instantiate and attach the single ImageView if it doesn't exist yet
            if (singleCoverView == null) {
                singleCoverView = new ImageView(requireContext());
                singleCoverView.setTag("SINGLE_PLAYLIST_COVER");
                singleCoverView.setScaleType(ImageView.ScaleType.CENTER_CROP);

                // Generate appropriate LayoutParams based on the parent view container type
                ViewGroup.MarginLayoutParams params = getMarginLayoutParams(coverContainer);

                // Convert 62dp to pixels for horizontal margin alignment
                int sideMarginPx = (int) (62 * requireContext().getResources().getDisplayMetrics().density);
                params.setMargins(sideMarginPx, 0, sideMarginPx, 0);

                coverContainer.addView(singleCoverView, params);
            }

            singleCoverView.setVisibility(View.VISIBLE);

            // Loads the custom cover image with rounded corners on all four sides using Glide
            CustomGlideRequest.Builder
                    .from(requireContext(), playlistCoverId, CustomGlideRequest.ResourceType.Playlist)
                    .build()
                    .transform(new GranularRoundedCorners(
                            CustomGlideRequest.CORNER_RADIUS,
                            CustomGlideRequest.CORNER_RADIUS,
                            CustomGlideRequest.CORNER_RADIUS,
                            CustomGlideRequest.CORNER_RADIUS
                    ))
                    .into(singleCoverView);

        } else {
            // Fallback: Hide the single cover view if it exists and render the 2x2 song collage
            if (singleCoverView != null) {
                singleCoverView.setVisibility(View.GONE);
            }

            playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
                if (bind != null && songs != null && !songs.isEmpty()) {

                    List<Child> randomSongs = new ArrayList<>(songs);
                    Collections.shuffle(randomSongs);

                    // Load Top-Left image (round top-left corner only)
                    CustomGlideRequest.Builder
                            .from(requireContext(), !randomSongs.isEmpty() ? randomSongs.get(0).getCoverArtId() : null, CustomGlideRequest.ResourceType.Song)
                            .build()
                            .transform(new GranularRoundedCorners(CustomGlideRequest.CORNER_RADIUS, 0, 0, 0))
                            .into(bind.playlistCoverImageViewTopLeft);

                    // Load Top-Right image (round top-right corner only)
                    CustomGlideRequest.Builder
                            .from(requireContext(), randomSongs.size() > 1 ? randomSongs.get(1).getCoverArtId() : null, CustomGlideRequest.ResourceType.Song)
                            .build()
                            .transform(new GranularRoundedCorners(0, CustomGlideRequest.CORNER_RADIUS, 0, 0))
                            .into(bind.playlistCoverImageViewTopRight);

                    // Load Bottom-Left image (round bottom-left corner only)
                    CustomGlideRequest.Builder
                            .from(requireContext(), randomSongs.size() > 2 ? randomSongs.get(2).getCoverArtId() : null, CustomGlideRequest.ResourceType.Song)
                            .build()
                            .transform(new GranularRoundedCorners(0, 0, 0, CustomGlideRequest.CORNER_RADIUS))
                            .into(bind.playlistCoverImageViewBottomLeft);

                    // Load Bottom-Right image (round bottom-right corner only)
                    CustomGlideRequest.Builder
                            .from(requireContext(), randomSongs.size() > 3 ? randomSongs.get(3).getCoverArtId() : null, CustomGlideRequest.ResourceType.Song)
                            .build()
                            .transform(new GranularRoundedCorners(0, 0, CustomGlideRequest.CORNER_RADIUS, 0))
                            .into(bind.playlistCoverImageViewBottomRight);
                }
            });
        }
    }

    // Creates full-match layout parameters compatible with the type of container group passed.
    @NonNull
    private static ViewGroup.MarginLayoutParams getMarginLayoutParams(ViewGroup coverContainer) {
        ViewGroup.MarginLayoutParams params;

        if (coverContainer instanceof android.widget.FrameLayout) {
            params = new android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        } else if (coverContainer instanceof androidx.constraintlayout.widget.ConstraintLayout) {
            params = new androidx.constraintlayout.widget.ConstraintLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        } else {
            params = new ViewGroup.MarginLayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }

        return params;
    }

    private void initSongsView() {
        bind.songRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.songRecyclerView.setHasFixedSize(true);

        /* Edge-to-edge dynamic inset for bottom padding */
        bind.songRecyclerView.setClipToPadding(false);
        bind.songRecyclerView.post(() -> {
            if (bind == null) return;
            int peekHeight = (int) getResources().getDimension(R.dimen.bottom_sheet_behavior_peek_height);
            WindowInsetsCompat rootInsets = ViewCompat.getRootWindowInsets(
                    requireActivity().getWindow().getDecorView());
            int navBottom = rootInsets == null ? 0
                    : rootInsets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
            bind.songRecyclerView.setPadding(
                    bind.songRecyclerView.getPaddingLeft(),
                    bind.songRecyclerView.getPaddingTop(),
                    bind.songRecyclerView.getPaddingRight(),
                    navBottom+peekHeight);
        });

        // Synchronize scrolling between the list and the header in landscape mode
        androidx.core.widget.NestedScrollView playlistInfoScrollView = bind.playlistInfoScrollView;
        if (playlistInfoScrollView != null) {
            bind.songRecyclerView.addOnScrollListener(new androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, int dx, int dy) {
                    super.onScrolled(recyclerView, dx, dy);
                    playlistInfoScrollView.scrollBy(0, dy);
                }
            });
        }

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.songRecyclerView.setAdapter(songHorizontalAdapter);
        setMediaBrowserListenableFuture();
        reapplyPlayback();

        playlistPageViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            songHorizontalAdapter.setItems(songs);
            if (songs != null) {
                bind.playlistSongCountLabel.setText(getString(R.string.playlist_song_count, songs.size()));
                long totalDuration = songs.stream().mapToLong(s -> s.getDuration() != null ? s.getDuration() : 0).sum();
                bind.playlistDurationLabel.setText(getString(R.string.playlist_duration, MusicUtil.getReadableDurationString(totalDuration, false)));
            }
            reapplyPlayback();
        });
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        activity.setBottomSheetInPeek(true);
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        bundle.putString(Constants.PLAYLIST_ID, playlistPageViewModel.getPlaylist().getId());
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    private void observePlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (songHorizontalAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (songHorizontalAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (songHorizontalAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            songHorizontalAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void setMediaBrowserListenableFuture() {
        songHorizontalAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }
}
