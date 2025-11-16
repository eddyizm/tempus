package com.cappielloantonio.tempo.repository;

import androidx.lifecycle.LiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ServerDao;
import com.cappielloantonio.tempo.model.Server;

import java.util.List;

public class ServerRepository {
    private static final String TAG = "QueueRepository";

    private final ServerDao serverDao = AppDatabase.getInstance().serverDao();

    public LiveData<List<Server>> getLiveServer() {
        return serverDao.getAll();
    }

    public void insert(Server server) {
        App.getExecutor().submit(() -> {
           serverDao.insert(server);
        });
    }

    public void delete(Server server) {
        App.getExecutor().submit(() -> {
            serverDao.delete(server);
        });
    }
}
