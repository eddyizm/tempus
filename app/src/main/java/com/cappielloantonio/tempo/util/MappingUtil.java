package com.cappielloantonio.tempo.util;

import android.content.ContentResolver;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Base64;

import androidx.annotation.OptIn;
import androidx.lifecycle.LifecycleOwner;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.common.HeartRating;

import com.cappielloantonio.tempo.App;
import com.cappielloantonio.tempo.glide.CustomGlideRequest;
import com.cappielloantonio.tempo.model.Download;
import com.cappielloantonio.tempo.provider.AlbumArtContentProvider;
import com.cappielloantonio.tempo.repository.DownloadRepository;
import com.cappielloantonio.tempo.subsonic.models.Child;
import com.cappielloantonio.tempo.subsonic.models.InternetRadioStation;
import com.cappielloantonio.tempo.subsonic.models.PodcastEpisode;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.nio.charset.StandardCharsets;

@OptIn(markerClass = UnstableApi.class)
public class MappingUtil {
    public static List<MediaItem> mapMediaItems(List<Child> items) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            mediaItems.add(mapMediaItem(items.get(i)));
        }

        return mediaItems;
    }

    private static final String TAG = "MappingUtil";

    public static MediaItem mapMediaItem(Child media) {
        try {
            Uri uri = getUri(media);
            String coverArtId = media.getCoverArtId();
            Uri artworkUri = null;

            if (coverArtId != null) {
                artworkUri = AlbumArtContentProvider.contentUri(coverArtId);
            }

            Bundle bundle = new Bundle();
            bundle.putString("id", media.getId());
            bundle.putString("parentId", media.getParentId());
            bundle.putBoolean("isDir", media.isDir());
            
            bundle.putString("title", media.getTitle());
            bundle.putString("album", media.getAlbum());
            bundle.putString("artist", media.getArtist());

            bundle.putInt("track", media.getTrack() != null ? media.getTrack() : 0);
            bundle.putInt("year", media.getYear() != null ? media.getYear() : 0);
            bundle.putString("genre", media.getGenre());
            bundle.putString("coverArtId", coverArtId);
            bundle.putLong("size", media.getSize() != null ? media.getSize() : 0);
            bundle.putString("contentType", media.getContentType());
            bundle.putString("suffix", media.getSuffix());
            bundle.putString("transcodedContentType", media.getTranscodedContentType());
            bundle.putString("transcodedSuffix", media.getTranscodedSuffix());
            bundle.putInt("duration", media.getDuration() != null ? media.getDuration() : 0);
            bundle.putInt("bitrate", media.getBitrate() != null ? media.getBitrate() : 0);
            bundle.putInt("samplingRate", media.getSamplingRate() != null ? media.getSamplingRate() : 0);
            bundle.putInt("bitDepth", media.getBitDepth() != null ? media.getBitDepth() : 0);
            bundle.putString("path", media.getPath());
            bundle.putBoolean("isVideo", media.isVideo());
            bundle.putInt("userRating", media.getUserRating() != null ? media.getUserRating() : 0);
            bundle.putDouble("averageRating", media.getAverageRating() != null ? media.getAverageRating() : 0);
            bundle.putLong("playCount", media.getPlayCount() != null ? media.getPlayCount() : 0);
            bundle.putInt("discNumber", media.getDiscNumber() != null ? media.getDiscNumber() : 0);
            bundle.putLong("created", media.getCreated() != null ? media.getCreated().getTime() : 0);
            bundle.putLong("starred", media.getStarred() != null ? media.getStarred().getTime() : 0);
            bundle.putString("albumId", media.getAlbumId());
            bundle.putString("artistId", media.getArtistId());
            bundle.putString("type", Constants.MEDIA_TYPE_MUSIC);
            bundle.putLong("bookmarkPosition", media.getBookmarkPosition() != null ? media.getBookmarkPosition() : 0);
            bundle.putInt("originalWidth", media.getOriginalWidth() != null ? media.getOriginalWidth() : 0);
            bundle.putInt("originalHeight", media.getOriginalHeight() != null ? media.getOriginalHeight() : 0);
            bundle.putString("uri", uri.toString());
            
            bundle.putString("assetLinkSong", media.getId() != null ? AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_SONG, media.getId()) : null);
            bundle.putString("assetLinkAlbum", media.getAlbumId() != null ? AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ALBUM, media.getAlbumId()) : null);
            bundle.putString("assetLinkArtist", media.getArtistId() != null ? AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ARTIST, media.getArtistId()) : null);
            bundle.putString("assetLinkGenre", AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_GENRE, media.getGenre()));
            Integer year = media.getYear();
            bundle.putString("assetLinkYear", year != null && year != 0 ? AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_YEAR, String.valueOf(year)) : null);

            return new MediaItem.Builder()
                    .setMediaId(media.getId())
                    .setMediaMetadata(
                            new MediaMetadata.Builder()
                                    .setTitle(media.getTitle())
                                    .setTrackNumber(media.getTrack() != null ? media.getTrack() : 0)
                                    .setDiscNumber(media.getDiscNumber() != null ? media.getDiscNumber() : 0)
                                    .setReleaseYear(media.getYear() != null ? media.getYear() : 0)
                                    .setAlbumTitle(media.getAlbum())
                                    .setArtist(media.getArtist())
                                    .setArtworkUri(artworkUri)
                                    .setUserRating(new HeartRating(media.getStarred() != null))
                                    .setSupportedCommands(
                                        ImmutableList.of(
                                                Constants.CUSTOM_COMMAND_TOGGLE_HEART_ON,
                                                Constants.CUSTOM_COMMAND_TOGGLE_HEART_OFF
                                        )
                                    )
                                    .setExtras(bundle)
                                    .setIsBrowsable(false)
                                    .setIsPlayable(true)
                                    .build()
                    )
                    .setRequestMetadata(
                            new MediaItem.RequestMetadata.Builder()
                                    .setMediaUri(uri)
                                    .setExtras(bundle)
                                    .build()
                    )
                    .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                    .setUri(uri)
                    .build();

        } catch (Exception e) {
            String id = media != null ? media.getId() : "NULL_MEDIA_OBJECT";
            String title = media != null ? media.getTitle() : "N/A";
            
            Log.e(TAG, "Instant Mix CRASH! Failed to map song to MediaItem. " +
                       "Problematic Song ID: " + id + 
                       ", Title: " + title + 
                       ". Inspect this song's Subsonic data for missing fields.", e);
            throw new RuntimeException("Mapping failed for song ID: " + id, e);
        }
    }

    public static MediaItem mapMediaItem(MediaItem old) {
        String mediaId = null;
        if (old.requestMetadata.extras != null)
            mediaId = old.requestMetadata.extras.getString("id");

        if (mediaId != null && DownloadUtil.getDownloadTracker(App.getContext()).isDownloaded(mediaId)) {
            return old;
        }
        Uri uri = old.requestMetadata.mediaUri == null ? null : MusicUtil.updateStreamUri(old.requestMetadata.mediaUri);
        return new MediaItem.Builder()
                .setMediaId(old.mediaId)
                .setMediaMetadata(old.mediaMetadata)
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(old.requestMetadata.extras)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }
    public static List<MediaItem> mapMediaItems(List<Child> items, String parentId) {
        ArrayList<MediaItem> mediaItems = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            mediaItems.add(mapMediaItem(items.get(i), parentId));
        }

        return mediaItems;
    }
    public static MediaItem mapMediaItem(Child item, String parentId) {
        MediaItem mediaItem = mapMediaItem(item);

        Bundle extras = mediaItem.mediaMetadata.extras != null
                ? new Bundle(mediaItem.mediaMetadata.extras)
                : new Bundle();

        extras.putString("parent_id", parentId);

        MediaMetadata metadata = mediaItem.mediaMetadata
                .buildUpon()
                .setExtras(extras)
                .build();

        return mediaItem.buildUpon()
                .setMediaId(item.getId())
                .setMediaMetadata(metadata)
                .build();
    }

    public static List<MediaItem> mapDownloads(List<Child> items) {
        ArrayList<MediaItem> downloads = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            downloads.add(mapDownload(items.get(i)));
        }

        return downloads;
    }

    public static MediaItem mapDownload(Child media) {

        Bundle bundle = new Bundle();
        bundle.putInt("samplingRate", media.getSamplingRate() != null ? media.getSamplingRate() : 0);
        bundle.putInt("bitDepth", media.getBitDepth() != null ? media.getBitDepth() : 0);

        return new MediaItem.Builder()
                .setMediaId(media.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(media.getTitle())
                                .setTrackNumber(media.getTrack() != null ? media.getTrack() : 0)
                                .setDiscNumber(media.getDiscNumber() != null ? media.getDiscNumber() : 0)
                                .setReleaseYear(media.getYear() != null ? media.getYear() : 0)
                                .setAlbumTitle(media.getAlbum())
                                .setArtist(media.getArtist())
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setExtras(bundle)
                                .setMediaUri(Preferences.preferTranscodedDownload() ? MusicUtil.getTranscodedDownloadUri(media.getId()) : MusicUtil.getDownloadUri(media.getId()))
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(Preferences.preferTranscodedDownload() ? MusicUtil.getTranscodedDownloadUri(media.getId()) : MusicUtil.getDownloadUri(media.getId()))
                .build();
    }

    public static MediaItem mapInternetRadioStation(InternetRadioStation internetRadioStation) {
        Uri uri = Uri.parse(internetRadioStation.getStreamUrl());
        Uri artworkUri = null;
        String homePageUrl = internetRadioStation.getHomePageUrl();
        String coverArtId = null;

        if (homePageUrl != null && !homePageUrl.isEmpty() && MusicUtil.isImageUrl(homePageUrl)) {
                String encodedUrl = Base64.encodeToString(homePageUrl.getBytes(StandardCharsets.UTF_8),
                                Base64.URL_SAFE | Base64.NO_WRAP);
                coverArtId = "ir_" + encodedUrl;
                artworkUri = AlbumArtContentProvider.contentUri(coverArtId);
        }

        Bundle bundle = new Bundle();
        bundle.putString("id", internetRadioStation.getId());
        bundle.putString("title", internetRadioStation.getName());
        bundle.putString("stationName", internetRadioStation.getName());
        bundle.putString("uri", uri.toString());
        bundle.putString("type", Constants.MEDIA_TYPE_RADIO);
        bundle.putString("coverArtId", coverArtId);
        if (homePageUrl != null) {
                bundle.putString("homepageUrl", homePageUrl);
        }

        return new MediaItem.Builder()
                .setMediaId(internetRadioStation.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(internetRadioStation.getName())
                                .setArtworkUri(artworkUri)
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                // .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();
    }

    public static MediaItem mapMediaItem(PodcastEpisode podcastEpisode) {
        Uri uri = getUri(podcastEpisode);
        Uri artworkUri = AlbumArtContentProvider.contentUri(podcastEpisode.getCoverArtId());

        Bundle bundle = new Bundle();
        bundle.putString("id", podcastEpisode.getId());
        bundle.putString("parentId", podcastEpisode.getParentId());
        bundle.putBoolean("isDir", podcastEpisode.isDir());
        bundle.putString("title", podcastEpisode.getTitle());
        bundle.putString("album", podcastEpisode.getAlbum());
        bundle.putString("artist", podcastEpisode.getArtist());
        bundle.putInt("year", podcastEpisode.getYear() != null ? podcastEpisode.getYear() : 0);
        bundle.putString("coverArtId", podcastEpisode.getCoverArtId());
        bundle.putLong("size", podcastEpisode.getSize() != null ? podcastEpisode.getSize() : 0);
        bundle.putString("contentType", podcastEpisode.getContentType());
        bundle.putString("suffix", podcastEpisode.getSuffix());
        bundle.putInt("duration", podcastEpisode.getDuration() != null ? podcastEpisode.getDuration() : 0);
        bundle.putInt("bitrate", podcastEpisode.getBitrate() != null ? podcastEpisode.getBitrate() : 0);
        bundle.putBoolean("isVideo", podcastEpisode.isVideo());
        bundle.putLong("created", podcastEpisode.getCreated() != null ? podcastEpisode.getCreated().getTime() : 0);
        bundle.putString("artistId", podcastEpisode.getArtistId());
        bundle.putString("description", podcastEpisode.getDescription());
        bundle.putString("type", Constants.MEDIA_TYPE_PODCAST);
        bundle.putString("uri", uri.toString());

        MediaItem item = new MediaItem.Builder()
                .setMediaId(podcastEpisode.getId())
                .setMediaMetadata(
                        new MediaMetadata.Builder()
                                .setTitle(podcastEpisode.getTitle())
                                .setReleaseYear(podcastEpisode.getYear() != null ? podcastEpisode.getYear() : 0)
                                .setAlbumTitle(podcastEpisode.getAlbum())
                                .setArtist(podcastEpisode.getArtist())
                                .setArtworkUri(artworkUri)
                                .setExtras(bundle)
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .build()
                )
                .setRequestMetadata(
                        new MediaItem.RequestMetadata.Builder()
                                .setMediaUri(uri)
                                .setExtras(bundle)
                                .build()
                )
                .setMimeType(MimeTypes.BASE_TYPE_AUDIO)
                .setUri(uri)
                .build();

        return item;
    }

    public static Child mapToChild(MediaItem item) {
        if (item == null) return null;

        Bundle extras = item.mediaMetadata.extras;
        String id = (extras != null && extras.getString("id") != null) ? extras.getString("id") : item.mediaId;

        if (id == null) return null;

        Child child = new Child(id);

        if (extras != null) {
            child.setParentId(extras.getString("parentId"));
            child.setDir(extras.getBoolean("isDir"));
            child.setTitle(extras.getString("title"));
            child.setAlbum(extras.getString("album"));
            child.setArtist(extras.getString("artist"));
            child.setGenre(extras.getString("genre"));
            child.setCoverArtId(extras.getString("coverArtId"));
            child.setContentType(extras.getString("contentType"));
            child.setSuffix(extras.getString("suffix"));
            child.setTranscodedContentType(extras.getString("transcodedContentType"));
            child.setTranscodedSuffix(extras.getString("transcodedSuffix"));
            child.setPath(extras.getString("path"));
            child.setVideo(extras.getBoolean("isVideo"));
            child.setAlbumId(extras.getString("albumId"));
            child.setArtistId(extras.getString("artistId"));
            child.setType(extras.getString("type"));

            if (extras.containsKey("track")) child.setTrack(extras.getInt("track"));
            if (extras.containsKey("year")) child.setYear(extras.getInt("year"));
            if (extras.containsKey("size")) child.setSize(extras.getLong("size"));
            if (extras.containsKey("duration")) child.setDuration(extras.getInt("duration"));
            if (extras.containsKey("bitrate")) child.setBitrate(extras.getInt("bitrate"));
            if (extras.containsKey("samplingRate")) child.setSamplingRate(extras.getInt("samplingRate"));
            if (extras.containsKey("bitDepth")) child.setBitDepth(extras.getInt("bitDepth"));
            if (extras.containsKey("userRating")) child.setUserRating(extras.getInt("userRating"));
            if (extras.containsKey("averageRating")) child.setAverageRating(extras.getDouble("averageRating"));
            if (extras.containsKey("playCount")) child.setPlayCount(extras.getLong("playCount"));
            if (extras.containsKey("discNumber")) child.setDiscNumber(extras.getInt("discNumber"));
            if (extras.containsKey("bookmarkPosition")) child.setBookmarkPosition(extras.getLong("bookmarkPosition"));
            if (extras.containsKey("originalWidth")) child.setOriginalWidth(extras.getInt("originalWidth"));
            if (extras.containsKey("originalHeight")) child.setOriginalHeight(extras.getInt("originalHeight"));

            long createdTime = extras.getLong("created", 0);
            if (createdTime != 0) child.setCreated(new Date(createdTime));

            long starredTime = extras.getLong("starred", 0);
            if (starredTime != 0) child.setStarred(new Date(starredTime));
        }

        // Fallbacks
        if (child.getTitle() == null && item.mediaMetadata.title != null) {
            child.setTitle(item.mediaMetadata.title.toString());
        }
        if (child.getArtist() == null && item.mediaMetadata.artist != null) {
            child.setArtist(item.mediaMetadata.artist.toString());
        }
        if (child.getAlbum() == null && item.mediaMetadata.albumTitle != null) {
            child.setAlbum(item.mediaMetadata.albumTitle.toString());
        }
        if (child.getTrack() == null && item.mediaMetadata.trackNumber != null) {
            child.setTrack(item.mediaMetadata.trackNumber);
        }
        if (child.getDiscNumber() == null && item.mediaMetadata.discNumber != null) {
            child.setDiscNumber(item.mediaMetadata.discNumber);
        }
        if (child.getYear() == null && item.mediaMetadata.releaseYear != null) {
            child.setYear(item.mediaMetadata.releaseYear);
        }

        return child;
    }

    private static Uri getUri(Child media) {
        // Check if it's in our local SQL Database
        DownloadRepository repo = new DownloadRepository();
        Download localDownload = repo.getDownload(media.getId());

        if (localDownload != null && localDownload.getDownloadUri() != null && !localDownload.getDownloadUri().isEmpty()) {
            Log.d(TAG, "Playing local file for: " + media.getTitle());
            return Uri.parse(localDownload.getDownloadUri());
        }

        // Legacy check for external directory, i think this was broken/buggy
        if (Preferences.getDownloadDirectoryUri() != null) {
            Uri local = ExternalAudioReader.getUri(media);
            if (local != null) return local;
        }

        // Fallback to streaming
        Log.d(TAG, "No local file found. Streaming: " + media.getTitle());
        return MusicUtil.getStreamUri(media.getId());
    }

    private static Uri getUri(PodcastEpisode podcastEpisode) {
        if (Preferences.getDownloadDirectoryUri() != null) {
            Uri local = ExternalAudioReader.getUri(podcastEpisode);
            return local != null ? local : MusicUtil.getStreamUri(podcastEpisode.getStreamId());
        }
        return DownloadUtil.getDownloadTracker(App.getContext()).isDownloaded(podcastEpisode.getStreamId())
                ? getDownloadUri(podcastEpisode.getStreamId())
                : MusicUtil.getStreamUri(podcastEpisode.getStreamId());
    }

    private static Uri getDownloadUri(String id) {
        Download download = new DownloadRepository().getDownload(id);
        return download != null && !download.getDownloadUri().isEmpty() ? Uri.parse(download.getDownloadUri()) : MusicUtil.getDownloadUri(id);
    }

    public static void observeExternalAudioRefresh(LifecycleOwner owner, Runnable onRefresh) {
        if (owner == null || onRefresh == null) {
            return;
        }
        ExternalAudioReader.getRefreshEvents().observe(owner, event -> onRefresh.run());
    }
}