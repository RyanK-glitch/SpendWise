package com.spendwise.domain;

/**
 * Contract checks written as ordinary if/throw rather than as Java assert, because the
 * Android runtime disables assert and it would be dead code in a shipped app. A broken
 * precondition blames the caller, a broken postcondition blames this class.
 */
public final class Guard {
    private Guard() {
    }

    /** Precondition. A failure means the caller passed something invalid. */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException("Precondition violated: " + message);
        }
    }

    /** Postcondition. A failure means this component broke its own promise. */
    public static void ensure(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Postcondition violated: " + message);
        }
    }

    /** Class invariant. A failure means the object reached a state it should not be able to. */
    public static void invariant(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("Invariant violated: " + message);
        }
    }

    /** Not null. */
    public static <T> T notNull(T value, String parameterName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    "Precondition violated: " + parameterName + " must not be null");
        }
        return value;
    }

    /** Not blank. */
    public static String notBlank(String value, String parameterName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(
                    "Precondition violated: " + parameterName + " must not be blank");
        }
        return value;
    }

    /** In range. */
    public static long inRange(long value, long minInclusive, long maxInclusive, String parameterName) {
        if (value < minInclusive || value > maxInclusive) {
            throw new IllegalArgumentException(String.format(
                    "Precondition violated: %s must be in [%d, %d] but was %d",
                    parameterName, minInclusive, maxInclusive, value));
        }
        return value;
    }
}
