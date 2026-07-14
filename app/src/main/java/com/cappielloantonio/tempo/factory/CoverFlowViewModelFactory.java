package com.cappielloantonio.tempo.factory;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.cappielloantonio.tempo.repository.CoverRepository;
import com.cappielloantonio.tempo.viewmodel.CoverFlowViewModel;

public class CoverFlowViewModelFactory implements ViewModelProvider.Factory {

    private final CoverRepository repository;

    public CoverFlowViewModelFactory(@NonNull CoverRepository repository) {
        this.repository = repository;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        if (modelClass.isAssignableFrom(CoverFlowViewModel.class)) {
            //noinspection unchecked
            return (T) new CoverFlowViewModel(repository);
        }
        throw new IllegalArgumentException("Unknown ViewModel class");
    }
}

