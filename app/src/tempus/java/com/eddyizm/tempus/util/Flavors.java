package com.eddyizm.tempus.util;

import android.content.Context;
import android.view.Menu;

import androidx.core.content.ContextCompat;

import com.eddyizm.tempus.R;
import com.google.android.gms.cast.framework.CastButtonFactory;
import com.google.android.gms.cast.framework.CastContext;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

public class Flavors {
    @SuppressWarnings("deprecation")
    public static void initializeCastContext(Context context) {
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS)
            CastContext.getSharedInstance(context, ContextCompat.getMainExecutor(context));
    }

    public static void setUpRouteButton(Context context, Menu menu) {
        CastButtonFactory.setUpMediaRouteButton(context, menu, R.id.media_route_menu_item);
    }
}
