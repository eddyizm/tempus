package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.SubsonicResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SongRepository {

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
    public MutableLiveData<List<Child>> getInstantMix(String id, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>(new ArrayList<>());

        performSmartMix(id, count, songs -> {
            List<Child> current = instantMix.getValue();
            if (current != null) {
                for (Child s : songs) {
                    if (!current.contains(s)) current.add(s);
                }
                instantMix.postValue(current);
            }
        });

        return instantMix;
    }

    /**
     * Overloaded method used by other Repositories
     */
    public void getInstantMix(String id, int count, MediaCallbackInternal callback) {
        performSmartMix(id, count, callback);
    }

    private void performSmartMix(final String id, final int count, final MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs(id, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        List<Child> songs = extractSongs(response, "similarSongs");
                        if (!songs.isEmpty()) {
                            callback.onSongsAvailable(songs);
                        }
                        if (songs.size() < count / 2) {
                            fetchContextAndSeed(id, count, callback);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        fetchContextAndSeed(id, count, callback);
                    }
                });
    }

    private void fetchContextAndSeed(final String id, final int count, final MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbum() != null) {
                            List<Child> albumSongs = response.body().getSubsonicResponse().getAlbum().getSongs();
                            if (albumSongs != null && !albumSongs.isEmpty()) {
                                callback.onSongsAvailable(new ArrayList<>(albumSongs));
                                String seedArtistId = albumSongs.get(0).getArtistId();
                                fetchSimilarByArtist(seedArtistId, count, callback);
                                return;
                            }
                        }
                        fillWithRandom(count, callback);
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        fillWithRandom(count, callback);
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
                        if (!similar.isEmpty()) {
                            callback.onSongsAvailable(similar);
                        }
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
                });
    }

    private void fillWithRandom(int target, final MediaCallbackInternal callback) {
        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(target, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        callback.onSongsAvailable(extractSongs(response, "randomSongs"));
                    }
                    @Override public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {}
                });
    }

    private List<Child> extractSongs(Response<ApiResponse> response, String type) {
        if (response.isSuccessful() && response.body() != null) {
            SubsonicResponse res = response.body().getSubsonicResponse();
            List<Child> list = null;
            if (type.equals("similarSongs") && res.getSimilarSongs() != null) {
                list = res.getSimilarSongs().getSongs();
            } else if (type.equals("similarSongs2") && res.getSimilarSongs2() != null) {
                list = res.getSimilarSongs2().getSongs();
            } else if (type.equals("randomSongs") && res.getRandomSongs() != null) {
                list = res.getRandomSongs().getSongs();
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