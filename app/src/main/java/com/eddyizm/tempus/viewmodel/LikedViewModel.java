package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.eddyizm.tempus.repository.DownloadRepository;
import com.eddyizm.tempus.repository.SongRepository;
import com.eddyizm.tempus.subsonic.models.Child;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LikedViewModel extends AndroidViewModel {
    private final SongRepository songRepository;
    private final DownloadRepository downloadRepository;

    private final MediatorLiveData<List<Child>> likedTracks = new MediatorLiveData<>();

    private List<Child> starredFromServer = Collections.emptyList();
    private List<Child> starredOnPhone = Collections.emptyList();

    public LikedViewModel(@NonNull Application application) {
        super(application);

        songRepository = new SongRepository();
        downloadRepository = new DownloadRepository();

        likedTracks.addSource(songRepository.getStarredSongs(false, -1), server -> {
            starredFromServer = server == null ? Collections.<Child>emptyList() : server;
            likedTracks.setValue(merge());
        });
        likedTracks.addSource(downloadRepository.getLiveLiked(), phone -> {
            starredOnPhone = phone == null
                    ? Collections.<Child>emptyList()
                    : phone.stream().map(child -> (Child) child).collect(Collectors.toList());
            likedTracks.setValue(merge());
        });
    }

    public LiveData<List<Child>> getLikedTracks() {
        return likedTracks;
    }

    // The server list says what is liked, and the rows on the phone fill in the tracks the server
    // has not answered for, so the screen still holds something without connectivity. The newest
    // like comes first, which is why the list is ordered here and not by either source alone.
    private List<Child> merge() {
        Map<String, Child> merged = new LinkedHashMap<>();

        for (Child song : starredFromServer) {
            if (song != null && song.getId() != null) merged.put(song.getId(), song);
        }
        for (Child song : starredOnPhone) {
            if (song != null && song.getId() != null) merged.putIfAbsent(song.getId(), song);
        }

        List<Child> result = new ArrayList<>(merged.values());
        result.sort(Comparator.comparing(Child::getStarred, Comparator.nullsLast(Comparator.<Date>reverseOrder())));
        return result;
    }
}
