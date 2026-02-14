package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
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
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentArtistCatalogueBinding;
import com.cappielloantonio.tempo.helper.recyclerview.GridItemDecoration;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.ArtistCatalogueAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.TileSizeManager;
import com.cappielloantonio.tempo.viewmodel.ArtistCatalogueViewModel;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class ArtistCatalogueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "ArtistCatalogueFragment";

    private FragmentArtistCatalogueBinding bind;
    private MainActivity activity;
    private ArtistCatalogueViewModel artistCatalogueViewModel;

    private ArtistCatalogueAdapter artistAdapter;

    private int spanCount = 2;
    private int tileSpacing = 20;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);

        initData();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentArtistCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        TileSizeManager.getInstance().calculateTileSize( requireContext() );
        spanCount = TileSizeManager.getInstance().getTileSpanCount( requireContext() );
        tileSpacing = TileSizeManager.getInstance().getTileSpacing( requireContext() );

        initAppBar();
        initArtistCatalogueView();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initData() {
        artistCatalogueViewModel = new ViewModelProvider(requireActivity()).get(ArtistCatalogueViewModel.class);
        artistCatalogueViewModel.loadArtists();
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
            if ((bind.artistInfoSector.getHeight() + verticalOffset) < (2 * ViewCompat.getMinimumHeight(bind.toolbar))) {
                bind.toolbar.setTitle(R.string.artist_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initArtistCatalogueView() {
        bind.artistCatalogueRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        bind.artistCatalogueRecyclerView.addItemDecoration(new GridItemDecoration(spanCount, tileSpacing, false));
        bind.artistCatalogueRecyclerView.setHasFixedSize(true);

        artistAdapter = new ArtistCatalogueAdapter(this);
        artistAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        bind.artistCatalogueRecyclerView.setAdapter(artistAdapter);
        artistCatalogueViewModel.getArtistList().observe(getViewLifecycleOwner(), artistList -> {
            artistAdapter.setItems(artistList);
            artistAdapter.sort(Preferences.getArtistSortOrder());
        });

        bind.artistCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.artistListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_artist_popup_menu));
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        inflater.inflate(R.menu.toolbar_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.action_search);

        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setImeOptions(EditorInfo.IME_ACTION_DONE);

        searchView.setQueryHint(getString(R.string.filter_artist));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                // this toast may be overkill...
                Toast.makeText(requireContext(), "Search: " + query, Toast.LENGTH_SHORT).show();
                filterArtists(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterArtists(newText);
                return true;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    private void filterArtists(String query) {
        List<ArtistID3> allArtists = artistCatalogueViewModel.getArtistList().getValue();
        
        if (allArtists == null || allArtists.isEmpty()) {
            return;
        }
        
        if (query == null || query.trim().isEmpty()) {
            artistAdapter.setItems(allArtists);
        } else {
            String searchQuery = query.toLowerCase().trim();
            List<ArtistID3> filteredArtists = new ArrayList<>();
            
            for (ArtistID3 artist : allArtists) {
                if (artist.getName() != null && 
                    artist.getName().toLowerCase().contains(searchQuery)) {
                    filteredArtists.add(artist);
                }
            }
            artistAdapter.setItems(filteredArtists);
        }
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.menu_artist_sort_name) {
                artistAdapter.sort(Constants.ARTIST_ORDER_BY_NAME);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_artist_sort_random) {
                artistAdapter.sort(Constants.ARTIST_ORDER_BY_RANDOM);
                return true;
            } else if (menuItem.getItemId() == R.id.menu_artist_sort_album_count) {
                artistAdapter.sort(Constants.ARTIST_ORDER_BY_ALBUM_COUNT);
                return true;
            }

            return false;
        });

        popup.show();
    }

    @Override
    public void onArtistClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onArtistLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.artistBottomSheetDialog, bundle);
    }
}