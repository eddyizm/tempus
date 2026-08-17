package com.eddyizm.tempus.util;

import android.content.Context;

import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

/**
 * Flavor-agnostic helper. Uses reflection to initialize CastContext only when Play Services
 * and Cast framework are available, avoiding compile-time dependency for degoogled builds.
 */
public class Flavors {
    @SuppressWarnings("unchecked")
    public static void initializeCastContext(Context context) {
        try {
            // Check GoogleApiAvailability and Play Services availability
            Class<?> googleApiAvailabilityCls = Class.forName("com.google.android.gms.common.GoogleApiAvailability");
            Object googleApiInstance = googleApiAvailabilityCls.getMethod("getInstance").invoke(null);
            int result = (Integer) googleApiAvailabilityCls
                    .getMethod("isGooglePlayServicesAvailable", Context.class)
                    .invoke(googleApiInstance, context);
            // ConnectionResult.SUCCESS == 0
            if (result == 0) {
                // Initialize CastContext.getSharedInstance(context, executor)
                Class<?> castContextCls = Class.forName("com.google.android.gms.cast.framework.CastContext");
                Executor executor = ContextCompat.getMainExecutor(context);
                castContextCls.getMethod("getSharedInstance", Context.class, Executor.class)
                        .invoke(null, context, executor);
            }
        } catch (ClassNotFoundException ignored) {
            // Play Services / Cast not available on this build flavor.
        } catch (Throwable ignored) {
            // Ignore any reflection/runtime errors to avoid crashing app startup.
        }
    }
}
