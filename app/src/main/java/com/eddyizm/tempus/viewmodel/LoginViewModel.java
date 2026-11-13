package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.repository.ServerRepository;

import java.util.List;

public class LoginViewModel extends AndroidViewModel {
    private final ServerRepository serverRepository;

    private Server toEdit = null;

    private final MutableLiveData<Boolean> loginSucceeded = new MutableLiveData<>(false);

    public LoginViewModel(@NonNull Application application) {
        super(application);

        serverRepository = new ServerRepository();
    }

    public LiveData<List<Server>> getServerList() {
        return serverRepository.getLiveServer();
    }

    public void addServer(Server server) {
        serverRepository.insert(server);
    }

    public void deleteServer(Server server) {
        if (server != null) {
            serverRepository.delete(server);
        } else if (toEdit != null) {
            serverRepository.delete(toEdit);
        }
    }

    public void setServerToEdit(Server server) {
        toEdit = server;
    }

    public Server getServerToEdit() {
        return toEdit;
    }

    public LiveData<Boolean> getLoginSucceeded() {
        return loginSucceeded;
    }

    public void setLoginSucceeded(boolean succeeded) {
        loginSucceeded.setValue(succeeded);
    }
}
