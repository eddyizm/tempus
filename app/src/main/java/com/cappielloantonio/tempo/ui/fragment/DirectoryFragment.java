package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentDirectoryBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.interfaces.DialogClickCallback;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.repository.DirectoryRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Directory;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.MusicDirectoryAdapter;
import com.cappielloantonio.tempo.ui.dialog.DownloadDirectoryDialog;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.ExternalAudioWriter;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.DirectoryViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.stream.Collectors;

@UnstableApi
public class DirectoryFragment extends Fragment implements ClickCallback {
    private static final String TAG = "DirectoryFragment";

    private FragmentDirectoryBinding bind;
    private MainActivity activity;
    private DirectoryViewModel directoryViewModel;

    private MusicDirectoryAdapter musicDirectoryAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;
    private DirectoryRepository directoryRepository;

    private MenuItem menuItem;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.directory_page_menu, menu);

        menuItem = menu.getItem(0);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentDirectoryBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        directoryViewModel = new ViewModelProvider(requireActivity()).get(DirectoryViewModel.class);
        directoryRepository = new DirectoryRepository();

        initAppBar();
        initDirectoryListView();

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

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_download_directory) {
            DownloadDirectoryDialog dialog = new DownloadDirectoryDialog(new DialogClickCallback() {
                @Override
                public void onPositiveClick() {
                    directoryViewModel.loadMusicDirectory(getArguments().getString(Constants.MUSIC_DIRECTORY_ID)).observe(getViewLifecycleOwner(), directory -> {
                        if (isVisible() && getActivity() != null) {
                            List<Child> songs = directory.getChildren().stream().filter(child -> !child.isDir()).collect(Collectors.toList());
                            if (Preferences.getDownloadDirectoryUri() == null) {
                                DownloadUtil.getDownloadTracker(requireContext()).download(
                                        MappingUtil.mapDownloads(songs),
                                        songs.stream().map(Download::new).collect(Collectors.toList())
                                );
                            } else {
                                songs.forEach(child -> ExternalAudioWriter.downloadToUserDirectory(requireContext(), child));
                            }
                        }
                    });
                }
            });

            dialog.show(activity.getSupportFragmentManager(), null);

            return true;
        }

        return false;
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        if (bind != null) {
            bind.toolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());
            bind.directoryBackImageView.setOnClickListener(v -> activity.navController.navigateUp());
        }
    }

    private void initDirectoryListView() {
        bind.directoryRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.directoryRecyclerView.setHasFixedSize(true);

        musicDirectoryAdapter = new MusicDirectoryAdapter(this);
        bind.directoryRecyclerView.setAdapter(musicDirectoryAdapter);
        directoryViewModel.loadMusicDirectory(getArguments().getString(Constants.MUSIC_DIRECTORY_ID)).observe(getViewLifecycleOwner(), directory -> {
            bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
                if ((bind.directoryInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                    bind.toolbar.setTitle(directory.getName());
                } else {
                    bind.toolbar.setTitle(R.string.empty_string);
                }
            });

            bind.directoryTitleLabel.setText(directory.getName());

            musicDirectoryAdapter.setItems(directory.getChildren());

            menuItem.setVisible(
                    directory.getChildren() != null && directory.getChildren()
                            .stream()
                            .filter(child -> !child.isDir())
                            .findFirst()
                            .orElse(null) != null
            );
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
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }

    @Override
    public void onMusicDirectoryClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.directoryFragment, bundle);
    }

    @Override
    public void onMusicDirectoryPlay(Bundle bundle) {
        String directoryId = bundle.getString(Constants.MUSIC_DIRECTORY_ID);
        if (directoryId != null) {
            Toast.makeText(requireContext(), getString(R.string.folder_play_collecting), Toast.LENGTH_SHORT).show();
            collectAndPlayDirectorySongs(directoryId);
        }
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