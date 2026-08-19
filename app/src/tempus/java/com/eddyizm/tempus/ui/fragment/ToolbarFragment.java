package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.R;
import com.eddyizm.tempus.databinding.FragmentToolbarBinding;
import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.repository.ServerRepository;
import com.eddyizm.tempus.subsonic.models.MusicFolder;
import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.util.MusicFolderUtil;
import com.eddyizm.tempus.util.Preferences;
import com.eddyizm.tempus.viewmodel.LibraryViewModel;
import com.eddyizm.tempus.viewmodel.MainViewModel;
import com.google.android.gms.cast.framework.CastButtonFactory;

import java.util.ArrayList;
import java.util.List;

@UnstableApi
public class ToolbarFragment extends Fragment {
    private static final String TAG = "ToolbarFragment";

    private static final int MUSIC_LIBRARY_MENU_GROUP = 1;
    private static final int ALL_LIBRARIES_ITEM_ID = 0;
    private static final int NO_CHECKED_ITEM = -1;

    private FragmentToolbarBinding bind;
    private MainActivity activity;
    private LibraryViewModel libraryViewModel;
    private MainViewModel mainViewModel;

    private final List<MusicFolder> musicFolders = new ArrayList<>();

    private String serverName;

    public ToolbarFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.main_page_menu, menu);
        CastButtonFactory.setUpMediaRouteButton(requireContext(), menu, R.id.media_route_menu_item);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        activity = (MainActivity) getActivity();

        bind = FragmentToolbarBinding.inflate(inflater, container, false);
        View view = bind.getRoot();

        libraryViewModel = new ViewModelProvider(requireActivity()).get(LibraryViewModel.class);
        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initMusicLibrarySwitcher();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Catches a restore that put the pager back on Podcast or Radio.
        updateMusicLibraryIndicator();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        bind = null;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.action_search) {
            activity.navController.navigate(R.id.searchFragment);
            return true;
        } else if (item.getItemId() == R.id.action_settings) {
            activity.navController.navigate(R.id.settingsFragment);
            return true;
        }

        return false;
    }

    private void initMusicLibrarySwitcher() {
        if (!isLibraryScopedScreen()) return;

        bind.toolbarTitleContainer.setOnClickListener(this::showMusicLibraryMenu);

        // The list is cached on the activity scoped LibraryViewModel, so once it has arrived the
        // Library tab and this share it. Until then either screen can be the one that fetches it.
        libraryViewModel.getMusicFolders(getViewLifecycleOwner()).observe(getViewLifecycleOwner(), folders -> {
            musicFolders.clear();
            if (folders != null) musicFolders.addAll(folders);
            updateMusicLibraryIndicator();
        });

        mainViewModel.getActiveMusicFolderId().observe(getViewLifecycleOwner(), musicFolderId -> updateMusicLibraryIndicator());

        // The name lives in the server table, not in Preferences, so it takes a read of every
        // row and a match on the stored id.
        new ServerRepository().getLiveServer().observe(getViewLifecycleOwner(), servers -> {
            String serverId = Preferences.getServerId();

            serverName = null;
            if (servers != null && serverId != null) {
                for (Server server : servers) {
                    if (serverId.equals(server.getServerId())) serverName = server.getServerName();
                }
            }

            updateMusicLibraryIndicator();
        });
    }

    /**
     * Home calls this when its pager changes page.
     */
    public void refreshMusicLibraryIndicator() {
        updateMusicLibraryIndicator();
    }

    /**
     * Podcasts and internet radio are no more library scoped than downloads are, and they are
     * pages of Home's pager, so the nav destination cannot tell them apart. Asked of Home on every
     * update instead of being pushed once.
     */
    private boolean isLibraryScopedTab() {
        Fragment parent = getParentFragment();
        return !(parent instanceof HomeFragment) || ((HomeFragment) parent).isLibraryScopedTab();
    }

    /**
     * Downloads are local files that no library filter reaches, so a line naming one there would
     * describe something other than what is on the screen.
     *
     * Asked of the host fragment, not the NavController: a dialog destination becomes the current
     * destination without destroying this view, so consulting the graph made Downloads answer yes
     * while a bottom sheet was open.
     */
    private boolean isLibraryScopedScreen() {
        return !(getParentFragment() instanceof DownloadFragment);
    }

    private void updateMusicLibraryIndicator() {
        if (bind == null) return;

        // One library offers no choice. A filter in force has to stay visible whatever the folder
        // list says, because this is the only way to clear one from outside Settings, and the list
        // is empty for as long as the server has not answered.
        boolean canChoose = musicFolders.size() > 1 || Preferences.getActiveMusicFolderId() != null;
        boolean visible = canChoose && isLibraryScopedScreen() && isLibraryScopedTab();

        // Podcast and Radio are the tabs the library never reaches, so the line carries the
        // server instead of nothing. No caret there, since nothing is tappable.
        boolean showServerName = !visible && serverName != null && isLibraryScopedScreen() && !isLibraryScopedTab();

        bind.toolbarTitleContainer.setClickable(visible);
        bind.toolbarMusicLibraryTextView.setVisibility(visible || showServerName ? View.VISIBLE : View.GONE);
        bind.toolbarMusicLibraryTextView.setCompoundDrawablesRelativeWithIntrinsicBounds(
                0, 0, visible ? R.drawable.ic_expand_more_small : 0, 0);

        if (showServerName) {
            bind.toolbarMusicLibraryTextView.setText(serverName);
            return;
        }

        if (!visible) return;

        String name = MusicFolderUtil.resolveMusicFolderName(musicFolders, Preferences.getActiveMusicFolderId());
        bind.toolbarMusicLibraryTextView.setText(name != null ? name : getString(R.string.settings_music_library_all));
    }

    private void showMusicLibraryMenu(View anchor) {
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        String activeMusicFolderId = Preferences.getActiveMusicFolderId();

        // Item ids are the folder's position in the list plus one, leaving zero for every library.
        // NO_CHECKED_ITEM means the stored library is not in the list, which happens when the
        // server dropped it or never answered. Checking every library there would say no filter is
        // running while the requests still carry one, so nothing is checked and the line above the
        // menu keeps naming the id that is actually in force.
        int checkedItemId = activeMusicFolderId == null ? ALL_LIBRARIES_ITEM_ID : NO_CHECKED_ITEM;
        popup.getMenu().add(MUSIC_LIBRARY_MENU_GROUP, ALL_LIBRARIES_ITEM_ID, ALL_LIBRARIES_ITEM_ID, R.string.settings_music_library_all);

        for (int i = 0; i < musicFolders.size(); i++) {
            MusicFolder musicFolder = musicFolders.get(i);
            if (musicFolder.getId() == null) continue;

            String name = musicFolder.getName() != null ? musicFolder.getName() : musicFolder.getId();
            popup.getMenu().add(MUSIC_LIBRARY_MENU_GROUP, i + 1, i + 1, name);

            if (musicFolder.getId().equals(activeMusicFolderId)) checkedItemId = i + 1;
        }

        // Checkable has to be granted to the group before an item will accept being checked.
        popup.getMenu().setGroupCheckable(MUSIC_LIBRARY_MENU_GROUP, true, true);
        if (checkedItemId != NO_CHECKED_ITEM) popup.getMenu().findItem(checkedItemId).setChecked(true);

        // Resolve the id while the menu is built. Reading it back by position on click would pick
        // whatever now sits at that index if a later fetch replaced the list.
        List<String> itemIds = new ArrayList<>();
        for (MusicFolder musicFolder : musicFolders) itemIds.add(musicFolder.getId());

        popup.setOnMenuItemClickListener(menuItem -> {
            int index = menuItem.getItemId() - 1;
            String selectedId = index < 0 ? Preferences.MUSIC_FOLDER_ALL : itemIds.get(index);

            // A null id never got a menu item, so reaching here with one is not possible today.
            if (selectedId == null) return true;

            mainViewModel.setActiveMusicFolderId(selectedId);
            return true;
        });

        popup.show();
    }
}
