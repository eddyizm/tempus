package com.eddyizm.tempus.widget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;

import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.eddyizm.tempus.glide.CustomGlideRequest;
import com.eddyizm.tempus.R;

import androidx.media3.common.C;
import androidx.media3.session.MediaController;
import androidx.media3.session.SessionToken;

import com.eddyizm.tempus.service.MediaService;
import com.eddyizm.tempus.util.AssetLinkUtil;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import java.util.concurrent.ExecutionException;

public final class WidgetUpdateManager {

    private static final int WIDGET_SAFE_ART_SIZE = 512;

    public static void updateFromState(Context ctx,
                                       String title,
                                       String artist,
                                       String album,
                                       Bitmap art,
                                       boolean playing,
                                       boolean shuffleEnabled,
                                       int repeatMode,
                                       long positionMs,
                                       long durationMs,
                                       String songLink,
                                       String albumLink,
                                       String artistLink) {
        if (TextUtils.isEmpty(title)) title = ctx.getString(R.string.widget_not_playing);
        if (TextUtils.isEmpty(artist)) artist = ctx.getString(R.string.widget_placeholder_subtitle);
        if (TextUtils.isEmpty(album)) album = "";

        final TimingInfo timing = WidgetTimingViews.createTimingInfo(positionMs, durationMs);

        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider4x1.class));
        for (int id : ids) {
            android.widget.RemoteViews rv = choosePopulate(ctx, title, artist, album, art, playing,
                    timing.elapsedText, timing.totalText, timing.progress, shuffleEnabled, repeatMode, id);
            WidgetProvider.attachIntents(ctx, rv, id, songLink, albumLink, artistLink);
            mgr.updateAppWidget(id, rv);
        }
    }

    public static void pushNow(Context ctx) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        int[] ids = mgr.getAppWidgetIds(new ComponentName(ctx, WidgetProvider4x1.class));
        for (int id : ids) {
            android.widget.RemoteViews rv = chooseBuild(ctx, id);
            WidgetProvider.attachIntents(ctx, rv, id, null, null, null);
            mgr.updateAppWidget(id, rv);
        }
    }

    public static void updateFromState(Context ctx,
                                       String title,
                                       String artist,
                                       String album,
                                       String coverArtId,
                                       boolean playing,
                                       boolean shuffleEnabled,
                                       int repeatMode,
                                       long positionMs,
                                       long durationMs,
                                       String songLink,
                                       String albumLink,
                                       String artistLink,
                                       int controlsVersionAtStart,
                                       int timingVersionAtStart) {
        final Context appCtx = ctx.getApplicationContext();
        final String t = TextUtils.isEmpty(title) ? appCtx.getString(R.string.widget_not_playing) : title;
        final String a = TextUtils.isEmpty(artist) ? appCtx.getString(R.string.widget_placeholder_subtitle) : artist;
        final String alb = !TextUtils.isEmpty(album) ? album : "";
        final boolean p = playing;
        final boolean sh = shuffleEnabled;
        final int rep = repeatMode;
        final TimingInfo timing = WidgetTimingViews.createTimingInfo(positionMs, durationMs);
        final String songLinkFinal = songLink;
        final String albumLinkFinal = albumLink;
        final String artistLinkFinal = artistLink;
        final int generation = WidgetState.beginFullUpdate(controlsVersionAtStart, timingVersionAtStart);

        if (!TextUtils.isEmpty(coverArtId)) {
            CustomGlideRequest.loadAlbumArtBitmap(
                    appCtx,
                    coverArtId,
                    WIDGET_SAFE_ART_SIZE,
                    new CustomTarget<Bitmap>(WIDGET_SAFE_ART_SIZE, WIDGET_SAFE_ART_SIZE) {
                        @Override
                        public void onResourceReady(Bitmap resource, Transition<? super Bitmap> transition) {
                            if (!WidgetState.isCurrent(generation)) return;
                            WidgetState.setShownCover(coverArtId);
                            AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
                            int[] ids = mgr.getAppWidgetIds(new ComponentName(appCtx, WidgetProvider4x1.class));
                            for (int id : ids) {
                                android.widget.RemoteViews rv = choosePopulate(appCtx, t, a, alb, resource, p,
                                        timing.elapsedText, timing.totalText, timing.progress, sh, rep, id);
                                WidgetState.applyChangesSinceStart(appCtx, rv);
                                WidgetProvider.attachIntents(appCtx, rv, id, songLinkFinal, albumLinkFinal, artistLinkFinal);
                                mgr.updateAppWidget(id, rv);
                            }
                        }

                        @Override
                        public void onLoadFailed(Drawable errorDrawable) {
                            onLoadCleared(null);
                        }

                        @Override
                        public void onLoadCleared(Drawable placeholder) {
                            if (!WidgetState.isCurrent(generation)) return;
                            WidgetState.clearShownCover();
                            AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
                            int[] ids = mgr.getAppWidgetIds(new ComponentName(appCtx, WidgetProvider4x1.class));
                            for (int id : ids) {
                                android.widget.RemoteViews rv = choosePopulate(appCtx, t, a, alb, null, p,
                                        timing.elapsedText, timing.totalText, timing.progress, sh, rep, id);
                                WidgetState.applyChangesSinceStart(appCtx, rv);
                                WidgetProvider.attachIntents(appCtx, rv, id, songLinkFinal, albumLinkFinal, artistLinkFinal);
                                mgr.updateAppWidget(id, rv);
                            }
                        }
                    }
            );
        } else {
            WidgetState.clearShownCover();
            AppWidgetManager mgr = AppWidgetManager.getInstance(appCtx);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(appCtx, WidgetProvider4x1.class));
            for (int id : ids) {
                android.widget.RemoteViews rv = choosePopulate(appCtx, t, a, alb, null, p,
                        timing.elapsedText, timing.totalText, timing.progress, sh, rep, id);
                WidgetState.applyChangesSinceStart(appCtx, rv);
                WidgetProvider.attachIntents(appCtx, rv, id, songLinkFinal, albumLinkFinal, artistLinkFinal);
                mgr.updateAppWidget(id, rv);
            }
        }
    }

    public static void refreshFromController(Context ctx) {
        final Context appCtx = ctx.getApplicationContext();
        final int versionAtStart = WidgetState.version();
        SessionToken token = new SessionToken(appCtx, new ComponentName(appCtx, MediaService.class));
        ListenableFuture<MediaController> future = new MediaController.Builder(appCtx, token).buildAsync();
        future.addListener(() -> {
            try {
                if (!future.isDone()) return;
                MediaController c = future.get();
                androidx.media3.common.MediaItem mi = c.getCurrentMediaItem();
                String title = null, artist = null, album = null, coverId = null;
                String songLink = null, albumLink = null, artistLink = null;
                if (mi != null && mi.mediaMetadata != null) {
                    if (mi.mediaMetadata.title != null) title = mi.mediaMetadata.title.toString();
                    if (mi.mediaMetadata.artist != null)
                        artist = mi.mediaMetadata.artist.toString();
                    if (mi.mediaMetadata.albumTitle != null)
                        album = mi.mediaMetadata.albumTitle.toString();
                    if (mi.mediaMetadata.extras != null) {
                        Bundle extras = mi.mediaMetadata.extras;
                        if (title == null) title = mi.mediaMetadata.extras.getString("title");
                        if (artist == null) artist = mi.mediaMetadata.extras.getString("artist");
                        if (album == null) album = mi.mediaMetadata.extras.getString("album");
                        coverId = extras.getString("coverArtId");

                        songLink = extras.getString("assetLinkSong");
                        if (songLink == null) {
                            songLink = AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_SONG, extras.getString("id"));
                        }

                        albumLink = extras.getString("assetLinkAlbum");
                        if (albumLink == null) {
                            albumLink = AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ALBUM, extras.getString("albumId"));
                        }

                        artistLink = extras.getString("assetLinkArtist");
                        if (artistLink == null) {
                            artistLink = AssetLinkUtil.buildLink(AssetLinkUtil.TYPE_ARTIST, extras.getString("artistId"));
                        }
                    }
                }
                long position = c.getCurrentPosition();
                long duration = c.getDuration();
                if (position == C.TIME_UNSET) position = 0;
                if (duration == C.TIME_UNSET) duration = 0;
                updateFromState(appCtx,
                        title != null ? title : appCtx.getString(R.string.widget_not_playing),
                        artist != null ? artist : appCtx.getString(R.string.widget_placeholder_subtitle),
                        album,
                        coverId,
                        c.isPlaying(),
                        c.getShuffleModeEnabled(),
                        c.getRepeatMode(),
                        position,
                        duration,
                        songLink,
                        albumLink,
                        artistLink,
                        versionAtStart,
                        WidgetState.version());
                c.release();
            } catch (ExecutionException | InterruptedException ignored) {
            }
        }, MoreExecutors.directExecutor());
    }

    public static android.widget.RemoteViews chooseBuild(Context ctx, int appWidgetId) {
        LayoutSize size = resolveLayoutSize(ctx, appWidgetId);
        switch (size) {
            case MEDIUM:
                return WidgetViewsFactory.buildMedium(ctx);
            case LARGE:
                return WidgetViewsFactory.buildLarge(ctx);
            case EXPANDED:
                return WidgetViewsFactory.buildExpanded(ctx);
            case COMPACT:
            default:
                return WidgetViewsFactory.buildCompact(ctx);
        }
    }

    private static android.widget.RemoteViews choosePopulate(Context ctx,
                                                             String title,
                                                             String artist,
                                                             String album,
                                                             Bitmap art,
                                                             boolean playing,
                                                             String elapsedText,
                                                             String totalText,
                                                             int progress,
                                                             boolean shuffleEnabled,
                                                             int repeatMode,
                                                             int appWidgetId) {
        LayoutSize size = resolveLayoutSize(ctx, appWidgetId);
        switch (size) {
            case MEDIUM:
                return WidgetViewsFactory.populateMedium(ctx, title, artist, album, art, playing,
                        elapsedText, totalText, progress, shuffleEnabled, repeatMode);
            case LARGE:
                return WidgetViewsFactory.populateLarge(ctx, title, artist, album, art, playing,
                        elapsedText, totalText, progress, shuffleEnabled, repeatMode);
            case EXPANDED:
                return WidgetViewsFactory.populateExpanded(ctx, title, artist, album, art, playing,
                        elapsedText, totalText, progress, shuffleEnabled, repeatMode);
            case COMPACT:
            default:
                return WidgetViewsFactory.populateCompact(ctx, title, artist, album, art, playing,
                        elapsedText, totalText, progress, shuffleEnabled, repeatMode);
        }
    }

    private static LayoutSize resolveLayoutSize(Context ctx, int appWidgetId) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(ctx);
        android.os.Bundle opts = mgr.getAppWidgetOptions(appWidgetId);
        int minH = opts != null ? opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT) : 0;
        int expandedThreshold = ctx.getResources().getInteger(R.integer.widget_expanded_min_height_dp);
        int largeThreshold = ctx.getResources().getInteger(R.integer.widget_large_min_height_dp);
        int mediumThreshold = ctx.getResources().getInteger(R.integer.widget_medium_min_height_dp);
        if (minH >= expandedThreshold) return LayoutSize.EXPANDED;
        if (minH >= largeThreshold) return LayoutSize.LARGE;
        if (minH >= mediumThreshold) return LayoutSize.MEDIUM;
        return LayoutSize.COMPACT;
    }

    private enum LayoutSize {
        COMPACT,
        MEDIUM,
        LARGE,
        EXPANDED
    }

}