package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.util.Constants;

/**
 * Dedicated "All songs" screen. It reuses the shared song-list page and injects its media
 * type, so it can be opened from the drawer with no navigation arguments.
 */
@UnstableApi
public class AllSongsFragment extends SongListPageFragment {
    @Nullable
    @Override
    protected Bundle prepareArgs(@Nullable Bundle args) {
        Bundle prepared = args == null ? new Bundle() : new Bundle(args);
        prepared.putString(Constants.MEDIA_ALL, Constants.MEDIA_ALL);
        return prepared;
    }
}
