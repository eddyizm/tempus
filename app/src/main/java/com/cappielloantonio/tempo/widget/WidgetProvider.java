package com.cappielloantonio.tempo.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import com.cappielloantonio.tempo.R;
import android.app.TaskStackBuilder;
import android.app.PendingIntent;
import com.cappielloantonio.tempo.ui.activity.MainActivity;
import android.util.Log;

public class WidgetProvider extends AppWidgetProvider {
    private static final String TAG = "TempoWidget";
    public static final String ACT_PLAY_PAUSE = "tempo.widget.PLAY_PAUSE";
    public static final String ACT_NEXT = "tempo.widget.NEXT";
    public static final String ACT_PREV = "tempo.widget.PREV";
    public static final String ACT_TOGGLE_SHUFFLE = "tempo.widget.SHUFFLE";
    public static final String ACT_CYCLE_REPEAT = "tempo.widget.REPEAT";

    @Override public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) {
            RemoteViews rv = WidgetUpdateManager.chooseBuild(ctx, id);
            attachIntents(ctx, rv, id);
            mgr.updateAppWidget(id, rv);
        }
    }

    @Override public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        String a = intent.getAction();
        Log.d(TAG, "onReceive action=" + a);
        if (ACT_PLAY_PAUSE.equals(a) || ACT_NEXT.equals(a) || ACT_PREV.equals(a)
                || ACT_TOGGLE_SHUFFLE.equals(a) || ACT_CYCLE_REPEAT.equals(a)) {
            WidgetActions.dispatchToMediaSession(ctx, a);
        } else if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a)) {
            WidgetUpdateManager.refreshFromController(ctx);
        }
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager, int appWidgetId, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions);
        RemoteViews rv = WidgetUpdateManager.chooseBuild(context, appWidgetId);
        attachIntents(context, rv, appWidgetId);
        appWidgetManager.updateAppWidget(appWidgetId, rv);
        WidgetUpdateManager.refreshFromController(context);
    }

    public static void attachIntents(Context ctx, RemoteViews rv) {
        attachIntents(ctx, rv, 0);
    }

    public static void attachIntents(Context ctx, RemoteViews rv, int requestCodeBase) {
        PendingIntent playPause = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 0,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_PLAY_PAUSE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent next = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 1,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_NEXT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent prev = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 2,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_PREV),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent shuffle = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 3,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_TOGGLE_SHUFFLE),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        PendingIntent repeat = PendingIntent.getBroadcast(
                ctx,
                requestCodeBase + 4,
                new Intent(ctx, WidgetProvider4x1.class).setAction(ACT_CYCLE_REPEAT),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );

        rv.setOnClickPendingIntent(R.id.btn_play_pause, playPause);
        rv.setOnClickPendingIntent(R.id.btn_next, next);
        rv.setOnClickPendingIntent(R.id.btn_prev, prev);
        rv.setOnClickPendingIntent(R.id.btn_shuffle, shuffle);
        rv.setOnClickPendingIntent(R.id.btn_repeat, repeat);

        PendingIntent launch = TaskStackBuilder.create(ctx)
                .addNextIntentWithParentStack(new Intent(ctx, MainActivity.class))
                .getPendingIntent(requestCodeBase + 10, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        rv.setOnClickPendingIntent(R.id.root, launch);
    }
}
