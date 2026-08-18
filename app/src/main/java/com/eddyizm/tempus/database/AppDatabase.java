package com.eddyizm.tempus.database;

import androidx.media3.common.util.UnstableApi;
import androidx.room.AutoMigration;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.eddyizm.tempus.App;
import com.eddyizm.tempus.database.converter.DateConverters;
import com.eddyizm.tempus.database.converter.StringListConverter;
import com.eddyizm.tempus.database.dao.ChronologyDao;
import com.eddyizm.tempus.database.dao.DownloadDao;
import com.eddyizm.tempus.database.dao.FavoriteDao;
import com.eddyizm.tempus.database.dao.InternetRadioStationDao;
import com.eddyizm.tempus.database.dao.LyricsDao;
import com.eddyizm.tempus.database.dao.PinnedPlaylistDao;
import com.eddyizm.tempus.database.dao.PlaylistDao;
import com.eddyizm.tempus.database.dao.PlaylistSongDao;
import com.eddyizm.tempus.database.dao.QueueDao;
import com.eddyizm.tempus.database.dao.RecentSearchDao;
import com.eddyizm.tempus.database.dao.ServerDao;
import com.eddyizm.tempus.database.dao.SessionMediaItemDao;
import com.eddyizm.tempus.model.Chronology;
import com.eddyizm.tempus.model.Download;
import com.eddyizm.tempus.model.Favorite;
import com.eddyizm.tempus.model.InternetRadioStationCache;
import com.eddyizm.tempus.model.LyricsCache;
import com.eddyizm.tempus.model.PinnedPlaylist;
import com.eddyizm.tempus.model.PlaylistSong;
import com.eddyizm.tempus.model.Queue;
import com.eddyizm.tempus.model.RecentSearch;
import com.eddyizm.tempus.model.Server;
import com.eddyizm.tempus.model.SessionMediaItem;
import com.eddyizm.tempus.subsonic.models.Playlist;

@UnstableApi
@Database(
        version = 21,
        entities = {
            Queue.class,
            Server.class,
            RecentSearch.class,
            Download.class,
            Chronology.class,
            Favorite.class,
            SessionMediaItem.class,
            Playlist.class,
            PinnedPlaylist.class,
            LyricsCache.class,
            InternetRadioStationCache.class,
            PlaylistSong.class,
        },
        autoMigrations = {
                @AutoMigration(from = 10, to = 11),
                @AutoMigration(from = 11, to = 12),
                @AutoMigration(from = 12, to = 13),
                @AutoMigration(from = 13, to = 14),
                @AutoMigration(from = 14, to = 15),
                @AutoMigration(from = 15, to = 16),
                @AutoMigration(from = 16, to = 17),
                @AutoMigration(from = 17, to = 18),
                @AutoMigration(from = 18, to = 19),
                @AutoMigration(from = 19, to = 20),
                @AutoMigration(from = 20, to = 21),
        }
)
@TypeConverters({DateConverters.class, StringListConverter.class})
public abstract class AppDatabase extends RoomDatabase {
    private final static String DB_NAME = "tempo_db";
    private static AppDatabase instance;

    public static synchronized AppDatabase getInstance() {
        if (instance == null) {
            instance = Room.databaseBuilder(App.getContext(), AppDatabase.class, DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
        }

        return instance;
    }

    public abstract QueueDao queueDao();

    public abstract ServerDao serverDao();

    public abstract RecentSearchDao recentSearchDao();

    public abstract DownloadDao downloadDao();

    public abstract ChronologyDao chronologyDao();

    public abstract FavoriteDao favoriteDao();

    public abstract SessionMediaItemDao sessionMediaItemDao();

    public abstract PlaylistDao playlistDao();

    public abstract PinnedPlaylistDao pinnedPlaylistDao();

    public abstract PlaylistSongDao playlistSongDao();

    public abstract LyricsDao lyricsDao();

    public abstract InternetRadioStationDao internetRadioStationDao();
}
