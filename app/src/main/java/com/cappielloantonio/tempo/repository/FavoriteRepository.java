package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.FavoriteDao;
import com.cappielloantonio.tempo.interfaces.StarCallback;
import com.cappielloantonio.tempo.model.Favorite;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;

import java.util.List;

import io.reactivex.rxjava3.core.Maybe;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteRepository {
    private final FavoriteDao favoriteDao = AppDatabase.getInstance().favoriteDao();

    public void star(String id, String albumId, String artistId, StarCallback starCallback) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .star(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            starCallback.onSuccess();
                        } else {
                            starCallback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        starCallback.onError();
                    }
                });
    }

    public void unstar(String id, String albumId, String artistId, StarCallback starCallback) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .unstar(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()) {
                            starCallback.onSuccess();
                        } else {
                            starCallback.onError();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        starCallback.onError();
                    }
                });
    }

    public Maybe<List<Favorite>> getFavorites() {
        return favoriteDao.getAll();
    }

    public void starLater(String id, String albumId, String artistId, boolean toStar) {
        App.getExecutor().submit(() -> favoriteDao.insert(new Favorite(System.currentTimeMillis(), id, albumId, artistId, toStar)));
    }

    public void delete(Favorite favorite) {
        App.getExecutor().submit(() -> favoriteDao.delete(favorite));
    }
}
