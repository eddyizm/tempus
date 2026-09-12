package com.eddyizm.tempus.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.eddyizm.tempus.model.Download;

import java.util.List;

@Dao
public interface DownloadDao {
    @Query("SELECT * FROM download WHERE download_state = 1 ORDER BY IFNULL(NULLIF(album_artist, ''), album), album, album_id, disc_number, track ASC")
    LiveData<List<Download>> getAll();

    @Query("SELECT * FROM download WHERE download_state = 1 ORDER BY IFNULL(NULLIF(album_artist, ''), album), album, album_id, disc_number, track ASC")
    List<Download> getAllSync();

    @Query("SELECT * FROM download WHERE id = :id")
    Download getOne(String id);

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

    @Query("UPDATE download SET download_state = 1 WHERE id = :id")
    void update(String id);

    @Query("DELETE FROM download WHERE id = :id")
    void delete(String id);

    @Query("DELETE FROM download WHERE id IN (:ids)")
    void deleteByIds(List<String> ids);

    @Query("DELETE FROM download")
    void deleteAll();
}