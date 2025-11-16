package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.DownloadDao;
import com.cappielloantonio.tempo.model.Download;

import java.util.List;

public class DownloadRepository {
    private final DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();

    public LiveData<List<Download>> getLiveDownload() {
        return downloadDao.getAll();
    }

    public List<Download> getAllDownloads() {
        return downloadDao.getAllSync();
    }

    public Download getDownload(String id) {
        return downloadDao.getOne(id);
    }

    public void insert(Download download) {
        downloadDao.insert(download);
    }

    public void update(String id) {
        App.getExecutor().submit(() -> downloadDao.update(id));
    }

    public void insertAll(List<Download> downloads) {
        downloadDao.insertAll(downloads);
    }

    public void deleteAll() {
        downloadDao.deleteAll();
    }

    public void delete(String id) {
        downloadDao.delete(id);
    }

    public void delete(List<String> ids) {
        downloadDao.deleteByIds(ids);
    }
}
