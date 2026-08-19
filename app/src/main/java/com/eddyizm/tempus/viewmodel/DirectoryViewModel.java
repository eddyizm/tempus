package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.repository.DirectoryRepository;
import com.eddyizm.tempus.subsonic.models.Directory;

public class DirectoryViewModel extends AndroidViewModel {
    private final DirectoryRepository directoryRepository;

    public DirectoryViewModel(@NonNull Application application) {
        super(application);

        directoryRepository = new DirectoryRepository();
    }

    public LiveData<Directory> loadMusicDirectory(String id) {
        return directoryRepository.getMusicDirectory(id);
    }
}
