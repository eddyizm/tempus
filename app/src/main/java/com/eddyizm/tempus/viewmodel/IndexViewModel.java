package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.repository.DirectoryRepository;
import com.eddyizm.tempus.subsonic.models.Indexes;
import com.eddyizm.tempus.subsonic.models.MusicFolder;

public class IndexViewModel extends AndroidViewModel {
    private final DirectoryRepository directoryRepository;

    private MusicFolder musicFolder;

    public IndexViewModel(@NonNull Application application) {
        super(application);

        directoryRepository = new DirectoryRepository();
    }

    public MutableLiveData<Indexes> getIndexes(String musicFolderId) {
        return directoryRepository.getIndexes(musicFolderId, null);
    }

    public String getMusicFolderName() {
        return musicFolder != null ? musicFolder.getName() : "";
    }

    public void setMusicFolder(MusicFolder musicFolder) {
        this.musicFolder = musicFolder;
    }
}
