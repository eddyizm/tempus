package com.eddyizm.tempus.repository;

import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.DownloadDao;
import com.eddyizm.tempus.database.dao.FavoriteDao;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.model.Favorite;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadRepository {
    private static final ExecutorService DOWNLOAD_WRITE_EXECUTOR = Executors.newSingleThreadExecutor();

    private final DownloadDao downloadDao = AppDatabase.getInstance().downloadDao();

    public LiveData<List<Download>> getLiveDownload() {
        return downloadDao.getAll();
    }

    public List<Download> getAllDownloads() {
        GetAllDownloadsThreadSafe getDownloads = new GetAllDownloadsThreadSafe(downloadDao);
        Thread thread = new Thread(getDownloads);
        thread.start();

        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return getDownloads.getDownloads();
    }

    public Download getDownload(String id) {
        Download download = null;

        GetDownloadThreadSafe getDownloadThreadSafe = new GetDownloadThreadSafe(downloadDao, id);
        Thread thread = new Thread(getDownloadThreadSafe);
        thread.start();

        try {
            thread.join();
            download = getDownloadThreadSafe.getDownload();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return download;
    }

    public List<Download> getFailedDownloads() {
        GetFailedDownloadsThreadSafe getDownloads = new GetFailedDownloadsThreadSafe(downloadDao);
        Thread thread = new Thread(getDownloads);
        thread.start();

        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return getDownloads.getDownloads();
    }

    private static class GetAllDownloadsThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private List<Download> downloads;

        public GetAllDownloadsThreadSafe(DownloadDao downloadDao) {
            this.downloadDao = downloadDao;
        }

        @Override
        public void run() {
            downloads = downloadDao.getAllSync();
        }

        public List<Download> getDownloads() {
            return downloads;
        }
    }

    private static class GetDownloadThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final String id;
        private Download download;

        public GetDownloadThreadSafe(DownloadDao downloadDao, String id) {
            this.downloadDao = downloadDao;
            this.id = id;
        }

        @Override
        public void run() {
            download = downloadDao.getOne(id);
        }

        public Download getDownload() {
            return download;
        }
    }

    private static class GetFailedDownloadsThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private List<Download> downloads;

        public GetFailedDownloadsThreadSafe(DownloadDao downloadDao) {
            this.downloadDao = downloadDao;
        }

        @Override
        public void run() {
            downloads = downloadDao.getFailedSync();
        }

        public List<Download> getDownloads() {
            return downloads;
        }
    }

    public void insert(Download download) {
        InsertThreadSafe insert = new InsertThreadSafe(downloadDao, download);
        DOWNLOAD_WRITE_EXECUTOR.execute(insert);
    }

    private static class InsertThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final Download download;

        public InsertThreadSafe(DownloadDao downloadDao, Download download) {
            this.downloadDao = downloadDao;
            this.download = download;
        }

        @Override
        public void run() {
            downloadDao.insert(download);
        }
    }

    public void update(String id) {
        UpdateThreadSafe update = new UpdateThreadSafe(downloadDao, id);
        DOWNLOAD_WRITE_EXECUTOR.execute(update);
    }

    private static class UpdateThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final String id;

        public UpdateThreadSafe(DownloadDao downloadDao, String id) {
            this.downloadDao = downloadDao;
            this.id = id;
        }

        @Override
        public void run() {
            downloadDao.update(id);
        }
    }

    public void insertAll(List<Download> downloads) {
        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(downloadDao, downloads);
        DOWNLOAD_WRITE_EXECUTOR.execute(insertAll);
    }

    private static class InsertAllThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final List<Download> downloads;

        public InsertAllThreadSafe(DownloadDao downloadDao, List<Download> downloads) {
            this.downloadDao = downloadDao;
            this.downloads = downloads;
        }

        @Override
        public void run() {
            downloadDao.insertAll(downloads);
        }
    }

    public void deleteAll() {
        DeleteAllThreadSafe deleteAll = new DeleteAllThreadSafe(downloadDao);
        DOWNLOAD_WRITE_EXECUTOR.execute(deleteAll);
    }

    private static class DeleteAllThreadSafe implements Runnable {
        private final DownloadDao downloadDao;

        public DeleteAllThreadSafe(DownloadDao downloadDao) {
            this.downloadDao = downloadDao;
        }

        @Override
        public void run() {
            downloadDao.deleteAll();
        }
    }

    public void delete(String id) {
        DeleteThreadSafe delete = new DeleteThreadSafe(downloadDao, id);
        DOWNLOAD_WRITE_EXECUTOR.execute(delete);
    }

    public void deleteFailed(String id) {
        DeleteFailedThreadSafe delete = new DeleteFailedThreadSafe(downloadDao, id);
        DOWNLOAD_WRITE_EXECUTOR.execute(delete);
    }

    public void delete(List<String> ids) {
        DeleteMultipleThreadSafe delete = new DeleteMultipleThreadSafe(downloadDao, ids);
        DOWNLOAD_WRITE_EXECUTOR.execute(delete);
    }

    private static class DeleteThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final String id;

        public DeleteThreadSafe(DownloadDao downloadDao, String id) {
            this.downloadDao = downloadDao;
            this.id = id;
        }

        @Override
        public void run() {
            downloadDao.delete(id);
        }
    }

    private static class DeleteFailedThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final String id;

        public DeleteFailedThreadSafe(DownloadDao downloadDao, String id) {
            this.downloadDao = downloadDao;
            this.id = id;
        }

        @Override
        public void run() {
            downloadDao.deleteFailed(id);
        }
    }

    private static class DeleteMultipleThreadSafe implements Runnable {
        private final DownloadDao downloadDao;
        private final List<String> ids;

        public DeleteMultipleThreadSafe(DownloadDao downloadDao, List<String> ids) {
            this.downloadDao = downloadDao;
            this.ids = ids;
        }

        @Override
        public void run() {
            downloadDao.deleteByIds(ids);
        }
    }
}
