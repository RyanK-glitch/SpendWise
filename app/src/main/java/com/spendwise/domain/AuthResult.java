package com.spendwise.domain;

import androidx.annotation.Nullable;

import com.spendwise.data.entity.User;

/**
 * The outcome of a sign in or sign up. Either a user id, or one failure reason from a
 * fixed list. The same INVALID_CREDENTIALS value is returned for a wrong password and
 * for an unknown account, so the screen cannot leak which accounts exist.
 */
public final class AuthResult {
    public enum Failure {
        INVALID_CREDENTIALS,

        EMAIL_ALREADY_REGISTERED,

        ACCOUNT_LOCKED,

        VALIDATION_ERROR,

        PROVIDER_NOT_CONFIGURED,

        PROVIDER_ERROR,

        STORAGE_ERROR
    }

    private final boolean success;
    private final User user;
    private final Failure failure;
    private final String message;
    private final long lockoutSecondsRemaining;

    private AuthResult(boolean success, @Nullable User user, @Nullable Failure failure,
                       @Nullable String message, long lockoutSecondsRemaining) {
        this.success = success;
        this.user = user;
        this.failure = failure;
        this.message = message;
        this.lockoutSecondsRemaining = lockoutSecondsRemaining;
    }

    /** A successful sign in, carrying the local user id. */
    public static AuthResult success(User user) {
        return new AuthResult(true, Guard.notNull(user, "user"), null, null, 0L);
    }

    /** A refused sign in, carrying one reason from the fixed list. */
    public static AuthResult failure(Failure failure, String message) {
        return new AuthResult(false, null,
                Guard.notNull(failure, "failure"),
                Guard.notBlank(message, "message"), 0L);
    }

    /** Locked out. */
    public static AuthResult lockedOut(long secondsRemaining) {
        long minutes = (secondsRemaining + 59) / 60;
        String message = "Too many failed attempts. Try again in "
                + (minutes <= 1 ? "a minute" : minutes + " minutes") + ".";
        return new AuthResult(false, null, Failure.ACCOUNT_LOCKED, message, secondsRemaining);
    }

    /** True when success. */
    public boolean isSuccess() {
        return success;
    }

    /** Returns the user. */
    @Nullable
    public User getUser() {
        return user;
    }

    /** Returns the failure. */
    @Nullable
    public Failure getFailure() {
        return failure;
    }

    /** Returns the message. */
    @Nullable
    public String getMessage() {
        return message;
    }

    /** Returns the lockout seconds remaining. */
    public long getLockoutSecondsRemaining() {
        return lockoutSecondsRemaining;
    }
}
