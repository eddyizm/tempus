package com.cappielloantonio.tempo.ui.fragment;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.GridLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentArtistPageBinding;
import com.cappielloantonio.tempo.databinding.InnerFragmentArtistPageButtonsBinding;
import com.cappielloantonio.tempo.databinding.InnerFragmentArtistPageDividerBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.helper.recyclerview.GridItemDecoration;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.AlbumCatalogueAdapter;
import com.cappielloantonio.tempo.ui.adapter.ArtistCatalogueAdapter;
import com.cappielloantonio.tempo.ui.adapter.SingleAdapter;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.viewmodel.ArtistPageViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class ArtistPageFragment extends Fragment implements ClickCallback {
    private FragmentArtistPageBinding bind;
    private MainActivity activity;
    private ArtistPageViewModel artistPageViewModel;
    private PlaybackViewModel playbackViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;
    private AlbumCatalogueAdapter albumCatalogueAdapter;
    private ArtistCatalogueAdapter artistCatalogueAdapter;

    private SingleAdapter<InnerFragmentArtistPageButtonsBinding> buttonsAdapter;
    private SingleAdapter<InnerFragmentArtistPageDividerBinding> bioAdapter;
    private SingleAdapter<InnerFragmentArtistPageDividerBinding> topSongsDividerAdapter;
    private SingleAdapter<InnerFragmentArtistPageDividerBinding> albumDividerAdapter;
    private SingleAdapter<InnerFragmentArtistPageDividerBinding> similarArtistsDividerAdapter;
    private MutableLiveData<String> bioLiveData = new MutableLiveData<>();
    private MutableLiveData<Uri> bioUriLiveData = new MutableLiveData<>();
    private int spanCount = 1;

    private ConcatAdapter concatAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentArtistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        artistPageViewModel = new ViewModelProvider(requireActivity()).get(ArtistPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        init();
        initAppBar();
        initArtistInfo();
        initPlayButtons();
        initTopSongsView();
        initAlbumsView();
        initSimilarArtistsView();
        initRecyclerView();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();

        initializeMediaBrowser();
        MediaManager.registerPlaybackObserver(mediaBrowserListenableFuture, playbackViewModel);
        observePlayback();
    }

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

    private void init() {
        artistPageViewModel.setArtist(requireArguments().getParcelable(Constants.ARTIST_OBJECT));
    }

    private void initAppBar() {
        activity.setSupportActionBar(bind.animToolbar);
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        bind.collapsingToolbar.setTitle(artistPageViewModel.getArtist().getName());
        bind.animToolbar.setNavigationOnClickListener(v -> activity.navController.navigateUp());
        bind.collapsingToolbar.setExpandedTitleColor(getResources().getColor(R.color.white, null));
    }

    private void initArtistInfo() {
        bioAdapter = new SingleAdapter<>(
                vg -> InnerFragmentArtistPageDividerBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false),
                holder -> {
                    InnerFragmentArtistPageDividerBinding binding = (InnerFragmentArtistPageDividerBinding) holder.item;
                    binding.title.setText(R.string.artist_page_title_biography_section);
                    binding.more.setText(R.string.artist_page_title_biography_more_button);
                    bioLiveData.observe(getViewLifecycleOwner(), s -> {
                        binding.mainText.setText(s);
                        binding.mainText.setVisibility(View.VISIBLE);
                    });
                    bioUriLiveData.observe(getViewLifecycleOwner(), uri -> {
                        binding.more.setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(uri);
                            startActivity(intent);
                        });
                        binding.more.setVisibility(View.VISIBLE);
                    });
                }
        );

        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artistInfo -> {
            if (bioAdapter == null || bind == null)
                return;
            if (artistInfo == null) {
                bioAdapter.setDisplay(false);
            } else {
                String normalizedBio = MusicUtil.forceReadableString(artistInfo.getBiography());
                if (normalizedBio.trim().isEmpty()) {
                    bioAdapter.setDisplay(false);
                } else {
                    bioAdapter.setDisplay(true);
                    bioLiveData.postValue(normalizedBio);
                    bioUriLiveData.postValue(Uri.parse(artistInfo.getLastFmUrl()));
                }

                if (getContext() != null)CustomGlideRequest.Builder
                        .from(requireContext(), artistPageViewModel.getArtist().getId(), CustomGlideRequest.ResourceType.Artist)
                        .build()
                        .into(bind.artistBackdropImageView);
            }
            concatAdapter.notifyDataSetChanged();
        });
    }

    private void initPlayButtons() {
        buttonsAdapter = new SingleAdapter<>(
                vg -> InnerFragmentArtistPageButtonsBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false),
                holder -> {
                    InnerFragmentArtistPageButtonsBinding binding = (InnerFragmentArtistPageButtonsBinding) holder.item;
                    binding.artistPageShuffleButton.setOnClickListener(v -> {
                        artistPageViewModel.getArtistShuffleList().observe(getViewLifecycleOwner(), songs -> {
                            if (!songs.isEmpty()) {
                                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                                activity.setBottomSheetInPeek(true);
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_tracks), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                    binding.artistPageRadioButton.setOnClickListener(v -> {
                        artistPageViewModel.getArtistInstantMix().observe(getViewLifecycleOwner(), songs -> {
                            if (songs != null && !songs.isEmpty()) {
                                MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                                activity.setBottomSheetInPeek(true);
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.artist_error_retrieving_radio), Toast.LENGTH_SHORT).show();
                            }
                        });
                    });
                }
        );
        buttonsAdapter.setDisplay(true);
    }

    private void initTopSongsView() {
        topSongsDividerAdapter = new SingleAdapter<>(
                vg -> InnerFragmentArtistPageDividerBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false),
                holder -> {
                    InnerFragmentArtistPageDividerBinding binding = (InnerFragmentArtistPageDividerBinding) holder.item;
                    binding.title.setText(R.string.artist_page_title_most_streamed_song_section);
                    // is the more button used?
                    binding.more.setVisibility(View.VISIBLE);
                    binding.more.setText(R.string.artist_page_title_most_streamed_song_see_all_button);
                    binding.more.setOnClickListener(v -> {
                        Bundle bundle = new Bundle();
                        bundle.putString(Constants.MEDIA_BY_ARTIST, Constants.MEDIA_BY_ARTIST);
                        bundle.putParcelable(Constants.ARTIST_OBJECT, artistPageViewModel.getArtist());
                        activity.navController.navigate(R.id.action_artistPageFragment_to_songListPageFragment, bundle);
                    });
                }
        );
        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, true, null);
        setMediaBrowserListenableFuture();
        reapplyPlayback();
        artistPageViewModel.getArtistTopSongList().observe(getViewLifecycleOwner(), songs -> {
            if (bind == null) return;
            if (songs == null) {
                topSongsDividerAdapter.setDisplay(false);
            } else {
                topSongsDividerAdapter.setDisplay(!songs.isEmpty());
                songHorizontalAdapter.setItems(songs);
                reapplyPlayback();
            }
            concatAdapter.notifyDataSetChanged();
        });
    }

    private void initAlbumsView() {
        albumDividerAdapter = new SingleAdapter<>(
                vg -> InnerFragmentArtistPageDividerBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false),
                holder -> {
                    InnerFragmentArtistPageDividerBinding binding = (InnerFragmentArtistPageDividerBinding) holder.item;
                    binding.title.setText(R.string.artist_page_title_album_section);
                    binding.more.setVisibility(View.GONE);
                }
        );
        albumCatalogueAdapter = new AlbumCatalogueAdapter(this, false);
        artistPageViewModel.getAlbumList().observe(getViewLifecycleOwner(), albums -> {
            if (bind == null) return;
            if (albums == null) {
                albumDividerAdapter.setDisplay(false);
            } else {
                albumDividerAdapter.setDisplay(!albums.isEmpty());
                albumCatalogueAdapter.setItems(albums);
            }
            concatAdapter.notifyDataSetChanged();
        });
    }

    private void initSimilarArtistsView() {
        similarArtistsDividerAdapter = new SingleAdapter<>(
                vg -> InnerFragmentArtistPageDividerBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false),
                holder -> {
                    InnerFragmentArtistPageDividerBinding binding = (InnerFragmentArtistPageDividerBinding) holder.item;
                    binding.title.setText(R.string.artist_page_title_album_more_like_this_button);
                    binding.more.setVisibility(View.GONE);
                }
        );
        artistCatalogueAdapter = new ArtistCatalogueAdapter(this);
        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artist -> {
            if (bind == null) return;
            if (artist == null) {
                similarArtistsDividerAdapter.setDisplay(false);
            } else {
                if (artist.getSimilarArtists() != null)
                    similarArtistsDividerAdapter.setDisplay(!artist.getSimilarArtists().isEmpty());

                List<ArtistID3> artists = new ArrayList<>();
                if (artist.getSimilarArtists() != null) {
                    artists.addAll(artist.getSimilarArtists());
                }
                artistCatalogueAdapter.setItems(artists);
            }
            concatAdapter.notifyDataSetChanged();
        });
    }

    private void initRecyclerView() {
        concatAdapter = new ConcatAdapter(List.of(
                buttonsAdapter,
                bioAdapter,
                topSongsDividerAdapter,
                songHorizontalAdapter,
                albumDividerAdapter,
                albumCatalogueAdapter,
                similarArtistsDividerAdapter,
                artistCatalogueAdapter
        ));
        bind.artistPageRecycler.setAdapter(concatAdapter);
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((Activity) getContext()).getWindowManager()
                .getDefaultDisplay()
                .getMetrics(displayMetrics);
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        spanCount = height < width ? 6 : 2;
        GridLayoutManager layoutManager = new GridLayoutManager(requireContext(), spanCount);
        SpanSizeLookup lookup = new SpanSizeLookup();
        layoutManager.setSpanSizeLookup(lookup);
        bind.artistPageRecycler.setLayoutManager(layoutManager);
        bind.artistPageRecycler.addItemDecoration(
                new GridItemDecoration(spanCount, 20, false, lookup));
        bind.artistPageRecycler.setHasFixedSize(true);
    }

    private class SpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
        @Override
        public int getSpanSize(int position) {
            int prevCount =
                    buttonsAdapter.getItemCount() +
                    bioAdapter.getItemCount() +
                    topSongsDividerAdapter.getItemCount() +
                    songHorizontalAdapter.getItemCount() +
                    albumDividerAdapter.getItemCount();
            int albumCount = albumCatalogueAdapter.getItemCount();
            return (position < prevCount || position >= prevCount + albumCount) ? spanCount : 1;
        }
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

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
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
