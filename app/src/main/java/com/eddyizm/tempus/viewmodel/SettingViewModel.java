package com.eddyizm.tempus.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.eddyizm.tempus.interfaces.ScanCallback;
import com.eddyizm.tempus.repository.DirectoryRepository;
import com.eddyizm.tempus.repository.ScanRepository;
import com.eddyizm.tempus.subsonic.models.MusicFolder;

import java.util.List;

public class SettingViewModel extends AndroidViewModel {
    private static final String TAG = "SettingViewModel";

    private final ScanRepository scanRepository;
    private final DirectoryRepository directoryRepository;

    private final MutableLiveData<List<MusicFolder>> musicFolders = new MutableLiveData<>(null);

    public SettingViewModel(@NonNull Application application) {
        super(application);

        scanRepository = new ScanRepository();
        directoryRepository = new DirectoryRepository();
    }

    public LiveData<List<MusicFolder>> getMusicFolders(LifecycleOwner owner) {
        if (musicFolders.getValue() == null) {
            directoryRepository.getMusicFolders().observe(owner, musicFolders::postValue);
        }

        return musicFolders;
    }

    public void launchScan(ScanCallback callback) {
        scanRepository.startScan(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                callback.onSuccess(isScanning, count);
            }
        });
    }

    public void getScanStatus(ScanCallback callback) {
        scanRepository.getScanStatus(new ScanCallback() {
            @Override
            public void onError(Exception exception) {
                callback.onError(exception);
            }

            @Override
            public void onSuccess(boolean isScanning, long count) {
                callback.onSuccess(isScanning, count);
            }
        });
    }
}
