package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.media3.common.MediaMetadata;
import androidx.media3.session.MediaBrowser;

import com.cappielloantonio.tempo.model.Cover;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public interface CoverRepository {
    /** Returns a list of covers. Call should be made off the UI thread. */
    @WorkerThread
    List<Cover> getCovers() throws Exception;
}

