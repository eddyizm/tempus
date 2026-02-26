package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;
import android.widget.ToggleButton;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentArtistPageBinding;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.helper.recyclerview.CustomLinearSnapHelper;
import com.cappielloantonio.tempo.helper.recyclerview.GridItemDecoration;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.AlbumCarouselAdapter;
import com.cappielloantonio.tempo.ui.adapter.ArtistCarouselAdapter;
import com.cappielloantonio.tempo.ui.adapter.ArtistCatalogueAdapter;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.viewmodel.ArtistPageViewModel;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@UnstableApi
public class ArtistPageFragment extends Fragment implements ClickCallback {
    private FragmentArtistPageBinding bind;
    private MainActivity activity;
    private ArtistPageViewModel artistPageViewModel;
    private PlaybackViewModel playbackViewModel;

    private SongHorizontalAdapter songHorizontalAdapter;
    private AlbumCarouselAdapter mainAlbumAdapter;
    private AlbumCarouselAdapter epAdapter;
    private AlbumCarouselAdapter singleAdapter;
    private AlbumCarouselAdapter appearsOnAdapter;
    private ArtistCarouselAdapter similarArtistAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    private int spanCount = 2;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentArtistPageBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        artistPageViewModel = new ViewModelProvider(requireActivity()).get(ArtistPageViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCount = Preferences.getLandscapeItemsPerRow();
        }

        init(view);
        initAppBar();
        initArtistInfo();
        initPlayButtons();
        initTopSongsView();
        initCategorizedAlbumsView();
        initSimilarArtistsView();

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

    private void init(View view) {
        artistPageViewModel.setArtist(requireArguments().getParcelable(Constants.ARTIST_OBJECT));
        artistPageViewModel.fetchCategorizedAlbums(getViewLifecycleOwner());

        bind.mostStreamedSongTextViewClickable.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString(Constants.MEDIA_BY_ARTIST, Constants.MEDIA_BY_ARTIST);
            bundle.putParcelable(Constants.ARTIST_OBJECT, artistPageViewModel.getArtist());
            activity.navController.navigate(R.id.action_artistPageFragment_to_songListPageFragment, bundle);
        });

        ToggleButton favoriteToggle = view.findViewById(R.id.button_favorite);
        favoriteToggle.setChecked(artistPageViewModel.getArtist().getStarred() != null);
        favoriteToggle.setOnClickListener(v -> artistPageViewModel.setFavorite(requireContext()));

        Button bioToggle = view.findViewById(R.id.button_toggle_bio);
        bioToggle.setOnClickListener(v ->
                Toast.makeText(getActivity(), R.string.artist_no_artist_info_toast, Toast.LENGTH_SHORT).show());
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
        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artistInfo -> {
            if (artistInfo == null) {
                if (bind != null) bind.artistPageBioSector.setVisibility(View.GONE);
            } else {
                if (getContext() != null && bind != null) {
                    ArtistID3 currentArtist = artistPageViewModel.getArtist();
                        String primaryId = currentArtist.getCoverArtId() != null && !currentArtist.getCoverArtId().trim().isEmpty()
                            ? currentArtist.getCoverArtId()
                            : currentArtist.getId();
                    
                    final String fallbackId = (Objects.requireNonNull(primaryId).equals(currentArtist.getCoverArtId()) &&
                                            currentArtist.getId() != null && 
                                            !currentArtist.getId().equals(primaryId))
                            ? currentArtist.getId()
                            : null;
                    
                    CustomGlideRequest.Builder
                            .from(requireContext(), primaryId, CustomGlideRequest.ResourceType.Artist)
                            .build()
                            .listener(new com.bumptech.glide.request.RequestListener<Drawable>() {
                                @Override
                                public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e,
                                                            Object model,
                                                            @NonNull com.bumptech.glide.request.target.Target<Drawable> target,
                                                            boolean isFirstResource) {
                                    if (e != null) {
                                        e.getMessage();
                                        if (e.getMessage().contains("400") && fallbackId != null) {

                                            Log.d("ArtistCover", "Primary ID failed (400), trying fallback: " + fallbackId);

                                            CustomGlideRequest.Builder
                                                    .from(requireContext(), fallbackId, CustomGlideRequest.ResourceType.Artist)
                                                    .build()
                                                    .into(bind.artistBackdropImageView);
                                            return true;
                                        }
                                    }
                                    return false;
                                }

                                @Override
                                public boolean onResourceReady(@NonNull Drawable resource,
                                                               @NonNull Object model,
                                                               com.bumptech.glide.request.target.Target<Drawable> target,
                                                               @NonNull com.bumptech.glide.load.DataSource dataSource,
                                                               boolean isFirstResource) {
                                    return false;
                                }
                            })
                            .into(bind.artistBackdropImageView);
                }

                if (bind != null) {
                    String normalizedBio = MusicUtil.forceReadableString(artistInfo.getBiography()).trim();
                    String lastFmUrl = artistInfo.getLastFmUrl();

                    if (normalizedBio.isEmpty()) {
                        bind.bioTextView.setVisibility(View.GONE);
                    } else {
                        bind.bioTextView.setText(normalizedBio);
                    }

                    if (lastFmUrl == null) {
                        bind.bioMoreTextViewClickable.setVisibility(View.GONE);
                    } else {
                        bind.bioMoreTextViewClickable.setOnClickListener(v -> {
                            Intent intent = new Intent(Intent.ACTION_VIEW);
                            intent.setData(Uri.parse(artistInfo.getLastFmUrl()));
                            startActivity(intent);
                        });
                        bind.bioMoreTextViewClickable.setVisibility(View.VISIBLE);
                    }

                    if (!normalizedBio.isEmpty() || lastFmUrl != null) {
                        View view = bind.getRoot();

                        Button bioToggle = view.findViewById(R.id.button_toggle_bio);
                        bioToggle.setOnClickListener(v -> {
                            if (bind != null) {
                                boolean displayBio = Preferences.getArtistDisplayBiography();
                                Preferences.setArtistDisplayBiography(!displayBio);
                                bind.artistPageBioSector.setVisibility(displayBio ? View.GONE : View.VISIBLE);
                            }
                        });

                        boolean displayBio = Preferences.getArtistDisplayBiography();
                        bind.artistPageBioSector.setVisibility(displayBio ? View.VISIBLE : View.GONE);
                    }
                }
            }
        });
    }

    private void initPlayButtons() {
        bind.artistPageShuffleButton.setOnClickListener(v -> artistPageViewModel.getArtistShuffleList().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                if (songs != null && !songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                    artistPageViewModel.getArtistShuffleList().removeObserver(this);
                }
            }
        }));

        bind.artistPageRadioButton.setOnClickListener(v -> artistPageViewModel.getArtistInstantMix().observe(getViewLifecycleOwner(), new Observer<List<Child>>() {
            @Override
            public void onChanged(List<Child> songs) {
                if (songs != null && !songs.isEmpty()) {
                    MediaManager.startQueue(mediaBrowserListenableFuture, songs, 0);
                    activity.setBottomSheetInPeek(true);
                    artistPageViewModel.getArtistInstantMix().removeObserver(this);
                }
            }
        }));
    }

    private void initTopSongsView() {
        bind.mostStreamedSongRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        songHorizontalAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, true, null);
        bind.mostStreamedSongRecyclerView.setAdapter(songHorizontalAdapter);
        setMediaBrowserListenableFuture();
        reapplyPlayback();
        artistPageViewModel.getArtistTopSongList().observe(getViewLifecycleOwner(), songs -> {
            if (songs == null) {
                if (bind != null) bind.artistPageTopSongsSector.setVisibility(View.GONE);
            } else {
                if (bind != null) {
                    bind.artistPageTopSongsSector.setVisibility(!songs.isEmpty() ? View.VISIBLE : View.GONE);
                    bind.mostStreamedSongTextViewClickable.setVisibility(songs.size() > 3 ? View.VISIBLE : View.GONE);
                }
                songHorizontalAdapter.setItems(songs.stream().limit(3).collect(java.util.stream.Collectors.toList()));
                reapplyPlayback();
            }
        });
    }

    private void initCategorizedAlbumsView() {
        // Main Albums
        bind.mainAlbumsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.mainAlbumsRecyclerView.setHasFixedSize(true);
        mainAlbumAdapter = new AlbumCarouselAdapter(this, false);
        bind.mainAlbumsRecyclerView.setAdapter(mainAlbumAdapter);
        artistPageViewModel.getMainAlbums().observe(getViewLifecycleOwner(), albums -> {
            if (bind != null) {
                bind.artistPageMainAlbumsSector.setVisibility(albums != null && !albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (albums != null) {
                    bind.mainAlbumsSeeAllTextView.setVisibility(albums.size() > 5 ? View.VISIBLE : View.GONE);
                    mainAlbumAdapter.setItems(albums);
                    bind.mainAlbumsSeeAllTextView.setOnClickListener(v -> navigateToAlbumList(getString(R.string.artist_page_title_album_section), albums));
                }
            }
        });

        // EPs
        bind.epsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.epsRecyclerView.setHasFixedSize(true);
        epAdapter = new AlbumCarouselAdapter(this, false);
        bind.epsRecyclerView.setAdapter(epAdapter);
        artistPageViewModel.getEPs().observe(getViewLifecycleOwner(), albums -> {
            if (bind != null) {
                bind.artistPageEpsSector.setVisibility(albums != null && !albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (albums != null) {
                    bind.epsSeeAllTextView.setVisibility(albums.size() > 5 ? View.VISIBLE : View.GONE);
                    epAdapter.setItems(albums);
                    bind.epsSeeAllTextView.setOnClickListener(v -> navigateToAlbumList(getString(R.string.artist_page_title_ep_section), albums));
                }
            }
        });

        // Singles
        bind.singlesRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.singlesRecyclerView.setHasFixedSize(true);
        singleAdapter = new AlbumCarouselAdapter(this, false);
        bind.singlesRecyclerView.setAdapter(singleAdapter);
        artistPageViewModel.getSingles().observe(getViewLifecycleOwner(), albums -> {
            if (bind != null) {
                bind.artistPageSinglesSector.setVisibility(albums != null && !albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (albums != null) {
                    bind.singlesSeeAllTextView.setVisibility(albums.size() > 5 ? View.VISIBLE : View.GONE);
                    singleAdapter.setItems(albums);
                    bind.singlesSeeAllTextView.setOnClickListener(v -> navigateToAlbumList(getString(R.string.artist_page_title_single_section), albums));
                }
            }
        });

        // Appears On
        bind.appearsOnRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.appearsOnRecyclerView.setHasFixedSize(true);
        appearsOnAdapter = new AlbumCarouselAdapter(this, true); // Show artist name for Appears On
        bind.appearsOnRecyclerView.setAdapter(appearsOnAdapter);
        artistPageViewModel.getAppearsOn().observe(getViewLifecycleOwner(), albums -> {
            if (bind != null) {
                bind.artistPageAppearsOnSector.setVisibility(albums != null && !albums.isEmpty() ? View.VISIBLE : View.GONE);
                if (albums != null) {
                    bind.appearsOnSeeAllTextView.setVisibility(albums.size() > 5 ? View.VISIBLE : View.GONE);
                    appearsOnAdapter.setItems(albums);
                    bind.appearsOnSeeAllTextView.setOnClickListener(v -> navigateToAlbumList(getString(R.string.artist_page_title_appears_on_section), albums));
                }
            }
        });
    }

    private void navigateToAlbumList(String title, List<com.cappielloantonio.tempo.subsonic.models.AlbumID3> albums) {
        Bundle bundle = new Bundle();
        bundle.putString(Constants.ALBUM_LIST_TITLE, title);
        bundle.putParcelableArrayList(Constants.ALBUMS_OBJECT, new ArrayList<>(albums));
        Navigation.findNavController(requireView()).navigate(R.id.albumListPageFragment, bundle);
    }

    private void initSimilarArtistsView() {
        bind.similarArtistsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
        bind.similarArtistsRecyclerView.setHasFixedSize(true);

        similarArtistAdapter = new ArtistCarouselAdapter(this);
        bind.similarArtistsRecyclerView.setAdapter(similarArtistAdapter);

        artistPageViewModel.getArtistInfo(artistPageViewModel.getArtist().getId()).observe(getViewLifecycleOwner(), artist -> {
            if (artist == null) {
                if (bind != null) bind.similarArtistSector.setVisibility(View.GONE);
            } else {
                if (bind != null && artist.getSimilarArtists() != null)
                    bind.similarArtistSector.setVisibility(!artist.getSimilarArtists().isEmpty() ? View.VISIBLE : View.GONE);

                List<ArtistID3> artists = new ArrayList<>();

                if (artist.getSimilarArtists() != null) {
                    artists.addAll(artist.getSimilarArtists());
                }

                similarArtistAdapter.setItems(artists);
            }
        });

        CustomLinearSnapHelper similarArtistSnapHelper = new CustomLinearSnapHelper();
        similarArtistSnapHelper.attachToRecyclerView(bind.similarArtistsRecyclerView);
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