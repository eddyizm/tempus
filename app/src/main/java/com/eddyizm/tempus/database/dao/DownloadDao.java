package com.eddyizm.tempus.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;

import com.eddyizm.tempus.model.Download;

import java.util.List;

@Dao
public interface DownloadDao {
    // SQLite before 3.32 (Android 10 and lower) refuses more than 999 bound arguments.
    int MAX_IDS_PER_QUERY = 500;

    @Query("SELECT * FROM download WHERE download_state = 1 ORDER BY IFNULL(NULLIF(album_artist, ''), album), album, album_id, disc_number, track ASC")
    LiveData<List<Download>> getAll();

    @Query("SELECT * FROM download WHERE download_state = 1 ORDER BY IFNULL(NULLIF(album_artist, ''), album), album, album_id, disc_number, track ASC")
    List<Download> getAllSync();

    @Query("SELECT * FROM download WHERE id = :id")
    Download getOne(String id);

    // State 0 means asked for and not finished: a download still under way looks the same as one that never will.
    @Query("SELECT id FROM download WHERE download_state = 0")
    List<String> getPendingIds();

    @Query("SELECT COUNT(*) FROM download WHERE download_state = 1")
    int countDownloaded();

    @Query("SELECT DISTINCT album_id FROM download WHERE NULLIF(album_artist, '') IS NULL AND NULLIF(album_id, '') IS NOT NULL AND download_state = 1")
    List<String> getAlbumIdsWithoutAlbumArtist();

    @Query("SELECT COUNT(*) FROM download WHERE album_id = :albumId AND NULLIF(album_artist, '') IS NULL AND NULLIF(album, '') <> :albumTitle AND download_state = 1")
    int countUnfilledUnderAnotherTitle(String albumId, String albumTitle);

    // No download_state here on purpose. A song still downloading has no album artist yet, and
    // skipping it would leave it to finish later and sort away from its own album.
    @Query("UPDATE download SET album_artist = :albumArtist WHERE album_id = :albumId AND album = :albumTitle AND NULLIF(album_artist, '') IS NULL")
    int setAlbumArtist(String albumId, String albumTitle, String albumArtist);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Download download);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Download> downloads);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllKeepingExisting(List<Download> downloads);

    /**
     * Asking again for a song already on the device must not drop it back to state 0, which
     * hides it from the Downloads screen until Media3 finishes the new request, or for good
     * when that request never runs (Wi-Fi only on mobile data, process killed).
     */
    @Transaction
    default void insertKeepingCompleted(Download download) {
        Download existing = getOne(download.getId());
        if (existing != null && existing.getDownloadState() == 1) download.setDownloadState(1);
        insert(download);
    }

    @Query("UPDATE download SET download_state = 1 WHERE id = :id")
    void update(String id);

    @Query("UPDATE download SET download_state = 1 WHERE id IN (:ids)")
    void updateByIds(List<String> ids);

    @Transaction
    default void updateByIdsInChunks(List<String> ids) {
        for (int from = 0; from < ids.size(); from += MAX_IDS_PER_QUERY) {
            updateByIds(ids.subList(from, Math.min(from + MAX_IDS_PER_QUERY, ids.size())));
        }
    }

    @Query("DELETE FROM download WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM download WHERE id = :id AND download_state = 0")
    void deleteFailed(String id);

    @Query("DELETE FROM download WHERE id IN (:ids)")
    void deleteByIds(List<String> ids);

    @Transaction
    default void deleteByIdsInChunks(List<String> ids) {
        for (int from = 0; from < ids.size(); from += MAX_IDS_PER_QUERY) {
            deleteByIds(ids.subList(from, Math.min(from + MAX_IDS_PER_QUERY, ids.size())));
        }
    }

    @Query("DELETE FROM download")
    void deleteAll();
}