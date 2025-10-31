package com.cappielloantonio.tempo.repository;

import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import android.util.Log;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.ArtistInfo2;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.IndexID3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ArtistRepository {
    private final AlbumRepository albumRepository;

    public ArtistRepository() {
        this.albumRepository = new AlbumRepository();
    }

    public void getArtistAllSongs(String artistId, ArtistSongsCallback callback) {
        Log.d("ArtistSync", "Getting albums for artist: " + artistId);

        // Get the artist info first, which contains the albums
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && 
                            response.body().getSubsonicResponse().getArtist() != null && 
                            response.body().getSubsonicResponse().getArtist().getAlbums() != null) {
                            
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();
                            Log.d("ArtistSync", "Got albums directly: " + albums.size());
                            
                            if (!albums.isEmpty()) {
                                fetchAllAlbumSongsWithCallback(albums, callback);
                            } else {
                                Log.d("ArtistSync", "No albums found in artist response");
                                callback.onSongsCollected(new ArrayList<>());
                            }
                        } else {
                            Log.d("ArtistSync", "Failed to get artist info");
                            callback.onSongsCollected(new ArrayList<>());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.d("ArtistSync", "Error getting artist info: " + t.getMessage());
                        callback.onSongsCollected(new ArrayList<>());
                    }
                });
    }

    private void fetchAllAlbumSongsWithCallback(List<AlbumID3> albums, ArtistSongsCallback callback) {
        if (albums == null || albums.isEmpty()) {
            Log.d("ArtistSync", "No albums to process");
            callback.onSongsCollected(new ArrayList<>());
            return;
        }

        List<Child> allSongs = new ArrayList<>();
        AtomicInteger remainingAlbums = new AtomicInteger(albums.size());
        Log.d("ArtistSync", "Processing " + albums.size() + " albums");
        
        for (AlbumID3 album : albums) {
            Log.d("ArtistSync", "Getting tracks for album: " + album.getName());
            MutableLiveData<List<Child>> albumTracks = albumRepository.getAlbumTracks(album.getId());
            albumTracks.observeForever(songs -> {
                Log.d("ArtistSync", "Got " + (songs != null ? songs.size() : 0) + " songs from album");
                if (songs != null) {
                    allSongs.addAll(songs);
                }
                albumTracks.removeObservers(null);
                
                int remaining = remainingAlbums.decrementAndGet();
                Log.d("ArtistSync", "Remaining albums: " + remaining);
                
                if (remaining == 0) {
                    Log.d("ArtistSync", "All albums processed. Total songs: " + allSongs.size());
                    callback.onSongsCollected(allSongs);
                }
            });
        }
    }

    public interface ArtistSongsCallback {
        void onSongsCollected(List<Child> songs);
    }

    public MutableLiveData<List<ArtistID3>> getStarredArtists(boolean random, int size) {
        MutableLiveData<List<ArtistID3>> starredArtists = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null) {
                            List<ArtistID3> artists = response.body().getSubsonicResponse().getStarred2().getArtists();

                            if (artists != null) {
                                if (!random) {
                                    getArtistInfo(artists, starredArtists);
                                } else {
                                    Collections.shuffle(artists);
                                    getArtistInfo(artists.subList(0, Math.min(size, artists.size())), starredArtists);
                                }
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return starredArtists;
    }

    public MutableLiveData<List<ArtistID3>> getArtists(boolean random, int size) {
        MutableLiveData<List<ArtistID3>> listLiveArtists = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            List<ArtistID3> artists = new ArrayList<>();

                            if(response.body().getSubsonicResponse().getArtists() != null && response.body().getSubsonicResponse().getArtists().getIndices() != null) {
                                for (IndexID3 index : response.body().getSubsonicResponse().getArtists().getIndices()) {
                                    if(index != null && index.getArtists() != null) {
                                        artists.addAll(index.getArtists());
                                    }
                                }
                            }

                            if (random) {
                                Collections.shuffle(artists);
                                getArtistInfo(artists.subList(0, artists.size() / size > 0 ? size : artists.size()), listLiveArtists);
                            } else {
                                listLiveArtists.setValue(artists);
                            }
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                    }
                });

        return listLiveArtists;
    }

    /*
     * Method that returns essential artist information (cover, album number, etc.)
     */
    public void getArtistInfo(List<ArtistID3> artists, MutableLiveData<List<ArtistID3>> list) {
        List<ArtistID3> liveArtists = list.getValue();
        if (liveArtists == null) liveArtists = new ArrayList<>();
        list.setValue(liveArtists);

        for (ArtistID3 artist : artists) {
            App.getSubsonicClientInstance(false)
                    .getBrowsingClient()
                    .getArtist(artist.getId())
                    .enqueue(new Callback<ApiResponse>() {
                        @Override
                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                            if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                                addToMutableLiveData(list, response.body().getSubsonicResponse().getArtist());
                            }
                        }

                        @Override
                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                        }
                    });
        }
    }

    public MutableLiveData<ArtistID3> getArtistInfo(String id) {
        MutableLiveData<ArtistID3> artist = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                            artist.setValue(response.body().getSubsonicResponse().getArtist());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artist;
    }

    public MutableLiveData<ArtistInfo2> getArtistFullInfo(String id) {
        MutableLiveData<ArtistInfo2> artistFullInfo = new MutableLiveData<>(null);

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtistInfo2(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtistInfo2() != null) {
                            artistFullInfo.setValue(response.body().getSubsonicResponse().getArtistInfo2());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artistFullInfo;
    }

    public void setRating(String id, int rating) {
        App.getSubsonicClientInstance(false)
                .getMediaAnnotationClient()
                .setRating(id, rating)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {

                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });
    }

    public MutableLiveData<ArtistID3> getArtist(String id) {
        MutableLiveData<ArtistID3> artist = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null) {
                            artist.setValue(response.body().getSubsonicResponse().getArtist());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return artist;
    }

    public MutableLiveData<List<Child>> getInstantMix(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> instantMix = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(artist.getId(), count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null) {
                            instantMix.setValue(response.body().getSubsonicResponse().getSimilarSongs2().getSongs());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return instantMix;
    }

    public MutableLiveData<List<Child>> getRandomSong(ArtistID3 artist, int count) {
        MutableLiveData<List<Child>> randomSongs = new MutableLiveData<>();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artist.getId())
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null &&
                                response.body().getSubsonicResponse().getArtist() != null &&
                                response.body().getSubsonicResponse().getArtist().getAlbums() != null) {

                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();
                            Log.d("ArtistRepository", "Got albums directly: " + albums.size());
                            if (albums.isEmpty()) {
                                Log.d("ArtistRepository", "No albums found in artist response");
                                return;
                            }

                            Collections.shuffle(albums);
                            int[] counts = albums.stream().mapToInt(AlbumID3::getSongCount).toArray();
                            Arrays.parallelPrefix(counts, Integer::sum);
                            int albumLimit = 0;
                            int multiplier = 4; // get more than the limit so we can shuffle them
                            while (albumLimit < albums.size() && counts[albumLimit] < count * multiplier)
                                albumLimit++;
                            Log.d("ArtistRepository", String.format("Retaining %d/%d albums", albumLimit, albums.size()));

                            fetchAllAlbumSongsWithCallback(albums.stream().limit(albumLimit).collect(Collectors.toList()), songs -> {
                                Collections.shuffle(songs);
                                randomSongs.setValue(songs.stream().limit(count).collect(Collectors.toList()));
                            });
                        } else {
                            Log.d("ArtistRepository", "Failed to get artist info");
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.d("ArtistRepository", "Error getting artist info: " + t.getMessage());
                    }
                });

        return randomSongs;
    }

    public MutableLiveData<List<Child>> getTopSongs(String artistName, int count) {
        MutableLiveData<List<Child>> topSongs = new MutableLiveData<>(new ArrayList<>());

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getTopSongs(artistName, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getTopSongs() != null && response.body().getSubsonicResponse().getTopSongs().getSongs() != null) {
                            topSongs.setValue(response.body().getSubsonicResponse().getTopSongs().getSongs());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return topSongs;
    }

    private void addToMutableLiveData(MutableLiveData<List<ArtistID3>> liveData, ArtistID3 artist) {
        List<ArtistID3> liveArtists = liveData.getValue();
        if (liveArtists != null) liveArtists.add(artist);
        liveData.setValue(liveArtists);
    }
}
