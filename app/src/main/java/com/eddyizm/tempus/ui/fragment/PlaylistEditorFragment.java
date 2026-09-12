package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentPlaylistEditorBinding;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.subsonic.models.Playlist;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.PlaylistDialogSongHorizontalAdapter;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.viewmodel.PlaylistEditorViewModel;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;

/**
 * Reorders and removes the tracks of one playlist, then sends the whole list on Save through
 * PlaylistEditorViewModel.updatePlaylist, a rename followed by a full replace, since Subsonic has
 * no reorder call. Leaving the screen discards every change.
 * <p>
 * The view model belongs to this fragment, not the activity, so the edited list survives rotation
 * and is dropped with the screen or on process death. The adapter and the view model share one
 * list, so a remove or a move edits it in place and nothing is posted back through LiveData.
 */
@UnstableApi
public class PlaylistEditorFragment extends Fragment implements PlaylistDialogSongHorizontalAdapter.Listener {
    private FragmentPlaylistEditorBinding bind;
    private PlaylistEditorViewModel playlistEditorViewModel;
    private PlaylistDialogSongHorizontalAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private Snackbar undoSnackbar;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        bind = FragmentPlaylistEditorBinding.inflate(inflater, container, false);
        playlistEditorViewModel = new ViewModelProvider(this).get(PlaylistEditorViewModel.class);

        Bundle args = getArguments();
        Playlist playlist = args != null ? args.getParcelable(Constants.PLAYLIST_OBJECT) : null;
        if (playlist == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return bind.getRoot();
        }

        // Only on the first creation. A rotation recreates the view and keeps the edited list.
        if (playlistEditorViewModel.getPlaylistToEdit() == null) {
            playlistEditorViewModel.setSongsToAdd(null);
            playlistEditorViewModel.setPlaylistToEdit(playlist);
        }

        initToolbar();
        initSongsView();
        observeSaveResult();

        return bind.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // The snackbar is shown on the activity's view, not on this screen's own, and its action
        // reaches the adapter that is nulled just below.
        if (undoSnackbar != null) undoSnackbar.dismiss();
        undoSnackbar = null;
        bind = null;
        adapter = null;
        itemTouchHelper = null;
    }

    private void initToolbar() {
        bind.toolbar.setNavigationOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());
        bind.toolbar.getMenu().findItem(R.id.action_save_playlist).setEnabled(!playlistEditorViewModel.isSavePending());
        bind.toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_save_playlist) {
                item.setEnabled(false);
                if (undoSnackbar != null) undoSnackbar.dismiss();
                playlistEditorViewModel.saveTracks();
                return true;
            }
            return false;
        });
    }

    /**
     * Observed with the view lifecycle, so a result that lands while the app is in the background
     * is delivered when the screen is back on top. Navigating up from a callback in that window is
     * dropped by the navigation library, which refuses to pop once the fragment manager has saved
     * its state.
     */
    private void observeSaveResult() {
        playlistEditorViewModel.getSaveResult().observe(getViewLifecycleOwner(), message -> {
            if (message == null) return;
            playlistEditorViewModel.clearSaveResult();
            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            playlistEditorViewModel.clearSavePending();
            bind.toolbar.getMenu().findItem(R.id.action_save_playlist).setEnabled(true);
            if (message == R.string.playlist_editor_dialog_action_save_success) {
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
    }

    private void initSongsView() {
        bind.playlistSongRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        bind.playlistSongRecyclerView.setHasFixedSize(true);

        adapter = new PlaylistDialogSongHorizontalAdapter(this);
        bind.playlistSongRecyclerView.setAdapter(adapter);

        playlistEditorViewModel.getPlaylistSongLiveList().observe(getViewLifecycleOwner(), songs -> {
            if (songs != null) {
                adapter.setItems(songs);
            } else {
                // With the allowCache false the view model passes, the repository publishes null
                // when the fetch fails or the playlist is gone, so there is nothing to edit and a
                // blank list would read as an empty playlist.
                Toast.makeText(requireContext(), R.string.playlist_editor_load_failure, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
            }
        });

        itemTouchHelper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean isLongPressDragEnabled() {
                return false;
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                if (playlistEditorViewModel.isSavePending()) return false;

                int from = viewHolder.getBindingAdapterPosition();
                int to = target.getBindingAdapterPosition();
                // The drop target can be more than one row away, and notifyItemMoved means the
                // rows in between shift by one, so the list has to move, not swap.
                List<Child> items = adapter.getItems();
                items.add(to, items.remove(from));
                adapter.notifyItemMoved(from, to);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) { }
        });
        itemTouchHelper.attachToRecyclerView(bind.playlistSongRecyclerView);
    }

    @Override
    public void onRemove(int position) {
        if (playlistEditorViewModel.isSavePending()) return;

        Child removed = playlistEditorViewModel.removeFromPlaylistSongLiveList(position);
        adapter.notifyItemRemoved(position);

        MainActivity activity = (MainActivity) requireActivity();
        if (activity.bind == null) return;

        boolean[] undone = {false};
        undoSnackbar = Snackbar.make(activity.bind.playerBottomSheet, R.string.playlist_editor_removed_track, Snackbar.LENGTH_LONG)
                .setAnchorView(activity.getSnackbarAnchor())
                .setAction(R.string.song_bottom_sheet_undo, v -> {
                    if (adapter == null || undone[0] || playlistEditorViewModel.isSavePending()) return;
                    undone[0] = true;

                    int at = playlistEditorViewModel.restoreToPlaylistSongLiveList(position, removed);
                    if (at >= 0) adapter.notifyItemInserted(at);
                });
        undoSnackbar.show();
    }

    @Override
    public void onStartDrag(@NonNull RecyclerView.ViewHolder viewHolder) {
        if (playlistEditorViewModel.isSavePending()) return;

        itemTouchHelper.startDrag(viewHolder);
    }
}
