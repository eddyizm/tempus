package com.eddyizm.tempus.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentLikedBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.navigation.NavigationController;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.SongHorizontalAdapter;
import com.eddyizm.tempus.viewmodel.LikedViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class LikedFragment extends Fragment implements ClickCallback {
    private static final String TAG = "LikedFragment";

    private FragmentLikedBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;
    private LikedViewModel likedViewModel;
    private SongHorizontalAdapter songAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();

        bind = FragmentLikedBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        likedViewModel = new ViewModelProvider(requireActivity()).get(LikedViewModel.class);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initRecyclerView();
        initSwipeToRefresh();
        observeLikedSongs();
    }

    @Override
    public void onStart() {
        super.onStart();
        initializeMediaBrowser();
        activity.toggleBottomNavigationBarVisibilityOnOrientationChange();
        activity.setBottomSheetVisibility(true);
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

    private void initRecyclerView() {
        bind.likedRecyclerView.setHasFixedSize(true);
        bind.likedRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        songAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        bind.likedRecyclerView.setAdapter(songAdapter);
        songAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }

    private void observeLikedSongs() {
        likedViewModel.getLikedSongs().observe(getViewLifecycleOwner(), songs -> {
            if (bind == null) return;

            if (songs == null || songs.isEmpty()) {
                bind.likedRecyclerView.setVisibility(View.GONE);
                bind.emptyLikedLayout.setVisibility(View.VISIBLE);
                bind.swipeLikedToRefresh.setVisibility(View.GONE);
            } else {
                bind.likedRecyclerView.setVisibility(View.VISIBLE);
                bind.emptyLikedLayout.setVisibility(View.GONE);
                bind.swipeLikedToRefresh.setVisibility(View.VISIBLE);
                songAdapter.setItems(songs);
            }
        });
    }

    private void initSwipeToRefresh() {
        bind.swipeLikedToRefresh.setOnRefreshListener(() -> {
            // Re-fetch starred songs by observing again
            likedViewModel.getStarredSongs().observe(getViewLifecycleOwner(), starred -> {
                bind.swipeLikedToRefresh.setRefreshing(false);
            });
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
        List<Child> songs = bundle.getParcelableArrayList(com.eddyizm.tempus.util.Constants.TRACKS_OBJECT);
        if (songs != null && !songs.isEmpty()) {
            int position = bundle.getInt(com.eddyizm.tempus.util.Constants.ITEM_POSITION, 0);
            MediaManager.startQueue(mediaBrowserListenableFuture, songs, position);
            activity.setBottomSheetInPeek(true);
        }
    }

    @Override
    public void onMediaLongClick(Bundle bundle) {
        androidx.navigation.Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }
}
