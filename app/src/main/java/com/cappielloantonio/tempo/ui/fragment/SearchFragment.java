package com.cappielloantonio.tempo.ui.fragment;

import android.content.ComponentName;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.MediaBrowser;
import androidx.media3.session.SessionToken;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentSearchBinding;
import com.cappielloantonio.tempo.databinding.InnerFragmentSearchGroupBinding;
import com.cappielloantonio.tempo.databinding.ItemLibraryArtistBinding;
import com.cappielloantonio.tempo.helper.recyclerview.CustomLinearSnapHelper;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.service.MediaManager;
import com.cappielloantonio.tempo.service.MediaService;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.AlbumAdapter;
import com.cappielloantonio.tempo.ui.adapter.ArtistAdapter;
import com.cappielloantonio.tempo.ui.adapter.SingleAdapter;
import com.cappielloantonio.tempo.ui.adapter.SongHorizontalAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.viewmodel.PlaybackViewModel;
import com.cappielloantonio.tempo.viewmodel.SearchViewModel;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Collections;
import java.util.List;

@UnstableApi
public class SearchFragment extends Fragment implements ClickCallback {
    private static final String TAG = "SearchFragment";

    private FragmentSearchBinding bind;
    private MainActivity activity;
    private SearchViewModel searchViewModel;
    private PlaybackViewModel playbackViewModel;

    ArtistAdapter realArtistAdapter;
    AlbumAdapter realAlbumAdapter;
    SongHorizontalAdapter realSongAdapter;
    private SingleAdapter<InnerFragmentSearchGroupBinding> artistAdapter;
    private SingleAdapter<InnerFragmentSearchGroupBinding> albumAdapter;
    private SingleAdapter<InnerFragmentSearchGroupBinding> songAdapter;
    private ConcatAdapter concatAdapter;

    private ListenableFuture<MediaBrowser> mediaBrowserListenableFuture;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentSearchBinding.inflate(inflater, container, false);
        View view = bind.getRoot();
        searchViewModel = new ViewModelProvider(requireActivity()).get(SearchViewModel.class);
        playbackViewModel = new ViewModelProvider(requireActivity()).get(PlaybackViewModel.class);

        initSearchResultView();
        initSearchView();
        inputFocus();

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
        if (songAdapter != null) setMediaBrowserListenableFuture();
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

    private void initSearchResultView() {
        realArtistAdapter = new ArtistAdapter(this, false, false);
        artistAdapter = new SingleAdapter<>(
                vg -> {
                    InnerFragmentSearchGroupBinding binding = InnerFragmentSearchGroupBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false);
                    CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
                    artistSnapHelper.attachToRecyclerView(binding.searchResultGroupRecyclerView);
                    return binding;
                },
                holder -> {
                    InnerFragmentSearchGroupBinding binding = (InnerFragmentSearchGroupBinding) holder.item;
                    binding.searchResultGroupRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.searchResultGroupRecyclerView.setHasFixedSize(true);
                    binding.searchResultGroupRecyclerView.setAdapter(realArtistAdapter);
                    binding.searchResultGroupTitle.setText(R.string.search_title_artist);
                }
        );

        realAlbumAdapter = new AlbumAdapter(this);
        albumAdapter = new SingleAdapter<>(
                vg -> {
                    InnerFragmentSearchGroupBinding binding = InnerFragmentSearchGroupBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false);
                    CustomLinearSnapHelper artistSnapHelper = new CustomLinearSnapHelper();
                    artistSnapHelper.attachToRecyclerView(binding.searchResultGroupRecyclerView);
                    return binding;
                },
                holder -> {
                    InnerFragmentSearchGroupBinding binding = (InnerFragmentSearchGroupBinding) holder.item;
                    binding.searchResultGroupRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false));
                    binding.searchResultGroupRecyclerView.setHasFixedSize(true);
                    binding.searchResultGroupRecyclerView.setAdapter(realAlbumAdapter);
                    binding.searchResultGroupTitle.setText(R.string.search_title_album);
                }
        );

        realSongAdapter = new SongHorizontalAdapter(getViewLifecycleOwner(), this, true, false, null);
        songAdapter = new SingleAdapter<>(
                vg -> InnerFragmentSearchGroupBinding.inflate(LayoutInflater.from(vg.getContext()), vg, false),
                holder -> {
                    InnerFragmentSearchGroupBinding binding = (InnerFragmentSearchGroupBinding) holder.item;
                    binding.searchResultGroupRecyclerView.setVisibility(View.GONE);
                    binding.searchResultGroupTitle.setText(R.string.search_title_song);
                }
        );

        concatAdapter = new ConcatAdapter(List.of(artistAdapter, albumAdapter, songAdapter, realSongAdapter));
        bind.searchResultsRecyclerView.setAdapter(concatAdapter);
        bind.searchResultsRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext(), LinearLayoutManager.VERTICAL, false));
        bind.searchResultsRecyclerView.setHasFixedSize(true);

        setMediaBrowserListenableFuture();
        reapplyPlayback();
    }

    private void initSearchView() {
        setRecentSuggestions();

        bind.searchView
                .getEditText()
                .setOnEditorActionListener((textView, actionId, keyEvent) -> {
                    String query = bind.searchView.getText().toString();

                    if (isQueryValid(query)) {
                        search(query);
                        return true;
                    }

                    return false;
                });

        bind.searchView
                .getEditText()
                .addTextChangedListener(new TextWatcher() {
                    @Override
                    public void beforeTextChanged(CharSequence charSequence, int start, int count, int after) {

                    }

                    @Override
                    public void onTextChanged(CharSequence charSequence, int start, int before, int count) {
                        if (start + count > 1) {
                            setSearchSuggestions(charSequence.toString());
                        } else {
                            setRecentSuggestions();
                        }
                    }

                    @Override
                    public void afterTextChanged(Editable editable) {

                    }
                });
    }

    public void setRecentSuggestions() {
        bind.searchViewSuggestionContainer.removeAllViews();

        for (String suggestion : searchViewModel.getRecentSearchSuggestion()) {
            View view = LayoutInflater.from(bind.searchViewSuggestionContainer.getContext()).inflate(R.layout.item_search_suggestion, bind.searchViewSuggestionContainer, false);

            ImageView leadingImageView = view.findViewById(R.id.search_suggestion_icon);
            TextView titleView = view.findViewById(R.id.search_suggestion_title);
            ImageView tailingImageView = view.findViewById(R.id.search_suggestion_delete_icon);

            leadingImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_history, null));
            titleView.setText(suggestion);

            view.setOnClickListener(v -> search(suggestion));

            tailingImageView.setOnClickListener(v -> {
                searchViewModel.deleteRecentSearch(suggestion);
                setRecentSuggestions();
            });

            bind.searchViewSuggestionContainer.addView(view);
        }
    }

    public void setSearchSuggestions(String query) {
        searchViewModel.getSearchSuggestion(query).observe(getViewLifecycleOwner(), suggestions -> {
            bind.searchViewSuggestionContainer.removeAllViews();

            for (String suggestion : suggestions) {
                View view = LayoutInflater.from(bind.searchViewSuggestionContainer.getContext()).inflate(R.layout.item_search_suggestion, bind.searchViewSuggestionContainer, false);

                ImageView leadingImageView = view.findViewById(R.id.search_suggestion_icon);
                TextView titleView = view.findViewById(R.id.search_suggestion_title);
                ImageView tailingImageView = view.findViewById(R.id.search_suggestion_delete_icon);

                leadingImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_search, null));
                titleView.setText(suggestion);
                tailingImageView.setVisibility(View.GONE);

                view.setOnClickListener(v -> search(suggestion));

                bind.searchViewSuggestionContainer.addView(view);
            }
        });
    }

    public void search(String query) {
        searchViewModel.setQuery(query);
        bind.searchBar.setText(query);
        bind.searchView.hide();
        performSearch(query);
    }

    private void performSearch(String query) {
        searchViewModel.search3(query).observe(getViewLifecycleOwner(), result -> {
            if (bind != null) {
                Log.d("Search", "artists: " + (result.getArtists() == null ? 0 : result.getArtists().size()));
                realArtistAdapter.setItems(result.getArtists() == null ?  Collections.emptyList() : result.getArtists());
                artistAdapter.setDisplay(result.getArtists() != null && !result.getArtists().isEmpty());

                Log.d("Search", "albums: " + (result.getAlbums() == null ? 0 : result.getAlbums().size()));
                realAlbumAdapter.setItems(result.getAlbums() == null ? Collections.emptyList() : result.getAlbums());
                albumAdapter.setDisplay(result.getAlbums() != null && !result.getAlbums().isEmpty());

                Log.d("Search", "songs: " + (result.getSongs() == null ? 0 : result.getSongs().size()));
                realSongAdapter.setItems(result.getSongs() == null ? Collections.emptyList() : result.getSongs());
                songAdapter.setDisplay(result.getSongs() != null && !result.getSongs().isEmpty());

                concatAdapter.notifyDataSetChanged();
            }
        });

        bind.searchResultsRecyclerView.setVisibility(View.VISIBLE);
    }

    private boolean isQueryValid(String query) {
        return !query.equals("") && query.trim().length() > 0;
    }

    private void inputFocus() {
        bind.searchView.show();
    }

    private void initializeMediaBrowser() {
        mediaBrowserListenableFuture = new MediaBrowser.Builder(requireContext(), new SessionToken(requireContext(), new ComponentName(requireContext(), MediaService.class))).buildAsync();
    }

    private void releaseMediaBrowser() {
        MediaBrowser.releaseFuture(mediaBrowserListenableFuture);
    }

    @Override
    public void onMediaClick(Bundle bundle) {
        Log.d("SearchFragment", "onMediaClick");
        MediaManager.startQueue(mediaBrowserListenableFuture, bundle.getParcelableArrayList(Constants.TRACKS_OBJECT), bundle.getInt(Constants.ITEM_POSITION));
        realSongAdapter.notifyDataSetChanged();
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
            if (realSongAdapter != null) {
                Boolean playing = playbackViewModel.getIsPlaying().getValue();
                realSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
        playbackViewModel.getIsPlaying().observe(getViewLifecycleOwner(), playing -> {
            if (realSongAdapter != null) {
                String id = playbackViewModel.getCurrentSongId().getValue();
                realSongAdapter.setPlaybackState(id, playing != null && playing);
            }
        });
    }

    private void reapplyPlayback() {
        if (realSongAdapter != null) {
            String id = playbackViewModel.getCurrentSongId().getValue();
            Boolean playing = playbackViewModel.getIsPlaying().getValue();
            realSongAdapter.setPlaybackState(id, playing != null && playing);
        }
    }

    private void setMediaBrowserListenableFuture() {
        realSongAdapter.setMediaBrowserListenableFuture(mediaBrowserListenableFuture);
    }
}
