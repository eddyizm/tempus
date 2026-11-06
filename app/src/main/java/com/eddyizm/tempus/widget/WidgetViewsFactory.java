package com.eddyizm.tempus.widget;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import androidx.media3.common.Player;

import com.eddyizm.tempus.R;

public final class WidgetViewsFactory {

    static final int PROGRESS_MAX = 1000;
    private static final float ALBUM_ART_CORNER_RADIUS_DP = 6f;

    private WidgetViewsFactory() {
    }

    public static RemoteViews buildCompact(Context ctx) {
        return build(ctx, R.layout.widget_layout_compact, false, false);
    }

    public static RemoteViews buildMedium(Context ctx) {
        return build(ctx, R.layout.widget_layout_medium, false, false);
    }

    public static RemoteViews buildLarge(Context ctx) {
        return build(ctx, R.layout.widget_layout_large_short, true, true);
    }

    public static RemoteViews buildExpanded(Context ctx) {
        return build(ctx, R.layout.widget_layout_large, true, true);
    }

    private static RemoteViews build(Context ctx,
                                     int layoutRes,
                                     boolean showAlbum,
                                     boolean showSecondaryControls) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
        rv.setTextViewText(R.id.title, ctx.getString(R.string.widget_not_playing));
        rv.setTextViewText(R.id.subtitle, ctx.getString(R.string.widget_placeholder_subtitle));
        rv.setTextViewText(R.id.album, "");
        rv.setViewVisibility(R.id.album, showAlbum ? View.INVISIBLE : View.GONE);
        WidgetTimingViews.apply(ctx, rv, null, null, 0);
        rv.setImageViewResource(R.id.btn_play_pause, R.drawable.ic_play);
        rv.setImageViewResource(R.id.album_art, R.drawable.ic_splash_logo);
        applySecondaryControlsDefaults(ctx, rv, showSecondaryControls);
        return rv;
    }

    private static void applySecondaryControlsDefaults(Context ctx,
                                                       RemoteViews rv,
                                                       boolean show) {
        int visibility = show ? View.VISIBLE : View.GONE;
        rv.setViewVisibility(R.id.controls_secondary, visibility);
        rv.setViewVisibility(R.id.btn_shuffle, visibility);
        rv.setViewVisibility(R.id.btn_repeat, visibility);
        if (show) {
            WidgetSecondaryControlsViews.apply(ctx, rv, false, Player.REPEAT_MODE_OFF);
        }
    }

    public static RemoteViews populateCompact(Context ctx,
                                              String title,
                                              String subtitle,
                                              String album,
                                              Bitmap art,
                                              boolean playing,
                                              String elapsedText,
                                              String totalText,
                                              int progress,
                                              boolean shuffleEnabled,
                                              int repeatMode) {
        return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
                progress, R.layout.widget_layout_compact, false, false, shuffleEnabled, repeatMode);
    }

    public static RemoteViews populateMedium(Context ctx,
                                             String title,
                                             String subtitle,
                                             String album,
                                             Bitmap art,
                                             boolean playing,
                                             String elapsedText,
                                             String totalText,
                                             int progress,
                                             boolean shuffleEnabled,
                                             int repeatMode) {
        return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
                progress, R.layout.widget_layout_medium, true, true, shuffleEnabled, repeatMode);
    }

    public static RemoteViews populateLarge(Context ctx,
                                            String title,
                                            String subtitle,
                                            String album,
                                            Bitmap art,
                                            boolean playing,
                                            String elapsedText,
                                            String totalText,
                                            int progress,
                                            boolean shuffleEnabled,
                                            int repeatMode) {
        return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
                progress, R.layout.widget_layout_large_short, true, true, shuffleEnabled, repeatMode);
    }

    public static RemoteViews populateExpanded(Context ctx,
                                               String title,
                                               String subtitle,
                                               String album,
                                               Bitmap art,
                                               boolean playing,
                                               String elapsedText,
                                               String totalText,
                                               int progress,
                                               boolean shuffleEnabled,
                                               int repeatMode) {
        return populateWithLayout(ctx, title, subtitle, album, art, playing, elapsedText, totalText,
                progress, R.layout.widget_layout_large, true, true, shuffleEnabled, repeatMode);
    }

    private static RemoteViews populateWithLayout(Context ctx,
                                                  String title,
                                                  String subtitle,
                                                  String album,
                                                  Bitmap art,
                                                  boolean playing,
                                                  String elapsedText,
                                                  String totalText,
                                                  int progress,
                                                  int layoutRes,
                                                  boolean showAlbum,
                                                  boolean showSecondaryControls,
                                                  boolean shuffleEnabled,
                                                  int repeatMode) {
        RemoteViews rv = new RemoteViews(ctx.getPackageName(), layoutRes);
        rv.setTextViewText(R.id.title, title);
        rv.setTextViewText(R.id.subtitle, subtitle);

        if (showAlbum && !TextUtils.isEmpty(album)) {
            rv.setTextViewText(R.id.album, album);
            rv.setViewVisibility(R.id.album, View.VISIBLE);
        } else {
            rv.setTextViewText(R.id.album, "");
            rv.setViewVisibility(R.id.album, View.GONE);
        }

        if (art != null) {
            Bitmap rounded = maybeRoundBitmap(ctx, art);
            rv.setImageViewBitmap(R.id.album_art, rounded != null ? rounded : art);
        } else {
            rv.setImageViewResource(R.id.album_art, R.drawable.ic_splash_logo);
        }

        rv.setImageViewResource(R.id.btn_play_pause,
                playing ? R.drawable.ic_pause : R.drawable.ic_play);

        WidgetTimingViews.apply(ctx, rv, elapsedText, totalText, progress);

        applySecondaryControls(ctx, rv, showSecondaryControls, shuffleEnabled, repeatMode);

        return rv;
    }

    private static Bitmap maybeRoundBitmap(Context ctx, Bitmap source) {
        if (source == null || source.isRecycled()) {
            return null;
        }

        try {
            int width = source.getWidth();
            int height = source.getHeight();
            if (width <= 0 || height <= 0) {
                return null;
            }

            Bitmap output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(output);

            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setShader(new BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));

            float radiusPx = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    ALBUM_ART_CORNER_RADIUS_DP,
                    ctx.getResources().getDisplayMetrics());
            float maxRadius = Math.min(width, height) / 2f;
            float safeRadius = Math.min(radiusPx, maxRadius);

            canvas.drawRoundRect(new RectF(0f, 0f, width, height), safeRadius, safeRadius, paint);
            return output;
        } catch (RuntimeException | OutOfMemoryError e) {
            android.util.Log.w("TempoWidget", "Failed to round album art", e);
            return null;
        }
    }

    private static void applySecondaryControls(Context ctx,
                                               RemoteViews rv,
                                               boolean show,
                                               boolean shuffleEnabled,
                                               int repeatMode) {
        if (!show) {
            rv.setViewVisibility(R.id.controls_secondary, View.GONE);
            rv.setViewVisibility(R.id.btn_shuffle, View.GONE);
            rv.setViewVisibility(R.id.btn_repeat, View.GONE);
            return;
        }

        rv.setViewVisibility(R.id.controls_secondary, View.VISIBLE);
        rv.setViewVisibility(R.id.btn_shuffle, View.VISIBLE);
        rv.setViewVisibility(R.id.btn_repeat, View.VISIBLE);
        WidgetSecondaryControlsViews.apply(ctx, rv, shuffleEnabled, repeatMode);
    }
}
