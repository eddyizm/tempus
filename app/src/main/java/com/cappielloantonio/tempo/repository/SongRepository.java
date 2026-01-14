package com.cappielloantonio.tempo.repository;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;
import com.cappielloantonio.tempo.util.Constants.SeedType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepository {

    private static final String TAG = "SongRepository";

    public interface MediaCallbackInternal {
        void onSongsAvailable(List<Child> songs);
    }

    public MutableLiveData<List<Child>> getStarredSongs(boolean random, int size) {
        MutableLiveData<List<Child>> starredSongs = new MutableLiveData<>(Collections.emptyList());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            if (songs != null) {
                                if (!random) {
                                    starredSongs.setValue(songs);
                                } else {
                                    Collections.shuffle(songs);
                                    starredSongs.setValue(songs.subList(0, Math.min(size, songs.size())));
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
                });

        return starredSongs;
    }

    /**
     * Used by ViewModels. Updates the LiveData list incrementally as songs are found.
     */
    public MutableLiveData<List<Child>> getInstantMix(String id, SeedType type, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(new ArrayList<>());
        Set<String> trackIds = new HashSet<>();

        performSmartMix(id, type, count, songs -> {
            List<Child> current = instantMix.getValue();
            if (current != null) {
                for (Child s : songs) {
                    if (!trackIds.contains(s.getId())) {
                        current.add(s);
                        trackIds.add(s.getId());
                    }
                }

                if (current.size() < count / 2) {
                    fetchSimilarOnly(id, count, remainder -> {
                        for (Child r : remainder) {
                            if (!trackIds.contains(r.getId())) {
                                current.add(r);
                                trackIds.add(r.getId());
                            }
                        }
                        instantMix.postValue(current);
                    });
                } else {
                    instantMix.postValue(current);
                }
            }
        });

        return instantMix;
    }

    /**
     * Overloaded method used by other Repositories
     */
    public void getInstantMix(String id, SeedType type, int count, MediaCallbackInternal callback) {
        new MediaCallbackAccumulator(callback, count).start(id, type);
    }

    private class MediaCallbackAccumulator {
        private final MediaCallbackInternal originalCallback;
        private final int targetCount;
        private final List<Child> accumulatedSongs = new ArrayList<>();
        private final Set<String> trackIds = new HashSet<>();
        private boolean isComplete = false;
        
        MediaCallbackAccumulator(MediaCallbackInternal callback, int count) {
            this.originalCallback = callback;
            this.targetCount = count;
        }
        
        void start(String id, SeedType type) {
            performSmartMix(id, type, targetCount, this::onBatchReceived);
        }
        
        private void onBatchReceived(List<Child> batch) {
            if (isComplete || batch == null || batch.isEmpty()) {
                return;
            }

            int added = 0;
            for (Child song : batch) {
                if (!trackIds.contains(song.getId()) && accumulatedSongs.size() < targetCount) {
                    trackIds.add(song.getId());
                    accumulatedSongs.add(song);
                    added++;
                }
            }
            
            if (accumulatedSongs.size() >= targetCount) {
                originalCallback.onSongsAvailable(new ArrayList<>(accumulatedSongs));
                isComplete = true;
            }
        }

    }

    private void performSmartMix(final String id, final SeedType type, final int count, final MediaCallbackInternal callback) {
        switch (type) {
            case ARTIST:
                fetchSimilarByArtist(id, count, callback);
                break;
            case ALBUM:
                fetchAlbumSongs(id, count, callback);
                break;
            case TRACK:
                fetchSingleTrackThenSimilar(id, count, callback);
                break;
        }
    }

    private void fetchAlbumSongs(String albumId, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getAlbum(albumId).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && 
                    response.body().getSubsonicResponse().getAlbum() != null) {
                    List<Child> albumSongs = response.body().getSubsonicResponse().getAlbum().getSongs();
                    if (albumSongs != null && !albumSongs.isEmpty()) {
                        int fromAlbum = Math.min(count, albumSongs.size());
                        List<Child> limitedAlbumSongs = albumSongs.subList(0, fromAlbum);
                        callback.onSongsAvailable(new ArrayList<>(limitedAlbumSongs));

                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchAlbumSongsThenSimilar.onFailure()", t);
            }
        });
    }

    private void fetchSimilarByArtist(String artistId, final int count, final MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(artistId, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> similar = extractSongs(response, "similarSongs2");
                        Log.d(TAG, "fetchSimilarByArtist.onResponse() - similar songs: " + similar.size());

                        if (!similar.isEmpty()) {
                            List<Child> limitedSimilar = similar.subList(0, Math.min(count, similar.size()));
                            callback.onSongsAvailable(limitedSimilar);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "fetchSimilarByArtist.onFailure()", t);
                    }
                });
    }

    private void fetchSingleTrackThenSimilar(String trackId, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getSong(trackId).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Child song = response.body().getSubsonicResponse().getSong();
                    if (song != null) {
                        callback.onSongsAvailable(Collections.singletonList(song));
                    }
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchSingleTrackThenSimilar.onFailure()", t);
            }
        });
    }

    private void fetchSimilarOnly(String id, int count, MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false).getBrowsingClient().getSimilarSongs(id, count).enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = extractSongs(response, "similarSongs");
                if (!songs.isEmpty()) {
                    int limit = Math.min(count, songs.size());
                    callback.onSongsAvailable(songs.subList(0, limit));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                Log.e(TAG, "fetchSimilarOnly.onFailure()", t);
            }
        });
    }


    public MutableLiveData<List<Child>> getContinuousMix(String id, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs(id, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs() != null) {
                            instantMix.setValue(response.body().getSubsonicResponse().getSimilarSongs().getSongs());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        instantMix.setValue(null);
                    }
                });

        return instantMix;
    }

    private List<Child> extractSongs(Response<ApiResponse> response, String type) {
        if (response.isSuccessful() && response.body() != null) {
            SubsonicResponse res = response.body().getSubsonicResponse();
            List<Child> list = null;
            if (type.equals("similarSongs") && res.getSimilarSongs() != null) {
                list = res.getSimilarSongs().getSongs();
            } else if (type.equals("similarSongs2") && res.getSimilarSongs2() != null) {
                list = res.getSimilarSongs2().getSongs();
            }
            return (list != null) ? list : new ArrayList<>();
        }

        return new ArrayList<>();
    }

    public MutableLiveData<List<Child>> getRandomSample(int number, Integer fromYear, Integer toYear) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(number, fromYear, toYear).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null) {
                    songs.addAll(Objects.requireNonNull(response.body().getSubsonicResponse().getRandomSongs().getSongs()));
                }
                randomSongsSample.setValue(songs);
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return randomSongsSample;
    }

    public MutableLiveData<List<Child>> getRandomSampleWithGenre(int number, Integer fromYear, Integer toYear, String genre) {
        MutableLiveData<List<Child>> randomSongsSample = new MutableLiveData<>();

        App.getSubsonicClientInstance(false).getAlbumSongListClient().getRandomSongs(number, fromYear, toYear, genre).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                List<Child> songs = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null) {
                    songs.addAll(Objects.requireNonNull(response.body().getSubsonicResponse().getRandomSongs().getSongs()));
                }
                randomSongsSample.setValue(songs);
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return randomSongsSample;
    }

    public void scrobble(String id, boolean submission) {
        App.getSubsonicClientInstance(false).getMediaAnnotationClient().scrobble(id, submission).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {}
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
    }

    public void setRating(String id, int rating) {
        App.getSubsonicClientInstance(false).getMediaAnnotationClient().setRating(id, rating).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {}
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
    }

    public MutableLiveData<List<Child>> getSongsByGenre(String id, int page) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(id, 100, 100 * page).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                    songsByGenre.setValue(response.body().getSubsonicResponse().getSongsByGenre().getSongs());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return songsByGenre;
    }

    public MutableLiveData<List<Child>> getSongsByGenres(ArrayList<String> genresId) {
        MutableLiveData<List<Child>> songsByGenre = new MutableLiveData<>();
        for (String id : genresId) {
            App.getSubsonicClientInstance(false).getAlbumSongListClient().getSongsByGenre(id, 500, 0).enqueue(new Callback<ApiResponse>() {
                @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                    List<Child> songs = new ArrayList<>();
                    if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSongsByGenre() != null) {
                        songs.addAll(Objects.requireNonNull(response.body().getSubsonicResponse().getSongsByGenre().getSongs()));
                    }
                    songsByGenre.setValue(songs);
                }
                @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
            });
        }
        return songsByGenre;
    }

    public MutableLiveData<Child> getSong(String id) {
        MutableLiveData<Child> song = new MutableLiveData<>();
        App.getSubsonicClientInstance(false).getBrowsingClient().getSong(id).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    song.setValue(response.body().getSubsonicResponse().getSong());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return song;
    }

    public MutableLiveData<String> getSongLyrics(Child song) {
        MutableLiveData<String> lyrics = new MutableLiveData<>(null);
        App.getSubsonicClientInstance(false).getMediaRetrievalClient().getLyrics(song.getArtist(), song.getTitle()).enqueue(new Callback<ApiResponse>() {
            @Override public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getLyrics() != null) {
                    lyrics.setValue(response.body().getSubsonicResponse().getLyrics().getValue());
                }
            }
            @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
        });
        return lyrics;
    }
}