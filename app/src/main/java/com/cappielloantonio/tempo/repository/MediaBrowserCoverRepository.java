package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaBrowser;

import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.model.Cover;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List; /** Example implementation that extracts data from a MediaBrowser */
public class MediaBrowserCoverRepository implements CoverRepository {

    private final ListenableFuture<MediaBrowser> mediaBrowserFuture;

    public MediaBrowserCoverRepository(@NonNull ListenableFuture<MediaBrowser> mediaBrowserFuture) {
        this.mediaBrowserFuture = mediaBrowserFuture;
    }

    @Override
    @WorkerThread
    public List<Cover> getCovers() throws Exception {
        MediaBrowser mediaBrowser = mediaBrowserFuture.get();   // blocks only inside a background thread
        MediaMetadata metadata = mediaBrowser.getMediaMetadata();

        // Inject this here, somehow, since it grabs the covertArtId
        /*`
        CustomGlideRequest.Builder
                .from(requireContext(), metadata.extras.getString("coverArtId"), CustomGlideRequest.ResourceType.Song)
                .build()
                .into(bind.playerHeaderLayout.playerHeaderMediaCoverImage);

        */
        // -----------------------------------------------------------------
        // Replace the below with the real extraction logic from metadata.
        // For demonstration we just return the three dog‑image URLs.
        // -----------------------------------------------------------------
        List<String> urls = Arrays.asList(
                "https://images.dog.ceo/breeds/affenpinscher/n02110627_11858.jpg",
                "https://images.dog.ceo/breeds/hound-english/n02089973_811.jpg",
                "https://images.dog.ceo/breeds/shiba/shiba-14.jpg"
        );

        List<Cover> covers = new ArrayList<>();
        for (String url : urls) {
            covers.add(new Cover(url, null));   // coverArtId can be filled later if needed
        }
        return covers;
    }
}
