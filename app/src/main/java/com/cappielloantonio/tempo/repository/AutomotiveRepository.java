package com.cappielloantonio.tempo.repository;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.session.LibraryResult;
import androidx.media3.session.MediaConstants;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.BuildConfig;
import com.cappielloantonio.tempo.R;
import com.cappielloantonio.tempo.database.AppDatabase;
import com.cappielloantonio.tempo.database.dao.ChronologyDao;
import com.cappielloantonio.tempo.database.dao.SessionMediaItemDao;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.model.Chronology;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.model.SessionMediaItem;
import com.cappielloantonio.tempo.provider.AlbumArtContentProvider;
import com.cappielloantonio.tempo.service.DownloaderManager;
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
import com.cappielloantonio.tempo.util.DownloadUtil;
import com.cappielloantonio.tempo.util.MappingUtil;
import com.cappielloantonio.tempo.util.MusicUtil;
import com.cappielloantonio.tempo.util.Preferences;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class AutomotiveRepository {
    private final SessionMediaItemDao sessionMediaItemDao = AppDatabase.getInstance().sessionMediaItemDao();
    private final ChronologyDao chronologyDao = AppDatabase.getInstance().chronologyDao();

    private Bundle createContentStyleExtras(boolean gridView) {
        Bundle extras = new Bundle();
        int contentStyle = gridView
                ? MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_GRID_ITEM
                : MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM;
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE, contentStyle);
        extras.putInt(MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE, contentStyle);
        return extras;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getAlbums(String prefix, String type, int size) {
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
                                Uri artworkUri = AlbumArtContentProvider.contentUri(album.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setAlbumTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getArtists(String prefix, int size) {
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

                                        Uri artworkUri = AlbumArtContentProvider.contentUri(artist.getCoverArtId());

                                        Bundle extras = createContentStyleExtras(Preferences.isAndroidAutoAlbumViewEnabled());

                                        MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                                .setTitle(artist.getName())
                                                .setIsBrowsable(true)
                                                .setIsPlayable(false)
                                                .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                                                .setArtworkUri(artworkUri)
                                                .setExtras(extras)
                                                .build();

                                        MediaItem mediaItem = new MediaItem.Builder()
                                                .setMediaId(prefix + artist.getId())
                                                .setMediaMetadata(mediaMetadata)
                                                .setUri("")
                                                .build();

                                        mediaItems.add(mediaItem);
                                        count++;
                                    }
                                }
                            }
                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {
                        listenableFuture.setException(t);
                    }
                });
        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredSongs() {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getAlbumSongListClient()
                .getStarred2()
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getStarred2() != null && response.body().getSubsonicResponse().getStarred2().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getStarred2().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
                .getRandomSongs(100, null, null)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getRandomSongs() != null && response.body().getSubsonicResponse().getRandomSongs().getSongs() != null) {
                            List<Child> songs = response.body().getSubsonicResponse().getRandomSongs().getSongs();

                            setChildrenMetadata(songs);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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

                    List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);

                    LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                    listenableFuture.set(libraryResult);
                } else {
                    listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                }

                chronologyDao.getLastPlayed(server, count).removeObserver(this);
            }
        });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredAlbums(String prefix) {
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
                                Uri artworkUri = AlbumArtContentProvider.contentUri(album.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<ApiResponse> call, @NonNull Throwable t) {

                    }
                });

        return listenableFuture;
    }

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getStarredArtists(String prefix) {
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
                                Uri artworkUri = AlbumArtContentProvider.contentUri(artist.getCoverArtId());

                                Bundle extras = createContentStyleExtras(Preferences.isAndroidAutoAlbumViewEnabled());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(artist.getName())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                        .setArtworkUri(artworkUri)
                                        .setExtras(extras)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + artist.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
                            }

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, Constants.AA_ALBUM_SOURCE + id);

                            LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);

                            listenableFuture.set(libraryResult);
                        } else {
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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

                            for (AlbumID3 album : albums) {
                                Uri artworkUri = AlbumArtContentProvider.contentUri(album.getCoverArtId());

                                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                        .setTitle(album.getName())
                                        .setAlbumTitle(album.getName())
                                        .setArtist(album.getArtist())
                                        .setGenre(album.getGenre())
                                        .setIsBrowsable(true)
                                        .setIsPlayable(false)
                                        .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                        .setArtworkUri(artworkUri)
                                        .build();

                                MediaItem mediaItem = new MediaItem.Builder()
                                        .setMediaId(prefix + album.getId())
                                        .setMediaMetadata(mediaMetadata)
                                        .setUri("")
                                        .build();

                                mediaItems.add(mediaItem);
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

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks, Constants.AA_PLAYLIST_SOURCE + id);

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

    public ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> getMadeForYou(String id, int count) {
        final SettableFuture<LibraryResult<ImmutableList<MediaItem>>> listenableFuture = SettableFuture.create();

        App.getSubsonicClientInstance(false)
                .getBrowsingClient()
                .getSimilarSongs2(id, count)
                .enqueue(new Callback<ApiResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<ApiResponse> call, @NonNull Response<ApiResponse> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().getSubsonicResponse().getSimilarSongs2() != null && response.body().getSubsonicResponse().getSimilarSongs2().getSongs() != null) {
                            List<Child> tracks = response.body().getSubsonicResponse().getSimilarSongs2().getSongs();

                            setChildrenMetadata(tracks);

                            List<MediaItem> mediaItems = MappingUtil.mapMediaItems(tracks);

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
                                    Uri artworkUri = AlbumArtContentProvider.contentUri(artist.getCoverArtId());

                                    Bundle extras = createContentStyleExtras(Preferences.isAndroidAutoAlbumViewEnabled());

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(artist.getName())
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                                            .setArtworkUri(artworkUri)
                                            .setExtras(extras)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(artistPrefix + artist.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri("")
                                            .build();

                                    mediaItems.add(mediaItem);
                                }
                            }

                            if (response.body().getSubsonicResponse().getSearchResult3().getAlbums() != null) {
                                for (AlbumID3 album : response.body().getSubsonicResponse().getSearchResult3().getAlbums()) {
                                    Uri artworkUri = AlbumArtContentProvider.contentUri(album.getCoverArtId());

                                    MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                                            .setTitle(album.getName())
                                            .setAlbumTitle(album.getName())
                                            .setArtist(album.getArtist())
                                            .setGenre(album.getGenre())
                                            .setIsBrowsable(true)
                                            .setIsPlayable(false)
                                            .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                                            .setArtworkUri(artworkUri)
                                            .build();

                                    MediaItem mediaItem = new MediaItem.Builder()
                                            .setMediaId(albumPrefix + album.getId())
                                            .setMediaMetadata(mediaMetadata)
                                            .setUri("")
                                            .build();

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
                            listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
                        List<MediaItem> mediaItems = MappingUtil.mapMediaItems(songs);
                        LibraryResult<ImmutableList<MediaItem>> libraryResult = LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null);
                        listenableFuture.set(libraryResult);
                    } else {
                        listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
                    }
                } else {
                    listenableFuture.set(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE));
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
