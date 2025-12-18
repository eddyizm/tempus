package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.lifecycle.Observer;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.common.MediaItem;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.InnerFragmentPlayerQueueBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.service.DownloaderManager;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.PlayQueue;
import com.cappielloantonio.tempo.ui.adapter.PlayerSongQueueAdapter;
import com.cappielloantonio.tempo.ui.dialog.PlaylistChooserDialog;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalAudioReader;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.cappielloantonio.tempo.viewmodel.PlayerBottomSheetViewModel;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
public class PlayerQueueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "PlayerQueueFragment";

    private InnerFragmentPlayerQueueBinding bind;

    private com.google.android.material.floatingactionbutton.FloatingActionButton fabMenuToggle;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabClearQueue;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabShuffleQueue;

    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabSaveToPlaylist;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabDownloadAll;
    private com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton fabLoadQueue;

    private boolean isMenuOpen = false;
    private final int ANIMATION_DURATION = 250; 
    private final float FAB_VERTICAL_SPACING_DP = 70f;

    private PlayerBottomSheetViewModel playerBottomSheetViewModel;
    private PlaybackViewModel playbackViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private PlayerSongQueueAdapter playerSongQueueAdapter;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = InnerFragmentPlayerQueueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        fabMenuToggle = bind.fabMenuToggle;
        fabClearQueue = bind.fabClearQueue;
        fabShuffleQueue = bind.fabShuffleQueue;

        fabSaveToPlaylist = bind.fabSaveToPlaylist;
        fabDownloadAll = bind.fabDownloadAll;
        fabLoadQueue = bind.fabLoadQueue;

        fabMenuToggle.setOnClickListener(v -> toggleFabMenu());
        fabClearQueue.setOnClickListener(v -> handleClearQueueClick());
        fabShuffleQueue.setOnClickListener(v -> handleShuffleQueueClick());

        fabSaveToPlaylist.setOnClickListener(v -> handleSaveToPlaylistClick());
        fabDownloadAll.setOnClickListener(v -> handleDownloadAllClick());
        fabLoadQueue.setOnClickListener(v -> handleLoadQueueClick());

        // Hide Load Queue FAB if sync is disabled
        if (!Preferences.isSyncronizationEnabled()) {
            fabLoadQueue.setVisibility(View.GONE);
        }

        initQueueRecyclerView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeBrowser();
        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

    @Override
    public void onResume() {
        super.onResume();
        setMediaBrowserListenableFuture();
        updateNowPlayingItem();
        mediaBrowserListenableFuture.addListener(() -> {
            try {
                long position = mediaBrowserListenableFuture.get().getCurrentMediaItemIndex();
                requireActivity().runOnUiThread(() -> {
                    bind.playerQueueRecyclerView.scrollToPosition((int) position);
                });
            } catch (Exception e) {
                Log.e("PlayerQueueFragment", "Failed to get mediaBrowserListenableFuture in onResume", e);
            }
        }, MoreExecutors.directExecutor());
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
    }

    private void initializeBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void setMediaBrowserListenableFuture() {
        playerSongQueueAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void initQueueRecyclerView() {
        bind.playerQueueRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playerQueueRecyclerView.setHasFixedSize(true);

        playerSongQueueAdapter = new PlayerSongQueueAdapter(this);
        bind.playerQueueRecyclerView.setAdapter(playerSongQueueAdapter);
        reapplyPlayback();

        playerBottomSheetViewModel.getQueueSong().observe(getViewLifecycleOwner(), queue -> {
            if (queue != null) {
                playerSongQueueAdapter.setItems(queue.stream().map(item -> (Child) item).collect(Collectors.toList()));
                reapplyPlayback();
            }
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, ItemTouchHelper.LEFT) {
            int originalPosition = -1;
            int fromPosition = -1;
            int toPosition = -1;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (originalPosition == -1) {
                    originalPosition = viewHolder.getBindingAdapterPosition();
                }

                fromPosition = viewHolder.getBindingAdapterPosition();
                toPosition = target.getBindingAdapterPosition();
                Collections.swap(playerSongQueueAdapter.getItems(), fromPosition, toPosition);
                recyclerView.getAdapter().notifyItemMoved(fromPosition, toPosition);

                return false;
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);

                if (originalPosition != -1 && fromPosition != -1 && toPosition != -1) {
                    MediaManager.swap(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), originalPosition, toPosition);
                }

                originalPosition = -1;
                fromPosition = -1;
                toPosition = -1;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                MediaManager.remove(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), viewHolder.getBindingAdapterPosition());
                viewHolder.getBindingAdapter().notifyDataSetChanged();
            }
        }).attachToRecyclerView(bind.playerQueueRecyclerView);
    }

    private void updateNowPlayingItem() {
        playerSongQueueAdapter.notifyDataSetChanged();
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
    }

    private void observePlayback() {
        playbackViewModel.getCurrentSongId().observe(getViewLifecycleOwner(), id -> {
            if (playerSongQueueAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                playerSongQueueAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (playerSongQueueAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                playerSongQueueAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (playerSongQueueAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            playerSongQueueAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    /**
     * Toggles the visibility and animates all six secondary FABs.
     */
    private void toggleFabMenu() {
        if (isMenuOpen) {
            // CLOSE MENU (Reverse order for visual effect)
            if (Preferences.isSyncronizationEnabled()) {
                closeFab(fabLoadQueue, 4);
            }
            closeFab(fabSaveToPlaylist, 3); 
            closeFab(fabClearQueue, 2);
            closeFab(fabDownloadAll, 1);
            closeFab(fabShuffleQueue, 0);
            
            fabMenuToggle.animate().rotation(0f).setDuration(ANIMATION_DURATION).start();
        } else {
            // OPEN MENU (lowest index at bottom)
            openFab(fabShuffleQueue, 0);
            openFab(fabDownloadAll, 1);
            openFab(fabClearQueue, 2);
            openFab(fabSaveToPlaylist, 3);
            if (Preferences.isSyncronizationEnabled()) {
                openFab(fabLoadQueue, 4);
            }
            fabMenuToggle.animate().rotation(45f).setDuration(ANIMATION_DURATION).start();
        }
        isMenuOpen = !isMenuOpen;
    }

    private void openFab(View fab, int index) {
        final float displacement = getResources().getDisplayMetrics().density * (FAB_VERTICAL_SPACING_DP * (index + 1));
        
        fab.setVisibility(View.VISIBLE);
        fab.setAlpha(0f);
        fab.setTranslationY(displacement); // Start at the hidden (closed) position
        
        fab.animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(ANIMATION_DURATION)
        .start();
    }

    private void closeFab(View fab, int index) {
        final float displacement = getResources().getDisplayMetrics().density * (FAB_VERTICAL_SPACING_DP * (index + 1));

        fab.animate()
        .translationY(displacement)
        .alpha(0f)
        .setDuration(ANIMATION_DURATION)
        .withEndAction(() -> fab.setVisibility(View.GONE))
        .start();
    }

    private void handleShuffleQueueClick() {
        Log.d(TAG, "Shuffle Queue Clicked!");

        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                int startPosition = mediaBrowser.getCurrentMediaItemIndex() + 1;
                int endPosition = playerSongQueueAdapter.getItems().size() - 1;

                if (startPosition < endPosition) {
                    ArrayList<Integer> pool = new ArrayList<>();

                    for (int i = startPosition; i <= endPosition; i++) {
                        pool.add(i);
                    }

                    while (pool.size() >= 2) {
                        int fromPosition = (int) (Math.random() * (pool.size()));
                        int positionA = pool.get(fromPosition);
                        pool.remove(fromPosition);

                        int toPosition = (int) (Math.random() * (pool.size()));
                        int positionB = pool.get(toPosition);
                        pool.remove(toPosition);

                        Collections.swap(playerSongQueueAdapter.getItems(), positionA, positionB);
                        bind.playerQueueRecyclerView.getAdapter().notifyItemMoved(positionA, positionB);
                    }

                    MediaManager.shuffle(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), startPosition, endPosition);
                }

            } catch (Exception e) {
                Log.e(TAG, "Error shuffling queue", e);
            }

            toggleFabMenu();
        }, MoreExecutors.directExecutor());
    }

    private void handleClearQueueClick() {
        Log.d(TAG, "Clear Queue Clicked!");

        mediaBrowserListenableFuture.addListener(() -> {
            try {
                MediaBrowser mediaBrowser = mediaBrowserListenableFuture.get();
                int startPosition = mediaBrowser.getCurrentMediaItemIndex() + 1;
                int endPosition = playerSongQueueAdapter.getItems().size();

                MediaManager.removeRange(mediaBrowserListenableFuture, playerSongQueueAdapter.getItems(), startPosition, endPosition);
                bind.playerQueueRecyclerView.getAdapter().notifyItemRangeRemoved(startPosition, endPosition - startPosition);

            } catch (Exception e) {
                Log.e(TAG, "Error clearing queue", e);
            }

            toggleFabMenu();
        }, MoreExecutors.directExecutor());
    }

    private void handleSaveToPlaylistClick() {
        Log.d(TAG, "Save to Playlist Clicked!");

        List<Child> queueSongs = playerSongQueueAdapter.getItems();

        if (queueSongs == null || queueSongs.isEmpty()) {
            Toast.makeText(requireContext(), "Queue is empty", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
            return;
        }

        Bundle bundle = new Bundle();
        bundle.putParcelableArrayList(Constants.TRACKS_OBJECT, new ArrayList<>(queueSongs));

        PlaylistChooserDialog dialog = new PlaylistChooserDialog();
        dialog.setArguments(bundle);
        dialog.show(requireActivity().getSupportFragmentManager(), null);

        toggleFabMenu();
    }

    private void handleDownloadAllClick() {
        Log.d(TAG, "Download All Clicked!");

        List<Child> queueSongs = playerSongQueueAdapter.getItems();

        if (queueSongs == null || queueSongs.isEmpty()) {
            Toast.makeText(requireContext(), "Queue is empty", Toast.LENGTH_SHORT).show();
            toggleFabMenu();
            return;
        }

        int downloadCount = 0;
        
        if (Preferences.getDownloadDirectoryUri() == null) {
            List<MediaItem> mediaItemsToDownload = MappingUtil.mapMediaItems(queueSongs);
            List<com.cappielloantonio.tempo.model.Download> downloadModels = new ArrayList<>();

            for (Child child : queueSongs) {
                com.cappielloantonio.tempo.model.Download downloadModel =
                        new com.cappielloantonio.tempo.model.Download(child);
                downloadModel.setArtist(child.getArtist());
                downloadModel.setAlbum(child.getAlbum());
                downloadModel.setCoverArtId(child.getCoverArtId());
                downloadModels.add(downloadModel);
            }

            DownloaderManager downloaderManager = DownloadUtil.getDownloadTracker(requireContext());

            if (downloaderManager != null) {
                downloaderManager.download(mediaItemsToDownload, downloadModels);
                downloadCount = queueSongs.size();
                Toast.makeText(requireContext(), 
                    getResources().getQuantityString(R.plurals.songs_download_started, downloadCount, downloadCount), 
                    Toast.LENGTH_SHORT).show();
                    
                new Handler().postDelayed(() -> {
                    if (playerSongQueueAdapter != null) {
                        playerSongQueueAdapter.notifyDataSetChanged();
                    }
                }, 1000);
            } else {
                Log.e(TAG, "DownloaderManager not initialized. Check DownloadUtil.");
                Toast.makeText(requireContext(), "Download service unavailable.", Toast.LENGTH_SHORT).show();
            }
        } else {
            for (Child song : queueSongs) {
                if (ExternalAudioReader.getUri(song) == null) {
                    ExternalAudioWriter.downloadToUserDirectory(requireContext(), song);
                    downloadCount++;
                }
            }
            
            if (downloadCount > 0) {
                Toast.makeText(requireContext(),
                    getResources().getQuantityString(R.plurals.songs_download_started, downloadCount, downloadCount),
                    Toast.LENGTH_SHORT).show();
                    
                new Handler().postDelayed(() -> {
                    if (playerSongQueueAdapter != null) {
                        playerSongQueueAdapter.notifyDataSetChanged();
                    }
                }, 2000);
            } else {
                Toast.makeText(requireContext(), "All songs already downloaded", Toast.LENGTH_SHORT).show();
            }
        }
        
        toggleFabMenu();
    }

    private void handleLoadQueueClick() {
        Log.d(TAG, "Load Queue Clicked!");
        if (!Preferences.isSyncronizationEnabled()) {
            toggleFabMenu();
            return;
        }

        PlayerBottomSheetViewModel playerBottomSheetViewModel = new ViewModelProvider(requireActivity()).get(PlayerBottomSheetViewModel.class);
        
        playerBottomSheetViewModel.getPlayQueue().observe(getViewLifecycleOwner(), new Observer<PlayQueue>() {
            @Override
            public void onChanged(PlayQueue playQueue) {
                playerBottomSheetViewModel.getPlayQueue().removeObserver(this);
                
                if (playQueue != null && playQueue.getEntries() != null && !playQueue.getEntries().isEmpty()) {
                    int currentIndex = 0;
                    for (int i = 0; i < playQueue.getEntries().size(); i++) {
                        if (playQueue.getEntries().get(i).getId().equals(playQueue.getCurrent())) {
                            currentIndex = i;
                            break;
                        }
                    }
                    
                    MediaManager.startQueue(mediaBrowserListenableFuture, playQueue.getEntries(), currentIndex);
                    
                    Toast.makeText(requireContext(), "Queue loaded", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "No saved queue found", Toast.LENGTH_SHORT).show();
                }
                
                toggleFabMenu();
            }
        });

        new Handler().postDelayed(() -> {
            if (isMenuOpen) {
                toggleFabMenu();
            }
        }, 1000);
    }
}