package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.documentfile.provider.DocumentFile;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.DownloadStack;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.util.ExternalAudioReader;
import com.cappielloantonio.tempo.util.Preferences;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class DownloadViewModel extends AndroidViewModel {
    private static final String TAG = "DownloadViewModel";

    private final DownloadRepository downloadRepository;

    private final MutableLiveData<List<Child>> downloadedTrackSample = new MutableLiveData<>(null);
    private final MutableLiveData<ArrayList<DownloadStack>> viewStack = new MutableLiveData<>(null);
    private final MutableLiveData<Integer> refreshResult = new MutableLiveData<>();

    public DownloadViewModel(@NonNull Application application) {
        super(application);

        downloadRepository = new DownloadRepository();

        initViewStack(new DownloadStack(Preferences.getDefaultDownloadViewType(), null));
    }

    public LiveData<List<Child>> getDownloadedTracks(LifecycleOwner owner) {
        downloadRepository.getLiveDownload().observe(owner, downloads -> downloadedTrackSample.postValue(downloads.stream().map(download -> (Child) download).collect(Collectors.toList())));
        return downloadedTrackSample;
    }

    public LiveData<ArrayList<DownloadStack>> getViewStack() {
        return viewStack;
    }

    public LiveData<Integer> getRefreshResult() {
        return refreshResult;
    }

    public void initViewStack(DownloadStack level) {
        ArrayList<DownloadStack> stack = new ArrayList<>();
        stack.add(level);
        viewStack.setValue(stack);
    }

    public void pushViewStack(DownloadStack level) {
        ArrayList<DownloadStack> stack = viewStack.getValue();
        stack.add(level);
        viewStack.setValue(stack);
    }

    public void popViewStack() {
        ArrayList<DownloadStack> stack = viewStack.getValue();
        stack.remove(stack.size() - 1);
        viewStack.setValue(stack);
    }

    public void refreshExternalDownloads() {
        App.getExecutor().submit(() -> {
            String directoryUri = Preferences.getDownloadDirectoryUri();
            if (directoryUri == null) {
                refreshResult.postValue(-1);
                return;
            }

            List<Download> downloads = downloadRepository.getAllDownloads();
            if (downloads == null || downloads.isEmpty()) {
                refreshResult.postValue(0);
                return;
            }

            ArrayList<Download> toRemove = new ArrayList<>();

            for (Download download : downloads) {
                String uriString = download.getDownloadUri();
                if (uriString == null || uriString.isEmpty()) {
                    continue;
                }

                Uri uri = Uri.parse(uriString);
                if (uri.getScheme() == null || !uri.getScheme().equalsIgnoreCase("content")) {
                    continue;
                }

                DocumentFile file;
                try {
                    file = DocumentFile.fromSingleUri(getApplication(), uri);
                } catch (SecurityException exception) {
                    file = null;
                }

                if (file == null || !file.exists()) {
                    toRemove.add(download);
                }
            }

            if (!toRemove.isEmpty()) {
                ArrayList<String> ids = new ArrayList<>();
                for (Download download : toRemove) {
                    ids.add(download.getId());
                    ExternalAudioReader.removeMetadata(download);
                }

                downloadRepository.delete(ids);
                ExternalAudioReader.refreshCache();
                refreshResult.postValue(ids.size());
            } else {
                refreshResult.postValue(0);
            }
        });
    }
}
