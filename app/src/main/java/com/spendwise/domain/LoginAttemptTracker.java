package com.spendwise.domain;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * The sign in lockout policy. Five failed attempts lock an account for five minutes.
 * The clock is injected so a test can jump forward instead of sleeping.
 */
public final class LoginAttemptTracker {
    public static final int MAX_ATTEMPTS = 5;
    public static final long LOCKOUT_MILLIS = 5 * 60 * 1000L;

    public interface Clock {
        long currentTimeMillis();
    }

    public static final Clock SYSTEM_CLOCK = System::currentTimeMillis;

    private static final class AttemptRecord {
        int consecutiveFailures;
        long lockedUntilMillis;
    }

    private final Map<String, AttemptRecord> records = new HashMap<>();
    private final Clock clock;

    public LoginAttemptTracker() {
        this(SYSTEM_CLOCK);
    }

    public LoginAttemptTracker(Clock clock) {
        this.clock = Guard.notNull(clock, "clock");
    }

    /**
     * True while the lock is still running. The deadline is compared against the injected
     * clock, which is what makes the expiry testable without sleeping.
     */
    public boolean isLockedOut(String email) {
        AttemptRecord record = records.get(normalise(email));
        if (record == null) {
            return false;
        }
        if (record.lockedUntilMillis == 0L) {
            return false;
        }
        if (clock.currentTimeMillis() >= record.lockedUntilMillis) {
            record.lockedUntilMillis = 0L;
            record.consecutiveFailures = 0;
            return false;
        }
        return true;
    }

    /** Seconds remaining. */
    public long secondsRemaining(String email) {
        AttemptRecord record = records.get(normalise(email));
        if (record == null || record.lockedUntilMillis == 0L) {
            return 0L;
        }
        long remaining = record.lockedUntilMillis - clock.currentTimeMillis();
        if (remaining <= 0L) {
            return 0L;
        }

        return (remaining + 999L) / 1000L;
    }

    /** Counts a failed attempt and starts the lock on the fifth one. */
    public boolean recordFailure(String email) {
        String key = normalise(email);
        AttemptRecord record = records.get(key);
        if (record == null) {
            record = new AttemptRecord();
            records.put(key, record);
        }

        record.consecutiveFailures++;
        if (record.consecutiveFailures >= MAX_ATTEMPTS) {
            record.lockedUntilMillis = clock.currentTimeMillis() + LOCKOUT_MILLIS;
            return true;
        }
        return false;
    }

    /** Clears the counter, so a successful sign in forgives earlier mistakes. */
    public void recordSuccess(String email) {
        records.remove(normalise(email));
    }

    /** Attempts remaining. */
    public int attemptsRemaining(String email) {
        AttemptRecord record = records.get(normalise(email));
        if (record == null) {
            return MAX_ATTEMPTS;
        }
        return Math.max(0, MAX_ATTEMPTS - record.consecutiveFailures);
    }

    /** Reset. */
    public void reset() {
        records.clear();
    }

    /** Normalise. */
    private static String normalise(String email) {
        return Guard.notBlank(email, "email").trim().toLowerCase(Locale.ROOT);
    }
}
