package com.cappielloantonio.tempo.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class Cover {
    private final String url;          // image to show in the flow
    private final String coverArtId;   // id used by the CustomGlideRequest (may be null)

    public Cover(@NonNull String url, @Nullable String coverArtId) {
        this.url = url;
        this.coverArtId = coverArtId;
    }

    @NonNull
    public String getUrl() {
        return url;
    }

    /** Returns the id that the CustomGlideRequest needs – can be null. */
    @Nullable
    public String getCoverArtId() {
        return coverArtId;
    }
}
