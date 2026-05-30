package com.cappielloantonio.tempo.model;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;

import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.Playlist;

@Keep
@Entity(
    tableName = "playlist_song",
    primaryKeys = {"playlist_id", "id"},
    foreignKeys = @ForeignKey(
        entity = Playlist.class,
        parentColumns = "id",
        childColumns = "playlist_id"
    ),
    indices = {@Index("playlist_id")}
)
public class PlaylistSong {
    @NonNull
    @ColumnInfo(name = "playlist_id")
    private String playlistId;
    @NonNull
    @ColumnInfo(name = "id")
    private String id;
    @ColumnInfo(name = "title")
    private String title;
    @ColumnInfo(name = "artist")
    private String artist;
    @ColumnInfo(name = "album")
    private String album;
    @ColumnInfo(name = "track")
    private Integer track;
    @ColumnInfo(name = "cover_art_id")
    private String coverArtId;
    @ColumnInfo(name = "duration")
    private Integer duration;
    @ColumnInfo(name = "album_id")
    private String albumId;
    @ColumnInfo(name = "artist_id")
    private String artistId;

    public PlaylistSong(String playlistId, String id, String title, String artist, String album, Integer track, String coverArtId, Integer duration, String albumId, String artistId) {
        this.playlistId = playlistId;
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.track = track;
        this.coverArtId = coverArtId;
        this.duration = duration;
        this.albumId = albumId;
        this.artistId = artistId;
    }

    public PlaylistSong(String playlistId, Child child) {
        this.playlistId = playlistId;
        this.id = child.getId();
        this.title = child.getTitle();
        this.artist = child.getArtist();
        this.album = child.getAlbum();
        this.track = child.getTrack();
        this.coverArtId = child.getCoverArtId();
        this.duration = child.getDuration();
        this.albumId = child.getAlbumId();
        this.artistId = child.getArtistId();
    }

    @NonNull
    public String getPlaylistId() {
        return playlistId;
    }

    public void setPlaylistId(@NonNull String playlistId) {
        this.playlistId = playlistId;
    }

    @NonNull
    public String getId() {
        return id;
    }

    public void setId(@NonNull String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public Integer getTrack() {
        return track;
    }

    public void setTrack(Integer track) {
        this.track = track;
    }

    public String getCoverArtId() {
        return coverArtId;
    }

    public void setCoverArtId(String coverArtId) {
        this.coverArtId = coverArtId;
    }

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(Integer duration) {
        this.duration = duration;
    }

    public String getAlbumId() {
        return albumId;
    }

    public void setAlbumId(String albumId) {
        this.albumId = albumId;
    }

    public String getArtistId() {
        return artistId;
    }

    public void setArtistId(String artistId) {
        this.artistId = artistId;
    }
}
