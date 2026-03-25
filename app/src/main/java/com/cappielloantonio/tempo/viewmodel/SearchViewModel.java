package com.cappielloantonio.tempo.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.media3.common.util.UnstableApi;

import com.cappielloantonio.tempo.model.RecentSearch;
import com.cappielloantonio.tempo.repository.SearchingRepository;
import com.cappielloantonio.tempo.subsonic.models.SearchResult2;
import com.cappielloantonio.tempo.subsonic.models.SearchResult3;
import com.cappielloantonio.tempo.ui.fragment.SearchFragment;

import java.util.ArrayList;
import java.util.List;

public class SearchViewModel extends AndroidViewModel {
    private static final String TAG = "SearchViewModel";

    private String query = "";

    private final SearchingRepository searchingRepository;

    public SearchViewModel(@NonNull Application application) {
        super(application);

        searchingRepository = new SearchingRepository();
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;

        if (!query.isEmpty()) {
            insertNewSearch(query);
        }
    }

    public LiveData<SearchResult2> search2(String title) {
        return searchingRepository.search2(title);
    }

    @UnstableApi
    public LiveData<SearchResult3> search3(SearchFragment sf, String title) {
        return searchingRepository.search3(sf, title);
    }

    public void insertNewSearch(String search) {
        searchingRepository.insert(new RecentSearch(search, System.currentTimeMillis() / 1000L));
    }

    public void deleteRecentSearch(String search) {
        searchingRepository.delete(new RecentSearch(search, 0));
    }

    public LiveData<List<String>> getSearchSuggestion(String query) {
        return searchingRepository.getSuggestions(query);
    }

    public List<String> getRecentSearchSuggestion() {
        ArrayList<String> suggestions = new ArrayList<>();
        suggestions.addAll(searchingRepository.getRecentSearchSuggestion());

        return suggestions;
    }
}
