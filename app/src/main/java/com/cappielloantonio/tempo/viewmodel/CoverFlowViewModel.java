package com.cappielloantonio.tempo.viewmodel;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.cappielloantonio.tempo.model.Cover;
import com.cappielloantonio.tempo.repository.CoverRepository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class CoverFlowViewModel extends ViewModel {

    private final CoverRepository repository;
    private final MutableLiveData<List<Cover>> coversLiveData = new MutableLiveData<>();

    public CoverFlowViewModel(@NonNull CoverRepository repository) {
        this.repository = repository;
        loadCovers();
    }

    public LiveData<List<Cover>> getCovers() {
        return coversLiveData;
    }

    private final Executor ioExecutor = Executors.newSingleThreadExecutor();

    private void loadCovers() {
        ioExecutor.execute(() -> {
            try {
                // Disabled, correct implementation not done just yet
                // List<Cover> list = repository.getCovers(); // Disabled as it ain't implemented

                /* Mock of data fecthing */
                List<String> urls = Arrays.asList(
                        "https://images.dog.ceo/breeds/affenpinscher/n02110627_11858.jpg",
                        "https://images.dog.ceo/breeds/hound-english/n02089973_811.jpg",
                        "https://images.dog.ceo/breeds/shiba/shiba-14.jpg"
                );
                List<Cover> coversList = new ArrayList<>();
                for (String url : urls) {
                    coversList.add(new Cover(url, null));   // coverArtId can be filled later if needed
                }

                // Normal logic end
                coversLiveData.postValue(coversList);
            } catch (Exception e) {
                coversLiveData.postValue(Collections.emptyList());
            }

        });
    }
}

