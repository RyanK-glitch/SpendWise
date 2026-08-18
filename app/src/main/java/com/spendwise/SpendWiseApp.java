package com.spendwise;

import android.app.Application;
import android.util.Log;

import com.spendwise.notification.NotificationHelper;
import com.spendwise.notification.SpendWiseMessagingService;
import com.spendwise.ui.ThemeManager;

/**
 * Application entry point. Creates the notification channels and applies the saved
 * theme before any screen is shown, so the app opens in the right colours.
 */
public class SpendWiseApp extends Application {
    private static final String TAG = "SpendWiseApp";

    /** Called by the framework when the screen is first created. */
    @Override
    public void onCreate() {
        super.onCreate();

        ThemeManager.apply(this);
        NotificationHelper.createChannels(this);
        registerForPush();
    }

    /** Subscribes the device to the push topic on first launch. */
    private void registerForPush() {
        if (!BuildConfig.FIREBASE_CONFIGURED) {
            return;
        }
        try {
            SpendWiseMessagingService.registerForPush();
        } catch (RuntimeException e) {
            Log.w(TAG, "Push registration skipped", e);
        }
    }
}
