package com.eddyizm.tempus.ui.fragment;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;

import com.eddyizm.tempus.util.Constants;

/**
 * Dedicated "All songs by this artist" screen. It reuses the shared song-list page and
 * injects its media type, keeping the artist passed by the artist page.
 */
@UnstableApi
public class ArtistAllSongsFragment extends SongListPageFragment {
    @Nullable
    @Override
    protected Bundle prepareArgs(@Nullable Bundle args) {
        Bundle prepared = args == null ? new Bundle() : new Bundle(args);
        if (prepared.getParcelable(Constants.ARTIST_OBJECT) == null) {
            return null;
        }
        prepared.putString(Constants.MEDIA_ALL_BY_ARTIST, Constants.MEDIA_ALL_BY_ARTIST);
        return prepared;
    }
}
