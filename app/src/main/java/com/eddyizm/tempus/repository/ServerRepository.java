package com.eddyizm.tempus.repository;

import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.App;

import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.ServerDao;
import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.util.Preferences;

import java.util.List;

public class ServerRepository {
    private static final String TAG = "QueueRepository";

    private final ServerDao serverDao = AppDatabase.getInstance().serverDao();

    public LiveData<List<Server>> getLiveServer() {
        return serverDao.getAll();
    }

    public void insert(Server server) {
        InsertThreadSafe insert = new InsertThreadSafe(serverDao, server);
        Thread thread = new Thread(insert);
        thread.start();
    }

    public void update(Server server) {
        UpdateThreadSafe update = new UpdateThreadSafe(serverDao, server);
        Thread thread = new Thread(update);
        thread.start();
    }

    public void delete(Server server) {
        DeleteThreadSafe delete = new DeleteThreadSafe(serverDao, server);
        Thread thread = new Thread(delete);
        thread.start();
    }

    /**
     * Applies an edit of the signed-in server's custom headers to the running session, so a
     * rotated token takes effect without signing in again. Glide, ExoPlayer and downloads read the
     * preference on every request; the API client is rebuilt to pick it up.
     */
    static void syncSignedInServer(Server server) {
        String signedInId = Preferences.getServerId();
        if (signedInId == null || !signedInId.equals(server.getServerId())) return;
        Preferences.setCustomHeaders(server.getCustomHeaders());
        new Handler(Looper.getMainLooper()).post(App::refreshSubsonicClient);
    }

    private static class InsertThreadSafe implements Runnable {
        private final ServerDao serverDao;
        private final Server server;

        public InsertThreadSafe(ServerDao serverDao, Server server) {
            this.serverDao = serverDao;
            this.server = server;
        }

        @Override
        public void run() {
            serverDao.insert(server);
            syncSignedInServer(server);
        }
    }

    public static class UpdateThreadSafe implements Runnable {
        private final ServerDao serverDao;
        private final Server server;

        public UpdateThreadSafe(ServerDao serverDao, Server server) {
            this.serverDao = serverDao;
            this.server = server;
        }

        @Override
        public void run() {
            serverDao.update(server);
            syncSignedInServer(server);
        }
    }

    private static class DeleteThreadSafe implements Runnable {
        private final ServerDao serverDao;
        private final Server server;

        public DeleteThreadSafe(ServerDao serverDao, Server server) {
            this.serverDao = serverDao;
            this.server = server;
        }

        @Override
        public void run() {
            serverDao.delete(server);
        }
    }
}
