package com.spendwise.ui;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.spendwise.domain.Currency;

/**
 * Remembers the display currency chosen on the Profile screen. An unreadable or
 * unknown stored value falls back to the base currency instead of throwing.
 */
public final class CurrencyManager {
    private static final String PREFS_NAME = "spendwise_currency";
    private static final String KEY_CODE = "display_currency";

    private CurrencyManager() {
    }

    /** Returns the currency. */
    @NonNull
    public static Currency getCurrency(@NonNull Context context) {
        SharedPreferences prefs = preferences(context);
        if (prefs == null) {
            return Currency.LKR;
        }
        Currency stored = Currency.fromCodeOrNull(prefs.getString(KEY_CODE, null));
        return stored == null ? Currency.LKR : stored;
    }

    /** Sets the currency. */
    public static void setCurrency(@NonNull Context context, @NonNull Currency currency) {
        SharedPreferences prefs = preferences(context);
        if (prefs != null) {
            prefs.edit().putString(KEY_CODE, currency.getCode()).apply();
        }
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
