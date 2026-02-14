package com.cappielloantonio.tempo.ui.fragment;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.databinding.FragmentAlbumCatalogueBinding;
import com.cappielloantonio.tempo.helper.recyclerview.GridItemDecoration;
import com.cappielloantonio.tempo.interfaces.ClickCallback;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import com.cappielloantonio.tempo.ui.adapter.AlbumCatalogueAdapter;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.Preferences;
import com.cappielloantonio.tempo.util.TileSizeManager;
import com.cappielloantonio.tempo.viewmodel.AlbumCatalogueViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@OptIn(markerClass = UnstableApi.class)
public class AlbumCatalogueFragment extends Fragment implements ClickCallback {
    private static final String TAG = "AlbumCatalogueFragment";

    private FragmentAlbumCatalogueBinding bind;
    private MainActivity activity;
    private AlbumCatalogueViewModel albumCatalogueViewModel;
    private int spanCount = 2;
    private int tileSpacing = 20;
    private AlbumCatalogueAdapter albumAdapter;
    private String currentSortOrder;
    private List<com.cappielloantonio.tempo.subsonic.models.AlbumID3> originalAlbums;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        currentSortOrder = Preferences.getAlbumSortOrder();

        initData();
    }

    @Override
    public void onResume() {
        super.onResume();
        String latestSort = Preferences.getAlbumSortOrder();
        
        if (!latestSort.equals(currentSortOrder)) {
            currentSortOrder = latestSort;
        }
        // Re-apply sort when returning to fragment
        if (originalAlbums != null && currentSortOrder != null) {
            applySortToAlbums(currentSortOrder);
        } else {
            Log.d(TAG, "onResume - Cannot re-sort, missing data");
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        albumCatalogueViewModel.stopLoading();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentAlbumCatalogueBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        TileSizeManager.getInstance().calculateTileSize( requireContext() );
        spanCount = TileSizeManager.getInstance().getTileSpanCount( requireContext() );
        tileSpacing = TileSizeManager.getInstance().getTileSpacing( requireContext() );

        initAppBar();
        initAlbumCatalogueView();
        initProgressLoader();

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    private void initData() {
        albumCatalogueViewModel = new ViewModelProvider(requireActivity()).get(AlbumCatalogueViewModel.class);
        albumCatalogueViewModel.loadAlbums();
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
                bind.toolbar.setTitle(R.string.album_catalogue_title);
            } else {
                bind.toolbar.setTitle(R.string.empty_string);
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initAlbumCatalogueView() {
        bind.albumCatalogueRecyclerView.setLayoutManager(new GridLayoutManager(requireContext(), spanCount));
        bind.albumCatalogueRecyclerView.addItemDecoration(new GridItemDecoration(spanCount, tileSpacing, false));
        bind.albumCatalogueRecyclerView.setHasFixedSize(true);

        albumAdapter = new AlbumCatalogueAdapter(this, true);
        albumAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        bind.albumCatalogueRecyclerView.setAdapter(albumAdapter);
        albumCatalogueViewModel.getAlbumList().observe(getViewLifecycleOwner(), albums -> {
            originalAlbums = albums;
            currentSortOrder = Preferences.getAlbumSortOrder();
            applySortToAlbums(currentSortOrder);
            updateSortIndicator();
        });

        bind.albumCatalogueRecyclerView.setOnTouchListener((v, event) -> {
            hideKeyboard(v);
            return false;
        });

        bind.albumListSortImageView.setOnClickListener(view -> showPopupMenu(view, R.menu.sort_album_popup_menu));
    }

    private void applySortToAlbums(String sortOrder) {
        if (originalAlbums == null) {
            return;
        }
        albumAdapter.setItemsWithoutFilter(originalAlbums);
        if (sortOrder != null) {
            albumAdapter.sort(sortOrder);
        }
    }

    private void initProgressLoader() {
        albumCatalogueViewModel.getLoadingStatus().observe(getViewLifecycleOwner(), isLoading -> {
            if (isLoading) {
                bind.albumListSortImageView.setEnabled(false);
                bind.albumListProgressLoader.setVisibility(View.VISIBLE);
            } else {
                bind.albumListSortImageView.setEnabled(true);
                bind.albumListProgressLoader.setVisibility(View.GONE);
            }
        });
    }

    private void updateSortIndicator() {
        if (bind == null) return;

        String sortText = getSortDisplayText(currentSortOrder);
        bind.albumListSortTextView.setText(sortText);
        bind.albumListSortTextView.setVisibility(View.VISIBLE);
    }

    private String getSortDisplayText(String sortOrder) {
        if (sortOrder == null) return "";
        
        switch (sortOrder) {
            case Constants.ALBUM_ORDER_BY_NAME:
                return getString(R.string.menu_sort_name);
            case Constants.ALBUM_ORDER_BY_ARTIST:
                return getString(R.string.menu_group_by_artist);
            case Constants.ALBUM_ORDER_BY_YEAR:
                return getString(R.string.menu_sort_year);
            case Constants.ALBUM_ORDER_BY_RANDOM:
                return getString(R.string.menu_sort_random);
            case Constants.ALBUM_ORDER_BY_RECENTLY_ADDED:
                return getString(R.string.menu_sort_recently_added);
            case Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED:
                return getString(R.string.menu_sort_recently_played);
            case Constants.ALBUM_ORDER_BY_MOST_PLAYED:
                return getString(R.string.menu_sort_most_played);
            default:
                return "";
        }
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
                albumAdapter.getFilter().filter(newText);
                return false;
            }
        });

        searchView.setPadding(-32, 0, 0, 0);
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void showPopupMenu(View view, int menuResource) {
        PopupMenu popup = new PopupMenu(requireContext(), view);
        popup.getMenuInflater().inflate(menuResource, popup.getMenu());

        popup.setOnMenuItemClickListener(menuItem -> {
            String newSortOrder = null;
            
            if (menuItem.getItemId() == R.id.menu_album_sort_name) {
                newSortOrder = Constants.ALBUM_ORDER_BY_NAME;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_artist) {
                newSortOrder = Constants.ALBUM_ORDER_BY_ARTIST;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_year) {
                newSortOrder = Constants.ALBUM_ORDER_BY_YEAR;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_random) {
                newSortOrder = Constants.ALBUM_ORDER_BY_RANDOM;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_recently_added) {
                newSortOrder = Constants.ALBUM_ORDER_BY_RECENTLY_ADDED;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_recently_played) {
                newSortOrder = Constants.ALBUM_ORDER_BY_RECENTLY_PLAYED;
            } else if (menuItem.getItemId() == R.id.menu_album_sort_most_played) {
                newSortOrder = Constants.ALBUM_ORDER_BY_MOST_PLAYED;
            }

            if (newSortOrder != null) {
                currentSortOrder = newSortOrder;
                Preferences.setAlbumSortOrder(newSortOrder);
                applySortToAlbums(newSortOrder);
                updateSortIndicator();
                return true;
            }

            return false;
        });

        popup.show();
    }

    @Override
    public void onAlbumClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumPageFragment, bundle);
        hideKeyboard(requireView());
    }

    @Override
    public void onAlbumLongClick(Bundle bundle) {
        Navigation.findNavController(requireView()).navigate(R.id.albumBottomSheetDialog, bundle);
    }
}