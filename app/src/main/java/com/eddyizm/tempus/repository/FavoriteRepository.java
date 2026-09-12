package com.eddyizm.tempus.repository;

import androidx.annotation.NonNull;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.database.AppDatabase;
import com.eddyizm.tempus.database.dao.FavoriteDao;
import com.eddyizm.tempus.interfaces.StarCallback;
import com.eddyizm.tempus.model.Favorite;
import com.eddyizm.tempus.subsonic.base.ApiResponse;
import com.eddyizm.tempus.util.FavoriteRegistry;
import com.eddyizm.tempus.subsonic.models.ResponseStatus;

import java.util.ArrayList;
import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FavoriteRepository {
    private final FavoriteDao favoriteDao = AppDatabase.getInstance().favoriteDao();

    // Exactly one of the three ids is set on any call, so the caller's intent is recorded against
    // whichever one that is, under the kind that id belongs to.
    private static FavoriteRegistry.Record remember(String id, String albumId, String artistId, boolean isStarred) {
        FavoriteRegistry.Record song = FavoriteRegistry.set(FavoriteRegistry.Kind.SONG, id, isStarred);
        FavoriteRegistry.Record album = FavoriteRegistry.set(FavoriteRegistry.Kind.ALBUM, albumId, isStarred);
        FavoriteRegistry.Record artist = FavoriteRegistry.set(FavoriteRegistry.Kind.ARTIST, artistId, isStarred);
        return song != null ? song : album != null ? album : artist;
    }

    // A non 2xx lands here too, since a proxy's 401 or 429 is answered by trying again later, not by
    // dropping the decision.
    private static void retryOrWithdraw(FavoriteRegistry.Record record, StarCallback starCallback) {
        if (FavoriteRegistry.isCurrent(record)) starCallback.onError();
        else FavoriteRegistry.withdraw(record);
    }

    // Subsonic reports a refusal as a 200 whose body says failed, so the status line alone cannot
    // tell a stored star from a rejected one.
    private static boolean serverRefused(Response<ApiResponse> response) {
        return response.body() != null
                && response.body().getSubsonicResponse() != null
                && ResponseStatus.FAILED.equals(response.body().getSubsonicResponse().getStatus());
    }

    // Timestamps are the queue table's key, so two rows in one millisecond would silently drop the
    // second.
    private static long lastQueuedAt;

    public void star(String id, String albumId, String artistId, StarCallback starCallback) {
        FavoriteRegistry.Record record = remember(id, albumId, artistId, true);

        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .star(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && !serverRefused(response)) {
                            FavoriteRegistry.accept(record);
                            starCallback.onSuccess();
                        } else if (serverRefused(response)) {
                            FavoriteRegistry.strike(record);
                            starCallback.onRefused();
                        } else {
                            retryOrWithdraw(record, starCallback);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        retryOrWithdraw(record, starCallback);
                    }
                });
    }

    public void unstar(String id, String albumId, String artistId, StarCallback starCallback) {
        FavoriteRegistry.Record record = remember(id, albumId, artistId, false);

        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .unstar(id, albumId, artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && !serverRefused(response)) {
                            FavoriteRegistry.accept(record);
                            starCallback.onSuccess();
                        } else if (serverRefused(response)) {
                            FavoriteRegistry.strike(record);
                            starCallback.onRefused();
                        } else {
                            retryOrWithdraw(record, starCallback);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        retryOrWithdraw(record, starCallback);
                    }
                });
    }

    public List<Favorite> getFavorites() {
        List<Favorite> favorites = new ArrayList<>();

        GetAllThreadSafe getAllThreadSafe = new GetAllThreadSafe(favoriteDao);
        Thread thread = new Thread(getAllThreadSafe);
        thread.start();

        try {
            thread.join();
            favorites = getAllThreadSafe.getFavorites();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return favorites;
    }

    private static class GetAllThreadSafe implements Runnable {
        private final FavoriteDao favoriteDao;
        private List<Favorite> favorites = new ArrayList<>();

        public GetAllThreadSafe(FavoriteDao favoriteDao) {
            this.favoriteDao = favoriteDao;
        }

        @Override
        public void run() {
            favorites = favoriteDao.getAll();
        }

        public List<Favorite> getFavorites() {
            return favorites;
        }
    }

    public void starLater(String id, String albumId, String artistId, boolean toStar) {
        FavoriteRegistry.supersede(FavoriteRegistry.Kind.SONG, id, toStar);
        FavoriteRegistry.supersede(FavoriteRegistry.Kind.ALBUM, albumId, toStar);
        FavoriteRegistry.supersede(FavoriteRegistry.Kind.ARTIST, artistId, toStar);
        remember(id, albumId, artistId, toStar);
        lastQueuedAt = Math.max(System.currentTimeMillis(), lastQueuedAt + 1);

        InsertThreadSafe insert = new InsertThreadSafe(favoriteDao, new Favorite(lastQueuedAt, id, albumId, artistId, toStar));
        Thread thread = new Thread(insert);
        thread.start();
    }

    private static class InsertThreadSafe implements Runnable {
        private final FavoriteDao favoriteDao;
        private final Favorite favorite;

        public InsertThreadSafe(FavoriteDao favoriteDao, Favorite favorite) {
            this.favoriteDao = favoriteDao;
            this.favorite = favorite;
        }

        @Override
        public void run() {
            favoriteDao.insert(favorite);
        }
    }

    public void delete(Favorite favorite) {
        DeleteThreadSafe delete = new DeleteThreadSafe(favoriteDao, favorite);
        Thread thread = new Thread(delete);
        thread.start();
    }

    private static class DeleteThreadSafe implements Runnable {
        private final FavoriteDao favoriteDao;
        private final Favorite favorite;

        public DeleteThreadSafe(FavoriteDao favoriteDao, Favorite favorite) {
            this.favoriteDao = favoriteDao;
            this.favorite = favorite;
        }

        @Override
        public void run() {
            favoriteDao.delete(favorite);
        }
    }
}
