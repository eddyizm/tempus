package com.cappielloantonio.tempo;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.util.UnstableApi;
import androidx.preference.PreferenceManager;

import com.cappielloantonio.tempo.github.Github;
import com.cappielloantonio.tempo.helper.ThemeHelper;
import com.cappielloantonio.tempo.subsonic.Subsonic;
import com.cappielloantonio.tempo.subsonic.SubsonicPreferences;
import com.cappielloantonio.tempo.ui.activity.CrashActivity;
import com.cappielloantonio.tempo.util.ClientCertManager;
import com.cappielloantonio.tempo.util.Preferences;

import cat.ereza.customactivityoncrash.config.CaocConfig;

public class App extends Application {
    private static App instance;
    private static Context context;
    private static Subsonic subsonic;
    private static Github github;
    private static SharedPreferences preferences;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();

        // Capture crash logs
        CaocConfig.Builder.create()
                .backgroundMode(CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM) //default: CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM
                .enabled(true) //default: true
                .showErrorDetails(true) //default: true
                .showRestartButton(true) //default: true
                .logErrorOnRestart(true) //default: true
                .trackActivities(false) //default: false
                .minTimeBetweenCrashesMs(3000) //default: 3000
                .errorDrawable(R.drawable.ui_crash) //default: bug image
                .restartActivity(null) //default: null (your app's launch activity)
                .errorActivity(CrashActivity.class) //default: null (default error activity)
                .eventListener(null) //default: null
                .customCrashDataCollector(null) //default: null
                .apply();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        String themePref = sharedPreferences.getString(Preferences.THEME, ThemeHelper.DEFAULT_MODE);
        ThemeHelper.applyTheme(themePref);

        instance = new App();
        context = getApplicationContext();
        preferences = PreferenceManager.getDefaultSharedPreferences(context);

        ClientCertManager.setupSslSocketFactory(context);
    }

    public static App getInstance() {
        if (instance == null) {
            instance = new App();
        }

        return instance;
    }

    public static Context getContext() {
        if (context == null) {
            context = getInstance();
        }

        return context;
    }

    public static Subsonic getSubsonicClientInstance(boolean override) {
        if (subsonic == null || override) {
            subsonic = getSubsonicClient();
        }
        return subsonic;
    }
    
    public static Subsonic getSubsonicPublicClientInstance(boolean override) {

        /*
        If I do the shortcut that the IDE suggests:
            SubsonicPreferences preferences = getSubsonicPreferences1();
        During the chain of calls it will run the following:
            String server = Preferences.getInUseServerAddress();
        Which could return Local URL, causing issues like generating public shares with Local URL

        To prevent this I just replicated the entire chain of functions here,
        if you need a call to Subsonic using the Server (Public) URL use this function.
         */

        String server = Preferences.getServer();
        String username = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        String salt = Preferences.getSalt();
        boolean isLowSecurity = Preferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        if (subsonic == null || override) {
            
            if (preferences.getAuthentication() != null) {
                if (preferences.getAuthentication().getPassword() != null)
                    Preferences.setPassword(preferences.getAuthentication().getPassword());
                if (preferences.getAuthentication().getToken() != null)
                    Preferences.setToken(preferences.getAuthentication().getToken());
                if (preferences.getAuthentication().getSalt() != null)
                    Preferences.setSalt(preferences.getAuthentication().getSalt());
            }

            
        }
        
        return new Subsonic(preferences);
    }

    public static Github getGithubClientInstance() {
        if (github == null) {
            github = new Github();
        }
        return github;
    }

    public SharedPreferences getPreferences() {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context);
        }

        return preferences;
    }

    public static void refreshSubsonicClient() {
        subsonic = getSubsonicClient();
    }

    private static Subsonic getSubsonicClient() {
        SubsonicPreferences preferences = getSubsonicPreferences();

        if (preferences.getAuthentication() != null) {
            if (preferences.getAuthentication().getPassword() != null)
                Preferences.setPassword(preferences.getAuthentication().getPassword());
            if (preferences.getAuthentication().getToken() != null)
                Preferences.setToken(preferences.getAuthentication().getToken());
            if (preferences.getAuthentication().getSalt() != null)
                Preferences.setSalt(preferences.getAuthentication().getSalt());
        }

        return new Subsonic(preferences);
    }

    @NonNull
    private static SubsonicPreferences getSubsonicPreferences() {
        String server = Preferences.getInUseServerAddress();
        String username = Preferences.getUser();
        String password = Preferences.getPassword();
        String token = Preferences.getToken();
        String salt = Preferences.getSalt();
        boolean isLowSecurity = Preferences.isLowScurity();

        SubsonicPreferences preferences = new SubsonicPreferences();
        preferences.setServerUrl(server);
        preferences.setUsername(username);
        preferences.setAuthentication(password, token, salt, isLowSecurity);

        return preferences;
    }
}
