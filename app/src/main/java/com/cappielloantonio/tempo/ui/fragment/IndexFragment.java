package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentIndexBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.repository.DirectoryRepository;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.MusicFolder;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.MusicIndexAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.IndexUtil;
import com.cappielloantonio.tempo.viewmodel.IndexViewModel;
import com.google.common.util.concurrent.ListenableFuture;

@UnstableApi
public class IndexFragment extends Fragment implements ClickCallback {
    private static final String TAG = "IndexFragment";

    private FragmentIndexBinding bind;
    private MainActivity activity;
    private IndexViewModel indexViewModel;

    private MusicIndexAdapter musicIndexAdapter;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private DirectoryRepository directoryRepository;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentIndexBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        indexViewModel = new ViewModelProvider(requireActivity()).get(IndexViewModel.class);
        directoryRepository = new DirectoryRepository();

        initAppBar();
        initDirectoryListView();
        init();

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void init() {
        MusicFolder musicFolder = getArguments().getParcelable(Constants.MUSIC_FOLDER_OBJECT);

        if (musicFolder != null) {
            indexViewModel.setMusicFolder(musicFolder);
            bind.indexTitleLabel.setText(musicFolder.getName());
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (bind != null)
            bind.toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());

        if (bind != null)
            bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                if ((bind.indexInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                    bind.toolbar.setTitle(indexViewModel.getMusicFolderName());
                } else {
                    bind.toolbar.setTitle(R.string.empty_string);
                }
            });
    }

    private void initDirectoryListView() {
        MusicFolder musicFolder = getArguments().getParcelable(Constants.MUSIC_FOLDER_OBJECT);

        bind.indexRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.indexRecyclerView.setHasFixedSize(true);

        musicIndexAdapter = new MusicIndexAdapter(this);
        bind.indexRecyclerView.setAdapter(musicIndexAdapter);

        indexViewModel.getIndexes(musicFolder != null ? musicFolder.getId() : null).observe(getViewLifecycleOwner(), indexes -> {
            if (indexes != null) {
                musicIndexAdapter.setItems(IndexUtil.getArtist(indexes));
            }
        });

        bind.fastScrollbar.setRecyclerView(bind.indexRecyclerView);
        bind.fastScrollbar.setViewsToUse(R.layout.layout_fast_scrollbar, R.id.fastscroller_bubble, R.id.fastscroller_handle);
    }

    @Override
    public void onMusicIndexClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.directoryFragment, bundle);
    }

    @Override
    public void onMusicIndexPlay(Bundle bundle) {
        String directoryId = bundle.getString(Constants.MUSIC_DIRECTORY_ID);
        if (directoryId != null) {
            Toast.makeText(requireContext(), getString(R.string.folder_play_collecting), Toast.LENGTH_SHORT).show();
            collectAndPlayDirectorySongs(directoryId);
        }
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void collectAndPlayDirectorySongs(String directoryId) {
        List<Child> allSongs = new ArrayList<>();
        AtomicInteger pendingRequests = new AtomicInteger(0);

        collectSongsFromDirectory(directoryId, allSongs, pendingRequests, () -> {
            if (!allSongs.isEmpty()) {
                activity.runOnUiThread(() -> {
                    MediaManager.startQueue(mediaBrowserListenableFuture, allSongs, 0);
                    activity.setBottomSheetInPeek(true);
                    Toast.makeText(requireContext(), getString(R.string.folder_play_playing, allSongs.size()), Toast.LENGTH_SHORT).show();
                });
            } else {
                activity.runOnUiThread(() -> {
                    Toast.makeText(requireContext(), getString(R.string.folder_play_no_songs), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void collectSongsFromDirectory(String directoryId, List<Child> allSongs, AtomicInteger pendingRequests, Runnable onComplete) {
        pendingRequests.incrementAndGet();

        directoryRepository.getMusicDirectory(directoryId).observe(getViewLifecycleOwner(), directory -> {
            if (directory != null && directory.getChildren() != null) {
                for (Child child : directory.getChildren()) {
                    if (child.isDir()) {
                        // It's a subdirectory, recurse into it
                        collectSongsFromDirectory(child.getId(), allSongs, pendingRequests, onComplete);
                    } else if (!child.isVideo()) {
                        // It's a song, add it to the list
                        synchronized (allSongs) {
                            allSongs.add(child);
                        }
                    }
                }
            }

            // Decrement pending requests and check if we're done
            if (pendingRequests.decrementAndGet() == 0) {
                onComplete.run();
            }
        });
    }
}