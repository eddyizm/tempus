package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.eddyizm.tempus.github.models.LatestRelease;
import com.eddyizm.tempus.repository.QueueRepository;
import com.eddyizm.tempus.repository.SystemRepository;
import com.eddyizm.tempus.subsonic.models.OpenSubsonicExtension;
import com.eddyizm.tempus.subsonic.models.SubsonicResponse;

import java.util.List;

public class MainViewModel extends AndroidViewModel {
    private static final String TAG = "SearchViewModel";

    private final SystemRepository systemRepository;

    public MainViewModel(@NonNull Application application) {
        super(application);

        systemRepository = new SystemRepository();
    }

    public boolean isQueueLoaded() {
        QueueRepository queueRepository = new QueueRepository();
        return queueRepository.count() != 0;
    }

    public LiveData<SubsonicResponse> ping() {
        return systemRepository.ping();
    }

    public LiveData<List<OpenSubsonicExtension>> getOpenSubsonicExtensions() {
        return systemRepository.getOpenSubsonicExtensions();
    }

    public LiveData<LatestRelease> checkTempoUpdate() {
        return systemRepository.checkTempoUpdate();
    }
}
