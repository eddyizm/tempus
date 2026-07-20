package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ServerDao;
import com.cappielloantonio.tempo.model.Server;
import com.cappielloantonio.tempo.util.CryptoUtil;

import java.util.List;

public class ServerRepository {
    private static final String TAG = "QueueRepository";

    private final ServerDao serverDao = AppDatabase.getInstance().serverDao();

    public LiveData<List<Server>> getLiveServer() {
        return serverDao.getAll();
    }

    public void insert(Server server) {
        InsertThreadSafe insert = new InsertThreadSafe(serverDao, withEncryptedPassword(server));
        Thread thread = new Thread(insert);
        thread.start();
    }

    /**
     * Re-encrypts any server passwords stored as plaintext before at-rest encryption was introduced.
     * Reads already tolerate plaintext via {@link CryptoUtil#decrypt}; this rewrites the stored rows
     * so the raw password no longer sits in the database. Idempotent: rows already encrypted are skipped.
     */
    public void encryptStoredPasswords() {
        new Thread(() -> {
            for (Server server : serverDao.getAllSync()) {
                String password = server.getPassword();
                if (password != null && !CryptoUtil.isEncrypted(password)) {
                    serverDao.insert(withEncryptedPassword(server));
                }
            }
        }).start();
    }

    // Single choke point: callers pass a plaintext password and it is encrypted before it reaches the
    // database. Already-encrypted values (e.g. from the migration pass) are left untouched.
    private static Server withEncryptedPassword(Server server) {
        String password = server.getPassword();
        if (password == null || CryptoUtil.isEncrypted(password)) {
            return server;
        }

        return new Server(
                server.getServerId(),
                server.getServerName(),
                server.getUsername(),
                CryptoUtil.encrypt(password),
                server.getAddress(),
                server.getLocalAddress(),
                server.getTimestamp(),
                server.isLowSecurity(),
                server.getClientCert()
        );
    }

    public void delete(Server server) {
        DeleteThreadSafe delete = new DeleteThreadSafe(serverDao, server);
        Thread thread = new Thread(delete);
        thread.start();
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
