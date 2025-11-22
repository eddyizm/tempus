package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.LyricsDao;
import com.cappielloantonio.tempo.model.LyricsCache;

import io.reactivex.rxjava3.core.Maybe;

public class LyricsRepository {
    private final LyricsDao lyricsDao = AppDatabase.getInstance().lyricsDao();

    public Maybe<LyricsCache> getLyrics(String songId) {
        return lyricsDao.getOne(songId);
    }

    public LiveData<LyricsCache> observeLyrics(String songId) {
        return lyricsDao.observeOne(songId);
    }

    public void insert(LyricsCache lyricsCache) {
        App.getExecutor().submit(() -> lyricsDao.insert(lyricsCache));
    }

    public void delete(String songId) {
        App.getExecutor().submit(() -> lyricsDao.delete(songId));
    }
}