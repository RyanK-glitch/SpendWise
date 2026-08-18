package com.spendwise.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Light, dark or follow the system. The choice is saved and applied at startup, so it
 * survives a restart.
 */
public final class ThemeManager {
    public enum Mode {
        FOLLOW_SYSTEM("system", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM),
        LIGHT("light", AppCompatDelegate.MODE_NIGHT_NO),
        DARK("dark", AppCompatDelegate.MODE_NIGHT_YES);

        private final String key;
        @AppCompatDelegate.NightMode
        private final int nightMode;

        Mode(String key, @AppCompatDelegate.NightMode int nightMode) {
            this.key = key;
            this.nightMode = nightMode;
        }

        /** Returns the key. */
        public String getKey() {
            return key;
        }

        /** Returns the night mode. */
        @AppCompatDelegate.NightMode
        public int getNightMode() {
            return nightMode;
        }

        /** From key. */
        public static Mode fromKey(@Nullable String key) {
            for (Mode mode : values()) {
                if (mode.key.equals(key)) {
                    return mode;
                }
            }
            return FOLLOW_SYSTEM;
        }
    }

    private static final String PREFS_NAME = "spendwise_theme";
    private static final String KEY_MODE = "theme_mode";

    private ThemeManager() {
    }

    /** Applies the value. */
    public static void apply(@NonNull Context context) {
        AppCompatDelegate.setDefaultNightMode(getMode(context).getNightMode());
    }

    /** Returns the mode. */
    @NonNull
    public static Mode getMode(@NonNull Context context) {
        SharedPreferences prefs = preferences(context);
        return prefs == null ? Mode.FOLLOW_SYSTEM : Mode.fromKey(prefs.getString(KEY_MODE, null));
    }

    /** Sets the mode. */
    public static void setMode(@NonNull Context context, @NonNull Mode mode) {
        SharedPreferences prefs = preferences(context);
        if (prefs != null) {
            prefs.edit().putString(KEY_MODE, mode.getKey()).apply();
        }
        AppCompatDelegate.setDefaultNightMode(mode.getNightMode());
    }

    /** Preferences. */
    @Nullable
    private static SharedPreferences preferences(Context context) {
        try {
            return context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
