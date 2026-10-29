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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentLikedBinding;
import com.eddyizm.tempus.interfaces.ClickCallback;
import com.eddyizm.tempus.navigation.NavigationController;
import com.eddyizm.tempus.service.MediaManager;
import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.subsonic.models.Child;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.ui.adapter.DownloadHorizontalAdapter;
import com.eddyizm.tempus.util.Constants;
import com.eddyizm.tempus.viewmodel.LikedViewModel;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;

@UnstableApi
public class LikedFragment extends Fragment implements ClickCallback {
    private FragmentLikedBinding bind;
    private MainActivity activity;
    private NavigationController navigationController;

    private LikedViewModel likedViewModel;

    private DownloadHorizontalAdapter likedAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();
        navigationController = activity.getNavigationController();

        bind = FragmentLikedBinding.inflate(inflater, container, false);
        likedViewModel = new ViewModelProvider(requireActivity()).get(LikedViewModel.class);

        return bind.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initAppBar();
        initLikedView();
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

    private void initAppBar() {
        MaterialToolbar materialToolbar = bind.getRoot().findViewById(R.id.toolbar);

        activity.setSupportActionBar(materialToolbar);
        navigationController.setHamburgerMenuForLandscape(activity, materialToolbar);

        Objects.requireNonNull(materialToolbar.getOverflowIcon()).setTint(requireContext().getResources().getColor(R.color.titleTextColor, null));
    }

    private void initLikedView() {
        bind.likedRecyclerView.setHasFixedSize(true);
        bind.likedRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        // The same row as Downloads, without the album line, since this list holds tracks alone,
        // and with the marker that tells the ones on the phone from the ones only the server has.
        likedAdapter = new DownloadHorizontalAdapter(this);
        likedAdapter.setShowAlbumLine(false);
        likedAdapter.setShowDownloadIndicator(true);
        bind.likedRecyclerView.setAdapter(likedAdapter);

        bind.likedFastScrollbar.setViewsToUse(R.layout.layout_fast_scrollbar, R.id.fastscroller_bubble, R.id.fastscroller_handle);
        bind.likedFastScrollbar.setRecyclerView(bind.likedRecyclerView);

        likedViewModel.getLikedTracks().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null || bind == null) {
                return;
            }

            // The list keeps the order the query returned, oldest like first. The level id doubles
            // as the view here, since there is nothing to drill into.
            likedAdapter.setItems(Constants.DOWNLOAD_TYPE_TRACK, Constants.DOWNLOAD_TYPE_TRACK, null, songs);

            bind.emptyLikedLayout.setVisibility(songs.isEmpty() ? View.VISIBLE : View.GONE);
            bind.likedTextView.setVisibility(songs.isEmpty() ? View.GONE : View.VISIBLE);
            bind.likedFastScrollbar.setVisibility(songs.size() > 1 ? View.VISIBLE : View.GONE);
            bind.loadingProgressBar.setVisibility(View.GONE);
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
        Navigation.findNavController(requireView()).navigate(R.id.songBottomSheetDialog, bundle);
    }
}
