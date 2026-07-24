package com.cappielloantonio.tempo.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.cappielloantonio.tempo.subsonic.models.Playlist;

import java.util.List;

@Dao
public interface PlaylistDao {

    @Query("SELECT * FROM playlist")
    LiveData<List<Playlist>> getAll();

    @Query("SELECT * FROM playlist")
    List<Playlist> getAllSync();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Playlist playlist);

    // Insert only if the row is absent. REPLACE would delete-then-reinsert an existing
    // playlist, which fails the playlist_song foreign key when its songs already exist
    // (and can race when several callers cache the same playlist at once). Used to make
    // sure a deep-linked playlist's row exists before caching its songs. See issue #729.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertIfAbsent(Playlist playlist);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Playlist> playlists);

    @Delete
    void delete(Playlist playlist);

    @Query("DELETE FROM playlist WHERE id = :playlistId")
    void deleteById(String playlistId);

    @Query("DELETE FROM playlist")
    void deleteAll();

    @Query("SELECT coverArt FROM playlist WHERE id = :playlistId")
    String getPlaylistCoverArtId(String playlistId);

    @Query("UPDATE playlist SET name = :newName WHERE id = :playlistId")
    void updateName(String playlistId, String newName);

    @Query("UPDATE playlist SET lastPlayed = :timestamp WHERE id = :playlistId")
    void updateLastPlayed(String playlistId, long timestamp);

    /**
     * Full list query used by PlaylistCatalogueFragment.
     */
    @Query("SELECT p.*, (pp.playlistId IS NOT NULL) AS isPinned " +
       "FROM playlist p " +
       "LEFT JOIN pinned_playlist pp ON p.id = pp.playlistId " +
       "ORDER BY " +
       "CASE WHEN :sortMethod = 'ORDER_BY_RANDOM' THEN RANDOM() END ASC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_PINNED' THEN pp.playlistId IS NOT NULL END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_NAME' THEN p.name END ASC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_DATE' THEN p.created END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_SONGS' THEN p.songCount END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_LAST_PLAYED' THEN p.lastPlayed END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_LAST_UPDATED' THEN p.changed END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_BOTH' THEN MAX(p.lastPlayed, IFNULL(p.changed, 0)) END DESC")
    LiveData<List<Playlist>> getSortedPlaylists(String sortMethod);

    /**
     * Preview query used by HomeViewModel.
     * Includes a LIMIT clause to only return a subset (e.g., 5 items).
     */
    @Query("SELECT p.*, (pp.playlistId IS NOT NULL) AS isPinned " +
       "FROM playlist p " +
       "LEFT JOIN pinned_playlist pp ON p.id = pp.playlistId " +
       "ORDER BY " +
       "CASE WHEN :sortMethod = 'ORDER_BY_RANDOM' THEN RANDOM() END ASC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_PINNED' THEN pp.playlistId IS NOT NULL END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_NAME' THEN p.name END ASC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_DATE' THEN p.created END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_SONGS' THEN p.songCount END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_LAST_PLAYED' THEN p.lastPlayed END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_LAST_UPDATED' THEN p.changed END DESC, " +
       "CASE WHEN :sortMethod = 'ORDER_BY_BOTH' THEN MAX(p.lastPlayed, IFNULL(p.changed, 0)) END DESC " +
       "LIMIT :limit")
    LiveData<List<Playlist>> getSortedPlaylistsPreview(String sortMethod, int limit);

}