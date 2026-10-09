package com.eddyizm.tempus;

import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.eddyizm.tempus.ui.activity.MainActivity;
import com.eddyizm.tempus.util.Preferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Proves the emulator + docker-Subsonic-server plumbing: the app installs,
 * launches, and reaches RESUMED without crashing when pointed at a server.
 *
 * The server address is the emulator's alias for the host loopback
 * (10.0.2.2), where the CI workflow publishes a Navidrome container.
 */
@RunWith(AndroidJUnit4.class)
public class AppLaunchSmokeTest {

    @Before
    public void grantNotificationsPermission() {
        // The app requests POST_NOTIFICATIONS on first launch; the resulting
        // dialog pauses the activity and blocks it from staying RESUMED in a
        // headless test. Grant it up front so the app reaches a usable state.
        Context context = ApplicationProvider.getApplicationContext();
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .grantRuntimePermission(context.getPackageName(),
                        "android.permission.POST_NOTIFICATIONS");
    }

    @Test
    public void appLaunchesAndConnects() {
        Context context = ApplicationProvider.getApplicationContext();
        String password = InstrumentationRegistry.getArguments()
                .getString("serverPassword", "test");
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString("server", "http://10.0.2.2:4533")
                .putString("user", "admin")
                .putString("password", password)
                // The battery-optimization warning card blocks the home content.
                .putBoolean("battery_optimization", false)
                .commit();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // The app is slow to reach RESUMED on a fresh software-rendered
            // emulator (splash + heavy init), so poll past ActivityScenario's
            // default 45s timeout.
            long deadline = System.currentTimeMillis() + 120_000;
            while (scenario.getState() != Lifecycle.State.RESUMED
                    && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());

            // The app sets openSubsonic only after a successful server ping;
            // wait for it to prove the app actually connected to Navidrome.
            deadline = System.currentTimeMillis() + 60_000;
            while (!Preferences.isOpenSubsonic()
                    && System.currentTimeMillis() < deadline) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            assertTrue("app did not connect to the server", Preferences.isOpenSubsonic());

            // The home screen loads random songs from the seeded library;
            // assert one of them renders to prove the library is served.
            // The Discovery section sits below the sync cards, so scroll to it.
            onView(withText("Test Song 1"))
                    .perform(scrollTo())
                    .check(matches(isDisplayed()));
        }
    }
}
