package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.PopupMenu;
import android.widget.SearchView;
import android.widget.Toast;

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
import com.cappielloantonio.tempo.databinding.FragmentPlaylistCatalogueBinding;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.interfaces.PlaylistCallback;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.PlaylistHorizontalAdapter;
import com.cappielloantonio.tempo.ui.dialog.PlaylistEditorDialog;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.LiveDataUtils;
import com.cappielloantonio.tempo.viewmodel.PlaylistCatalogueViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@UnstableApi
public class PlaylistCatalogueFragment extends Fragment implements ClickCallback {
    private FragmentPlaylistCatalogueBinding bind;
    private MainActivity activity;
    private PlaylistCatalogueViewModel playlistCatalogueViewModel;
    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private PlaylistHorizontalAdapter playlistHorizontalAdapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentPlaylistCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        playlistCatalogueViewModel = new ViewModelProvider(requireActivity()).get(PlaylistCatalogueViewModel.class);

        Bundle args = getArguments();
        if (args == null) {
            if (activity != null && activity.navController != null) activity.navController.navigateUp();
            return view;
        }

        init(args);
        initAppBar();
        initPlaylistCatalogueView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    private void init(Bundle args) {
        if (args.getString(Constants.PLAYLIST_ALL) != null) {
            playlistCatalogueViewModel.setType(Constants.PLAYLIST_ALL);
        } else if (args.getString(Constants.PLAYLIST_DOWNLOADED) != null) {
            playlistCatalogueViewModel.setType(Constants.PLAYLIST_DOWNLOADED);
        }
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.toolbar);

        if (activity.getSupportActionBar() != null) {
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            activity.getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        bind.toolbar.setNavigationOnClickListener(v -> {
            hideKeyboard(v);
            activity.navController.navigateUp();
        });


        bind.appBarLayout.addOnOffsetChangedListener((appBarLayout, verticalOffset) -> {
            if ((bind.albumInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.playlist_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();

        playlistCatalogueViewModel.getSortedPlaylistList().observe(getViewLifecycleOwner(), playlists -> {
            if (playlists != null) {
                android.util.Log.d("TempusLog", "UI Update: Received " + playlists.size() + " items");
                playlistHorizontalAdapter.setItems(playlists);
                playlistHorizontalAdapter.notifyDataSetChanged(); 
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initPlaylistCatalogueView() {
        bind.playlistCatalogueRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistCatalogueRecyclerView.setHasFixedSize(true);

        playlistHorizontalAdapter = new PlaylistHorizontalAdapter(this);
        bind.playlistCatalogueRecyclerView.setAdapter(playlistHorizontalAdapter);

        playlistCatalogueViewModel.getPlaylistList(getViewLifecycleOwner());

        bind.playlistCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.playlistListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_playlist_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_menu, menu);

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
                playlistHorizontalAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            int id = menuItem.getItemId();
            if (id == R.id.menu_playlist_sort_name) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_NAME);
            } else if (id == R.id.menu_playlist_sort_pinned) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_PINNED);
            } else if (id == R.id.menu_playlist_sort_random) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_RANDOM);
            } else if (id == R.id.menu_playlist_sort_date) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_DATE);
            } else if (id == R.id.menu_playlist_sort_songs) {
                playlistCatalogueViewModel.setSortOrder(Constants.PLAYLIST_ORDER_BY_SONGS);
            }
            return true;
        });
        popup.show();
    }

    @Override
    public void onPlaylistClick(Bundle bundle) {
        bundle.putBoolean("is_offline", false);
        Navigation.findNavController(requireView()).navigate(R.id.playlistPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onPlaylistLongClick(View view, Bundle bundle) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(R.menu.playlist_popup_menu, popup.getMenu());
        
        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.action_go_to_playlist) {
                Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
                if (playlist != null) {
                    LiveDataUtils.observePlaylistSongsOnce(getViewLifecycleOwner(), playlist.getId(), songs -> {
                        MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                        activity.setBottomSheetInPeek(true);
                    });
                }
                return true;
            } else if (menuItem.getItemId() == R.id.action_play_shuffle) {
                Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
                if (playlist != null) {
                    LiveDataUtils.observePlaylistSongsOnce(getViewLifecycleOwner(), playlist.getId(), songs -> {
                        List<Child> shuffledSongs = new ArrayList<>(songs);
                        Collections.shuffle(shuffledSongs);
                        MediaManager.startQueue(mediaBrowserListenableFuture, shuffledSongs, 0);
                        activity.setBottomSheetInPeek(true);
                    });
                }
                return true;
            } else if (menuItem.getItemId() == R.id.action_add_to_queue) {
                Playlist playlist = bundle.getParcelable(Constants.PLAYLIST_OBJECT);
                if (playlist != null) {
                    LiveDataUtils.observePlaylistSongsOnce(getViewLifecycleOwner(), playlist.getId(), songs -> {
                        MediaManager.enqueue(mediaBrowserListenableFuture, songs, false);
                        Toast.makeText(requireContext(), R.string.playlist_added_to_queue, Toast.LENGTH_SHORT).show();
                    });
                }
                return true;
            } else if (menuItem.getItemId() == R.id.action_edit_playlist) {
                PlaylistEditorDialog dialog = new PlaylistEditorDialog(new PlaylistCallback() {
                    @Override
                    public void onDismiss() {
                        refreshPlaylistView();
                    }
                });

                dialog.setArguments(bundle);
                dialog.show(activity.getSupportFragmentManager(), null);
                return true;
            }
            return false;
        });
        popup.show();
    }

    private void refreshPlaylistView() {
        playlistCatalogueViewModel.getPlaylistList(getViewLifecycleOwner());
    }
}