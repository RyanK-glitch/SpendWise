package com.spendwise.ui;

import android.text.Editable;
import android.text.TextWatcher;

/**
 * A TextWatcher with only one method to implement, so screens can clear a field error
 * as the user types without three empty overrides each time.
 */
public final class SimpleTextWatcher implements TextWatcher {
    private final Runnable onChanged;

    public SimpleTextWatcher(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    /** Before text changed. */
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    /** Called by the framework as the user types. */
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    /** After text changed. */
    @Override
    public void afterTextChanged(Editable s) {
        if (onChanged != null) {
            onChanged.run();
        }
    }
}
