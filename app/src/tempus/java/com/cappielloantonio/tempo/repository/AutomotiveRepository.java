package com.cappielloantonio.tempo.repository;

import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.Log;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaConstants;
import androidx.media3.session.SessionError;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ChronologyDao;
import com.cappielloantonio.tempo.database.dao.SessionMediaItemDao;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.model.SessionMediaItem;
import com.cappielloantonio.tempo.provider.AlbumArtContentProvider;
import com.cappielloantonio.tempo.subsonic.base.ApiResponse;
import com.cappielloantonio.tempo.subsonic.models.AlbumID3;
import com.cappielloantonio.tempo.subsonic.models.Artist;
import com.cappielloantonio.tempo.subsonic.models.ArtistID3;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Directory;
import com.cappielloantonio.tempo.subsonic.models.Index;
import com.cappielloantonio.tempo.subsonic.models.IndexID3;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.subsonic.models.MusicFolder;
import com.cappielloantonio.tempo.subsonic.models.Playlist;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;
import com.cappielloantonio.tempo.subsonic.models.Genre;
import com.cappielloantonio.tempo.util.Constants;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@UnstableApi
public class AutomotiveRepository {
    private final SessionMediaItemDao sessionMediaItemDao = AppDatabase.getInstance().sessionMediaItemDao();
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    private static final String TAG = "AutomotiveRepository";

    public final int INSTANT_MIX_MAX_TRACKS = 20;
    public final int INSTANT_MIX_MIN_TRACKS_SMALL_MIX = INSTANT_MIX_MAX_TRACKS;
    public final int INSTANT_MIX_MIN_TRACKS_MEDIUM_MIX = INSTANT_MIX_MAX_TRACKS+10;
    public final int INSTANT_MIX_MIN_TRACKS_LARGE_MIX = INSTANT_MIX_MAX_TRACKS+20;
    public final int INSTANT_MIX_NUMBER_OF_TRACKS_IN_SMALL_MIX = 12;
    public final int INSTANT_MIX_NUMBER_OF_TRACKS_IN_MEDIUM_MIX = 15;
    public final int INSTANT_MIX_NUMBER_OF_TRACKS_IN_LARGE_MIX = 18;

    public final InstantMixBuilder instantMixBuilder = new InstantMixBuilder(this);
    public final MadeForYouBuilder madeForYouBuilder = new MadeForYouBuilder(this);

    private Bundle createContentStyleExtras(boolean gridView) {
        Bundle extras = new Bundle();
        int contentStyle = gridView
                ? MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                : MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM;
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, contentStyle);
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, contentStyle);
        return extras;
    }

    private MediaItem createFunction(String title, String id, boolean isGridView, Uri artworkUri){
        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setArtworkUri(artworkUri)
                .setExtras(createContentStyleExtras(isGridView))
                .build();

        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(mediaMetadata)
                .setUri("")
                .build();
    }

    private MediaItem createArtist(String artistName, String id, boolean isGridView, String artistCoverArtId){
        Uri artworkUri = (artistCoverArtId != null && !artistCoverArtId.isEmpty())
                ? AlbumArtContentProvider.contentUri(artistCoverArtId)
                : Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_artists);

        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(artistName)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                .setArtworkUri(artworkUri)
                .setExtras(createContentStyleExtras(isGridView))
                .build();

        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(mediaMetadata)
                .setUri("")
                .build();
    }

    private MediaItem createAlbum(String albumName, String artirstName, String genre, String id, boolean isPlayable, String albumCoverArtId){
        Uri artworkUri = (albumCoverArtId != null && !albumCoverArtId.isEmpty())
                ? AlbumArtContentProvider.contentUri(albumCoverArtId)
                : Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_albums);

        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                .setTitle(albumName)
                .setAlbumTitle(albumName)
                .setArtist(artirstName)
                .setGenre(genre)
                .setIsBrowsable(!isPlayable)
                .setIsPlayable(isPlayable)
                .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                .setArtworkUri(artworkUri)
                .build();

        return new MediaItem.Builder()
                .setMediaId(id)
                .setMediaMetadata(mediaMetadata)
                .setUri("")
                .build();
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbums(String prefix, String type, int size, Boolean isRootCall) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2(type, size, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbumList2() != null && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getAlbumList2().getAlbums();

                            // Hack for artist view
                            if("alphabeticalByArtist".equals(type))for(AlbumID3 album : albums){
                                String artistName = album.getArtist();
                                String albumName = album.getName();
                                album.setName(artistName);
                                album.setArtist(albumName);
                            }

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                MediaItem mediaItem = createAlbum(
                                        album.getName(),
                                        album.getArtist(),
                                        album.getGenre(),
                                        prefix + album.getId(),
                                        false,
                                        album.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }

                            if (isRootCall == true) {
                                MediaItem jumpTo = createFunction(
                                        App.getContext().getString(R.string.aa_starred_albums),
                                        Constants.AA_JUMP_TO_STARRED_ALBUMS_ID,
                                        Preferences.isAndroidAutoAlbumViewEnabled(),
                                        Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_star_album)
                                );
                                mediaItems.add(0, jumpTo);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtists(String prefix, int size, Boolean isRootCall) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();
        if (size > 500) size = 500;
        final int maxSize = size;
        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getSubsonicResponse().getArtists() != null
                                && response.body().getSubsonicResponse().getArtists().getIndices() != null) {

                            List<IndexID3> indices = response.body().getSubsonicResponse().getArtists().getIndices();
                            List<MediaItem> mediaItems = new ArrayList<>();

                            int count = 0;
                            for (IndexID3 index : indices) {
                                if (index.getArtists() != null && count < maxSize) {
                                    for (ArtistID3 artist : index.getArtists()) {
                                        if (count >= maxSize) break;

                                        MediaItem mediaItem = createArtist(
                                                artist.getName(),
                                                prefix + artist.getId(),
                                                Preferences.isAndroidAutoAlbumViewEnabled(),
                                                artist.getCoverArtId()
                                        );

                                        mediaItems.add(mediaItem);
                                        count++;
                                    }
                                }
                            }

                            MediaItem jumpTo = createFunction(
                                    App.getContext().getString(R.string.aa_view_by_albums),
                                    Constants.AA_ARTISTS_BY_ALBUMS_ID,
                                    Preferences.isAndroidAutoAlbumViewEnabled(),
                                    Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_albums)
                            );
                            mediaItems.add(0, jumpTo);

                            if (isRootCall == true) {
                                jumpTo = createFunction(
                                        App.getContext().getString(R.string.aa_starred_artists),
                                        Constants.AA_JUMP_TO_STARRED_ARTISTS_ID,
                                        Preferences.isAndroidAutoAlbumViewEnabled(),
                                        Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_artists)
                                );
                                mediaItems.add(0, jumpTo);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });
        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredSongs(int size) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();
        if (size > 500) size = 500;
        final int maxSize = size;

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            setChildrenMetadata(songs);

                            if( !Preferences.isAndroidAutoShuffleStarredTracksEnabled() ) {
                                songs = songs.subList(0, Math.min(maxSize, songs.size()));
                            }
                            else {
                                Collections.shuffle(songs);
                                songs = songs.subList(0, Math.min(100, songs.size()));
                            }

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, Constants.AA_QUEUE_CACHED_SOURCE);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRandomSongs(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(count, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, Constants.AA_QUEUE_CACHED_SOURCE);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getRecentlyPlayedSongs(String server, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        chronologyDao.getLastPlayed(server, count).observeForever(new Observer<List<Chronology>>() {
            @Override
            public void onChanged(List<Chronology> chronology) {
                if (chronology != null && !chronology.isEmpty()) {
                    List<Child> songs = new ArrayList<>(chronology);

                    setChildrenMetadata(songs);

                    List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, Constants.AA_QUEUE_CACHED_SOURCE);

                    LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                    listenableFuture.set(libraryResult);
                } else {
                    listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                }

                chronologyDao.getLastPlayed(server, count).removeObserver(this);
            }
        });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredAlbums(String prefix, Boolean isRootCall) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getAlbums() != null) {
                            List<AlbumID3> albums = response.body().getSubsonicResponse().getStarred2().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (AlbumID3 album : albums) {
                                MediaItem mediaItem = createAlbum(
                                        album.getName(),
                                        album.getArtist(),
                                        album.getGenre(),
                                        prefix + album.getId(),
                                        false,
                                        album.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }

                            if (isRootCall == true) {
                                MediaItem jumpTo = createFunction(
                                        App.getContext().getString(R.string.aa_albums),
                                        Constants.AA_JUMP_TO_ALBUMS_ID,
                                        Preferences.isAndroidAutoAlbumViewEnabled(),
                                        Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_albums)
                                );
                                mediaItems.add(0, jumpTo);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredArtists(String prefix, Boolean isRootCall) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getArtists() != null) {
                            List<ArtistID3> artists = response.body().getSubsonicResponse().getStarred2().getArtists();

                            artists.sort((a1, a2) -> {
                                String name1 = a1.getName() != null ? a1.getName() : "";
                                String name2 = a2.getName() != null ? a2.getName() : "";
                                return name1.compareToIgnoreCase(name2);
                            });

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (ArtistID3 artist : artists) {
                                MediaItem mediaItem = createArtist(
                                        artist.getName(),
                                        prefix + artist.getId(),
                                        Preferences.isAndroidAutoAlbumViewEnabled(),
                                        artist.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }
                            if (isRootCall == true) {
                                MediaItem jumpTo = createFunction(
                                        App.getContext().getString(R.string.aa_artists),
                                        Constants.AA_JUMP_TO_ARTISTS_ID,
                                        Preferences.isAndroidAutoAlbumViewEnabled(),
                                        Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_artists)
                                );
                                mediaItems.add(0, jumpTo);
                            }
                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMusicFolders(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicFolders()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getMusicFolders() != null && response.body().getSubsonicResponse().getMusicFolders().getMusicFolders() != null) {
                            List<MusicFolder> musicFolders = response.body().getSubsonicResponse().getMusicFolders().getMusicFolders();

                            List<MediaItem> mediaItems = new ArrayList<>();
                            Uri artworkUri = Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_folders);

                            for (MusicFolder musicFolder : musicFolders) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(musicFolder.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + musicFolder.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getIndexes(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getIndexes(id, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getIndexes() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getIndexes().getIndices() != null) {
                                List<Index> indices = response.body().getSubsonicResponse().getIndexes().getIndices();

                                for (Index index : indices) {
                                    if (index.getArtists() != null) {
                                        for (Artist artist : index.getArtists()) {
                                            MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                                    .setTitle(artist.getName())
                                                    .setIsBrowsable(true)
                                                    .setIsPlayable(false)
                                                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                                    .build();

                                            MediaItem mediaItem = new MediaItem.Builder()
                                                    .setMediaId(prefix + artist.getId())
                                                    .setMediaMetadata(mediaMetadata)
                                                    .setUri("")
                                                    .build();

                                            mediaItems.add(mediaItem);
                                        }
                                    }
                                }
                            }

                            if (response.body().getSubsonicResponse().getIndexes().getChildren() != null) {
                                List<Child> children = response.body().getSubsonicResponse().getIndexes().getChildren();

                                for (Child song : children) {
                                    Uri artworkUri = AlbumArtContentProvider.contentUri(song.getCoverArtId());

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(song.getTitle())
                                            .setAlbumTitle(song.getAlbum())
                                            .setArtist(song.getArtist())
                                            .setIsBrowsable(false)
                                            .setIsPlayable(true)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(prefix + song.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri(MusicUtil.getStreamUri(song.getId()))
                                            .build();

                                    mediaItems.add(mediaItem);
                                }

                                setChildrenMetadata(children);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getDirectories(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getMusicDirectory(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getDirectory() != null && response.body().getSubsonicResponse().getDirectory().getChildren() != null) {
                            Directory directory = response.body().getSubsonicResponse().getDirectory();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Child child : directory.getChildren()) {
                                Uri artworkUri = AlbumArtContentProvider.contentUri(child.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(child.getTitle())
                                        .setIsBrowsable(child.isDir())
                                        .setIsPlayable(!child.isDir())
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(child.isDir() ? prefix + child.getId() : child.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(!child.isDir() ? MusicUtil.getStreamUri(child.getId()) : Uri.parse(""))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setChildrenMetadata(directory.getChildren().stream().filter(child -> !child.isDir()).collect(Collectors.toList()));

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylists(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylists()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylists() != null && response.body().getSubsonicResponse().getPlaylists().getPlaylists() != null) {
                            List<Playlist> playlists = response.body().getSubsonicResponse().getPlaylists().getPlaylists();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Playlist playlist : playlists) {
                                String coverId = playlist.getCoverArtId();
                                Uri artworkUri = (coverId != null && !coverId.isEmpty())
                                        ? AlbumArtContentProvider.contentUri(coverId)
                                        : Uri.parse("android.resource://" + BuildConfig.APPLICATION_ID + "/" + R.drawable.ic_aa_playlist);

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(playlist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + playlist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getNewestPodcastEpisodes(int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPodcastClient()
                .getNewestPodcasts(count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getNewestPodcasts() != null && response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes() != null) {
                            List<PodcastEpisode> episodes = response.body().getSubsonicResponse().getNewestPodcasts().getEpisodes();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (PodcastEpisode episode : episodes) {
                                Uri artworkUri = AlbumArtContentProvider.contentUri(episode.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(episode.getTitle())
                                        .setIsBrowsable(false)
                                        .setIsPlayable(true)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(episode.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri(MusicUtil.getStreamUri(episode.getStreamId()))
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            setPodcastEpisodesMetadata(episodes);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getInternetRadioStations() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getInternetRadioClient()
                .getInternetRadioStations()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getInternetRadioStations() != null && response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations() != null) {

                            List<InternetRadioStation> radioStations = response.body().getSubsonicResponse().getInternetRadioStations().getInternetRadioStations();

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (InternetRadioStation radioStation : radioStations) {
                                mediaItems.add(MappingUtil.mapInternetRadioStation(radioStation));
                            }

                            setInternetRadioStationsMetadata(radioStations);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbumTracks(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getAlbum(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getAlbum() != null && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getAlbum().getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, Constants.AA_QUEUE_CACHED_SOURCE);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtistAlbum(String prefix, String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getArtist() != null && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {

                            List<AlbumID3> albums = response.body().getSubsonicResponse().getArtist().getAlbums();

                            List<MediaItem> mediaItems = new ArrayList<>();
                            int totalTracks = 0;

                            for (AlbumID3 album : albums) {
                                if (album.getSongCount() != null) {
                                    totalTracks += album.getSongCount();
                                }

                                MediaItem mediaItem = createAlbum(
                                        album.getName(),
                                        album.getArtist(),
                                        album.getGenre(),
                                        prefix + album.getId(),
                                        false,
                                        album.getCoverArtId()
                                );
                                mediaItems.add(mediaItem);
                            }

                            if (albums.size() >= 2 && totalTracks >= INSTANT_MIX_MIN_TRACKS_SMALL_MIX) {
                                int numberOfTracks =
                                        totalTracks >= INSTANT_MIX_MIN_TRACKS_LARGE_MIX ? INSTANT_MIX_NUMBER_OF_TRACKS_IN_LARGE_MIX :
                                                totalTracks >= INSTANT_MIX_MIN_TRACKS_MEDIUM_MIX ? INSTANT_MIX_NUMBER_OF_TRACKS_IN_MEDIUM_MIX :
                                                        INSTANT_MIX_NUMBER_OF_TRACKS_IN_SMALL_MIX;
                                ArtistID3 artist = response.body().getSubsonicResponse().getArtist();
                                MediaItem instantMixItem = createAlbum(
                                        App.getContext().getString(R.string.aa_instant_mix),
                                        "By Tempus",
                                        "Instant Mix",
                                        Constants.AA_INSTANTMIX_SOURCE + "[" + numberOfTracks + "]" + id,
                                        true,
                                        artist.getCoverArtId()
                                );
                                mediaItems.add(0, instantMixItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getInstantMix(String artistId, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        Log.d(TAG, "Instant Mix: Starting for artistId=" + artistId + " for " + count + " tracks");

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getArtist(artistId)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getArtist() != null
                                && response.body().getSubsonicResponse().getArtist().getAlbums() != null) {

                            List<AlbumID3> albums = new ArrayList<>(
                                    response.body().getSubsonicResponse().getArtist().getAlbums()
                            );
                            Log.d(TAG, "Instant Mix: Found " + albums.size() + " albums");

                            Random random = new Random();
                            Collections.shuffle(albums, random);

                            // Fetch just the first album to get the first track
                            AlbumID3 firstAlbum = albums.get(0);
                            App.getSubsonicClientInstance(false)
                                    .getBrowsingClient()
                                    .getAlbum(firstAlbum.getId())
                                    .enqueue(new Callback<ApiResponse>() {
                                        @Override
                                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                                            if (response.isSuccessful()
                                                    && response.body() != null
                                                    && response.body().getSubsonicResponse().getAlbum() != null
                                                    && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {

                                                List<Child> songs = response.body().getSubsonicResponse().getAlbum().getSongs();
                                                Child firstTrack = songs.get(random.nextInt(songs.size()));

                                                Log.d(TAG, "Instant Mix: First track: " + firstTrack.getTitle());

                                                setChildrenMetadata(List.of(firstTrack));

                                                // Tag parent_id with artistId so handle() can resume the mix
                                                MediaItem mediaItem = MappingUtil.mapMediaItem(firstTrack,
                                                        Constants.AA_INSTANTMIX_SOURCE+ "[" + count + "]"  + artistId);

                                                listenableFuture.set(LibraryResult.ofItemList(
                                                        ImmutableList.of(mediaItem), null));
                                            } else {
                                                Log.e(TAG, "Instant Mix: Failed to fetch first album");
                                                listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                                            }
                                        }
                                        @Override
                                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                                            Log.e(TAG, "Instant Mix: Failed to fetch first album: " + t.getMessage());
                                            listenableFuture.setException(t);
                                        }
                                    });
                        } else {
                            Log.e(TAG, "Instant Mix: Failed to retrieve artist albums for artistId=" + artistId);
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "Instant Mix: Network failure: " + t.getMessage());
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getPlaylistSongs(String id) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getPlaylistClient()
                .getPlaylist(id)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getPlaylist() != null && response.body().getSubsonicResponse().getPlaylist().getEntries() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getPlaylist().getEntries();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, Constants.AA_QUEUE_CACHED_SOURCE);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMadeForYou(String mixType, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getAlbumList2("recent", 15, 0, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getAlbumList2() != null
                                && response.body().getSubsonicResponse().getAlbumList2().getAlbums() != null) {
                            List<AlbumID3> recentAlbums = new ArrayList<>(
                                    response.body().getSubsonicResponse().getAlbumList2().getAlbums());
                            Log.d(TAG, "MadeForYou: recent albums loaded: " + recentAlbums.size());

                            if (recentAlbums.isEmpty()) {
                                Log.w(TAG, "MadeForYou: No recent albums, falling back to random song");
                                fallbackToFirstRandomSong(mixType, count, listenableFuture);
                                return;
                            }

                            Random random = new Random();
                            Collections.shuffle(recentAlbums, random);

                            // Fetch just the first album to get the first track
                            AlbumID3 firstAlbum = recentAlbums.get(0);
                            App.getSubsonicClientInstance(false)
                                    .getBrowsingClient()
                                    .getAlbum(firstAlbum.getId())
                                    .enqueue(new Callback<ApiResponse>() {
                                        @Override
                                        public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                                            if (response.isSuccessful()
                                                    && response.body() != null
                                                    && response.body().getSubsonicResponse().getAlbum() != null
                                                    && response.body().getSubsonicResponse().getAlbum().getSongs() != null) {

                                                List<Child> songs = response.body().getSubsonicResponse().getAlbum().getSongs();
                                                Child firstTrack = songs.get(random.nextInt(songs.size()));

                                                Log.d(TAG, "MadeForYou: First track: " + firstTrack.getTitle());

                                                setChildrenMetadata(List.of(firstTrack));

                                                // Tag parent_id with mixType so handle() can resume the mix
                                                MediaItem mediaItem = MappingUtil.mapMediaItem(firstTrack,
                                                        Constants.AA_MADE_FOR_YOU_SOURCE+ "[" + count + "]"  + mixType);

                                                listenableFuture.set(LibraryResult.ofItemList(
                                                        ImmutableList.of(mediaItem), null));
                                            } else {
                                                Log.e(TAG, "MadeForYou: Failed to fetch first album");
                                                fallbackToFirstRandomSong(mixType, count, listenableFuture);                                            }
                                        }
                                        @Override
                                        public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                                            Log.e(TAG, "MadeForYou: Failed to fetch first album: " + t.getMessage());
                                            listenableFuture.setException(t);
                                        }
                                    });
                        } else {
                            Log.w(TAG, "MadeForYou: Recent albums failed, falling back to random songs");
                            fallbackToFirstRandomSong(mixType, count, listenableFuture);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "MadeForYou: Failed to fetch recent albums: " + t.getMessage());
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    private void fallbackToFirstRandomSong(
            String mixType,
            int count,
            SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture) {

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getRandomSongs(1, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful()
                                && response.body() != null
                                && response.body().getSubsonicResponse().getRandomSongs() != null
                                && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {

                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();
                            Child firstTrack = songs.get(0);

                            Log.d(TAG, "MadeForYou: Fallback first track: " + firstTrack.getTitle());

                            setChildrenMetadata(List.of(firstTrack));

                            MediaItem mediaItem = MappingUtil.mapMediaItem(firstTrack,
                                    Constants.AA_MADE_FOR_YOU_SOURCE + "[" + count + "]" + mixType);

                            listenableFuture.set(LibraryResult.ofItemList(
                                    ImmutableList.of(mediaItem), null));
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }
                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        Log.e(TAG, "MadeForYou: Fallback random song failed: " + t.getMessage());
                        listenableFuture.setException(t);
                    }
                });
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> search(String query, String albumPrefix, String artistPrefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getSearchingClient()
                .search3(query, 20, 0, 20, 0, 20, 0)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSearchResult3() != null) {
                            List<MediaItem> mediaItems = new ArrayList<>();

                            if (response.body().getSubsonicResponse().getSearchResult3().getArtists() != null) {
                                for (ArtistID3 artist : response.body().getSubsonicResponse().getSearchResult3().getArtists()) {

                                    MediaItem mediaItem = createArtist(
                                            artist.getName(),
                                            artistPrefix + artist.getId(),
                                            Preferences.isAndroidAutoAlbumViewEnabled(),
                                            artist.getCoverArtId()
                                    );
                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 album : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    MediaItem mediaItem = createAlbum(
                                            album.getName(),
                                            album.getArtist(),
                                            album.getGenre(),
                                            albumPrefix + album.getId(),
                                            false,
                                            album.getCoverArtId()
                                    );
                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getSongs() != null) {
                                List<Child> tracks = response.body().getSubsonicResponse().getSearchResult3().getSongs();
                                setChildrenMetadata(tracks);
                                mediaItems.addAll(MappingUtil.mapMediaItems(tracks));
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setChildrenMetadata(List<Child> children) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (Child child : children) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(child);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setPodcastEpisodesMetadata(List<PodcastEpisode> podcastEpisodes) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (PodcastEpisode podcastEpisode : podcastEpisodes) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(podcastEpisode);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    @OptIn(markerClass = UnstableApi.class)
    public void setInternetRadioStationsMetadata(List<InternetRadioStation> internetRadioStations) {
        long timestamp = System.currentTimeMillis();
        ArrayList<SessionMediaItem> sessionMediaItems = new ArrayList<>();

        for (InternetRadioStation internetRadioStation : internetRadioStations) {
            SessionMediaItem sessionMediaItem = new SessionMediaItem(internetRadioStation);
            sessionMediaItem.setTimestamp(timestamp);
            sessionMediaItems.add(sessionMediaItem);
        }

        InsertAllThreadSafe insertAll = new InsertAllThreadSafe(sessionMediaItemDao, sessionMediaItems);
        Thread thread = new Thread(insertAll);
        thread.start();
    }

    public SessionMediaItem getSessionMediaItem(String id) {
        SessionMediaItem sessionMediaItem = null;

        GetMediaItemThreadSafe getMediaItemThreadSafe = new GetMediaItemThreadSafe(sessionMediaItemDao, id);
        Thread thread = new Thread(getMediaItemThreadSafe);
        thread.start();

        try {
            thread.join();
            sessionMediaItem = getMediaItemThreadSafe.getSessionMediaItem();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return sessionMediaItem;
    }

    public List<MediaItem> getMetadatas(long timestamp) {
        List<MediaItem> mediaItems = Collections.emptyList();

        GetMediaItemsThreadSafe getMediaItemsThreadSafe = new GetMediaItemsThreadSafe(sessionMediaItemDao, timestamp);
        Thread thread = new Thread(getMediaItemsThreadSafe);
        thread.start();

        try {
            thread.join();
            mediaItems = getMediaItemsThreadSafe.getMediaItems();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return mediaItems;
    }

    public void deleteMetadata() {
        DeleteAllThreadSafe delete = new DeleteAllThreadSafe(sessionMediaItemDao);
        Thread thread = new Thread(delete);
        thread.start();
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getGenres(String prefix) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getGenres()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getGenres() != null && response.body().getSubsonicResponse().getGenres().getGenres() != null) {
                            List<Genre> genres = response.body().getSubsonicResponse().getGenres().getGenres();

                            // Sort genres alphabetically by name
                            genres.sort((g1, g2) -> {
                                String name1 = g1.getGenre() != null ? g1.getGenre() : "";
                                String name2 = g2.getGenre() != null ? g2.getGenre() : "";
                                return name1.compareToIgnoreCase(name2);
                            });

                            List<MediaItem> mediaItems = new ArrayList<>();

                            for (Genre genre : genres) {
                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(genre.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + genre.getGenre())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSongsByGenre(String genre, int count, boolean shuffle) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        Call<ApiResponse> call;
        if (shuffle) {
            call = App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getRandomSongs(count, null, null, genre);
        } else {
            call = App.getSubsonicClientInstance(false)
                    .getAlbumSongListClient()
                    .getSongsByGenre(genre, count, 0);
        }

        call.enqueue(new Callback<ApiResponse>() {
            @Override
            public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<com.cappielloantonio.tempo.subsonic.models.Child> songs;
                    if (shuffle) {
                        songs = response.body().getSubsonicResponse().getRandomSongs() != null
                                ? response.body().getSubsonicResponse().getRandomSongs().getSongs()
                                : null;
                    } else {
                        songs = response.body().getSubsonicResponse().getSongsByGenre() != null
                                ? response.body().getSubsonicResponse().getSongsByGenre().getSongs()
                                : null;
                    }

                    if (songs != null) {
                        setChildrenMetadata(songs);
                        List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs, Constants.AA_QUEUE_CACHED_SOURCE);
                        LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);
                        listenableFuture.set(libraryResult);
                    } else {
                        listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                    }
                } else {
                    listenableFuture.set(LibraryResult.ofError(SessionError.ERROR_BAD_VALUE));
                }
            }

            @Override
            public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                listenableFuture.setException(t);
            }
        });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getSongsByGenre(String genre, int count) {
        return getSongsByGenre(genre, count, false);
    }

    private static class GetMediaItemThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final String id;

        private SessionMediaItem sessionMediaItem;

        public GetMediaItemThreadSafe(SessionMediaItemDao sessionMediaItemDao, String id) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.id = id;
        }

        @Override
        public void run() {
            sessionMediaItem = sessionMediaItemDao.get(id);
        }

        public SessionMediaItem getSessionMediaItem() {
            return sessionMediaItem;
        }
    }

    @OptIn(markerClass = UnstableApi.class)
    private static class GetMediaItemsThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final Long timestamp;
        private final List<MediaItem> mediaItems = new ArrayList<>();

        public GetMediaItemsThreadSafe(SessionMediaItemDao sessionMediaItemDao, Long timestamp) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.timestamp = timestamp;
        }

        @Override
        public void run() {
            List<SessionMediaItem> sessionMediaItems = sessionMediaItemDao.get(timestamp);
            sessionMediaItems.forEach(sessionMediaItem -> mediaItems.add(sessionMediaItem.getMediaItem()));
        }

        public List<MediaItem> getMediaItems() {
            return mediaItems;
        }
    }

    private static class InsertAllThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;
        private final List<SessionMediaItem> sessionMediaItems;

        public InsertAllThreadSafe(SessionMediaItemDao sessionMediaItemDao, List<SessionMediaItem> sessionMediaItems) {
            this.sessionMediaItemDao = sessionMediaItemDao;
            this.sessionMediaItems = sessionMediaItems;
        }

        @Override
        public void run() {
            sessionMediaItemDao.insertAll(sessionMediaItems);
        }
    }

    private static class DeleteAllThreadSafe implements Runnable {
        private final SessionMediaItemDao sessionMediaItemDao;

        public DeleteAllThreadSafe(SessionMediaItemDao sessionMediaItemDao) {
            this.sessionMediaItemDao = sessionMediaItemDao;
        }

        @Override
        public void run() {
            sessionMediaItemDao.deleteAll();
        }
    }
}
