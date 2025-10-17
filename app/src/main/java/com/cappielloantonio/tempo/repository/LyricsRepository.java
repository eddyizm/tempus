package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.LyricsDao;
import com.cappielloantonio.tempo.model.LyricsCache;

public class LyricsRepository {
    private final LyricsDao lyricsDao = AppDatabase.getInstance().lyricsDao();

    public LyricsCache getLyrics(String songId) {
        GetLyricsThreadSafe getLyricsThreadSafe = new GetLyricsThreadSafe(lyricsDao, songId);
        Thread thread = new Thread(getLyricsThreadSafe);
        thread.start();

        try {
            thread.join();
            return getLyricsThreadSafe.getLyrics();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    public LiveData<LyricsCache> observeLyrics(String songId) {
        return lyricsDao.observeOne(songId);
    }

    public void insert(LyricsCache lyricsCache) {
        InsertThreadSafe insert = new InsertThreadSafe(lyricsDao, lyricsCache);
        Thread thread = new Thread(insert);
        thread.start();
    }

    public void delete(String songId) {
        DeleteThreadSafe delete = new DeleteThreadSafe(lyricsDao, songId);
        Thread thread = new Thread(delete);
        thread.start();
    }

    private static class GetLyricsThreadSafe implements Runnable {
        private final LyricsDao lyricsDao;
        private final String songId;
        private LyricsCache lyricsCache;

        public GetLyricsThreadSafe(LyricsDao lyricsDao, String songId) {
            this.lyricsDao = lyricsDao;
            this.songId = songId;
        }

        @Override
        public void run() {
            lyricsCache = lyricsDao.getOne(songId);
        }

        public LyricsCache getLyrics() {
            return lyricsCache;
        }
    }

    private static class InsertThreadSafe implements Runnable {
        private final LyricsDao lyricsDao;
        private final LyricsCache lyricsCache;

        public InsertThreadSafe(LyricsDao lyricsDao, LyricsCache lyricsCache) {
            this.lyricsDao = lyricsDao;
            this.lyricsCache = lyricsCache;
        }

        @Override
        public void run() {
            lyricsDao.insert(lyricsCache);
        }
    }

    private static class DeleteThreadSafe implements Runnable {
        private final LyricsDao lyricsDao;
        private final String songId;

        public DeleteThreadSafe(LyricsDao lyricsDao, String songId) {
            this.lyricsDao = lyricsDao;
            this.songId = songId;
        }

        @Override
        public void run() {
            lyricsDao.delete(songId);
        }
    }
}