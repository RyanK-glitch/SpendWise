package com.spendwise.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Holds the signed-in session. The user id is written to EncryptedSharedPreferences,
 * backed by the Android Keystore, and falls back to memory rather than to plain disk
 * if the Keystore is unavailable. A volatile double-checked singleton, because any
 * thread may ask who is signed in.
 */
public final class SessionManager {
    private static final String TAG = "SessionManager";
    private static final String PREFS_NAME = "spendwise_session";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_DISPLAY_NAME = "display_name";
    private static final String KEY_PROVIDER = "provider";

    public static final long NO_USER = -1L;

    private static volatile SessionManager instance;

    private final SharedPreferences preferences;
    private final boolean encrypted;

    private long memoryUserId = NO_USER;
    private String memoryEmail;
    private String memoryDisplayName;
    private String memoryProvider;

    private SessionManager(Context context) {
        SharedPreferences prefs = null;
        boolean isEncrypted = false;
        try {
            MasterKey masterKey = new MasterKey.Builder(context.getApplicationContext())
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build();

            prefs = EncryptedSharedPreferences.create(
                    context.getApplicationContext(),
                    PREFS_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
            isEncrypted = true;
        } catch (GeneralSecurityException | IOException e) {
            Log.w(TAG, "Encrypted storage unavailable ("
                    + e.getClass().getSimpleName() + "); session will be memory-only");
        }
        this.preferences = prefs;
        this.encrypted = isEncrypted;
    }

    /** Double-checked locking again, because any thread may ask who is signed in. */
    public static SessionManager getInstance(Context context) {
        SessionManager local = instance;
        if (local == null) {
            synchronized (SessionManager.class) {
                local = instance;
                if (local == null) {
                    local = new SessionManager(context);
                    instance = local;
                }
            }
        }
        return local;
    }

    /** True when persistent. */
    public boolean isPersistent() {
        return encrypted;
    }

    /** Begin session. */
    public void beginSession(long userId, String email, String displayName, String provider) {
        if (preferences != null) {
            preferences.edit()
                    .putLong(KEY_USER_ID, userId)
                    .putString(KEY_EMAIL, email)
                    .putString(KEY_DISPLAY_NAME, displayName)
                    .putString(KEY_PROVIDER, provider)
                    .apply();
        } else {
            memoryUserId = userId;
            memoryEmail = email;
            memoryDisplayName = displayName;
            memoryProvider = provider;
        }
    }

    /** Clears the session on sign out, in memory and on disk. */
    public void endSession() {
        if (preferences != null) {
            preferences.edit().clear().apply();
        }
        memoryUserId = NO_USER;
        memoryEmail = null;
        memoryDisplayName = null;
        memoryProvider = null;
    }

    /** True when a session exists. MainActivity asks this before it draws anything. */
    public boolean isSignedIn() {
        return getUserId() != NO_USER;
    }

    /** Returns the user id. */
    public long getUserId() {
        if (preferences != null) {
            return preferences.getLong(KEY_USER_ID, NO_USER);
        }
        return memoryUserId;
    }

    /** Returns the email. */
    public String getEmail() {
        return preferences != null ? preferences.getString(KEY_EMAIL, null) : memoryEmail;
    }

    /** Returns the display name. */
    public String getDisplayName() {
        return preferences != null
                ? preferences.getString(KEY_DISPLAY_NAME, null) : memoryDisplayName;
    }

    /** Returns the provider. */
    public String getProvider() {
        return preferences != null
                ? preferences.getString(KEY_PROVIDER, null) : memoryProvider;
    }
}
