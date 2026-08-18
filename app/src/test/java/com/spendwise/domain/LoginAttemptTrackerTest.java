package com.spendwise.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Walks every transition of the lockout state machine, including the boundary at exactly
 * five attempts and the moment the lock expires, using an injected clock.
 */
public class LoginAttemptTrackerTest {

    private static final String EMAIL = "user@example.com";

    /** Test double: time only moves when the test says so. */
    private static final class FakeClock implements LoginAttemptTracker.Clock {
        private long now = 1_000_000L;

        /** Current time millis. */
        @Override
        public long currentTimeMillis() {
            return now;
        }

        void advanceMillis(long millis) {
            now += millis;
        }
    }

    private FakeClock clock;
    private LoginAttemptTracker tracker;

    /** Sets the up. */
    @Before
    public void setUp() {
        clock = new FakeClock();
        tracker = new LoginAttemptTracker(clock);
    }

    /** Fresh account_is not locked and has a full attempt budget. */
    @Test
    public void freshAccount_isNotLockedAndHasAFullAttemptBudget() {
        assertFalse(tracker.isLockedOut(EMAIL));
        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS, tracker.attemptsRemaining(EMAIL));
        assertEquals(0L, tracker.secondsRemaining(EMAIL));
    }

    /** Failures below the limit_do not lock the account. */
    @Test
    public void failuresBelowTheLimit_doNotLockTheAccount() {
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS - 1; i++) {
            assertFalse("locked too early at attempt " + (i + 1), tracker.recordFailure(EMAIL));
            assertFalse(tracker.isLockedOut(EMAIL));
        }
        assertEquals(1, tracker.attemptsRemaining(EMAIL));
    }

    /** The final permitted failure_triggers the lockout. */
    @Test
    public void theFinalPermittedFailure_triggersTheLockout() {
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS - 1; i++) {
            tracker.recordFailure(EMAIL);
        }
        // Boundary: the MAX_ATTEMPTS-th failure is the one that locks.
        assertTrue(tracker.recordFailure(EMAIL));
        assertTrue(tracker.isLockedOut(EMAIL));
        assertEquals(0, tracker.attemptsRemaining(EMAIL));
    }

    /** Locked account_reports time remaining. */
    @Test
    public void lockedAccount_reportsTimeRemaining() {
        lockOut();
        assertEquals(LoginAttemptTracker.LOCKOUT_MILLIS / 1000, tracker.secondsRemaining(EMAIL));
    }

    /** Seconds remaining_rounds up so it never displays zero while still locked. */
    @Test
    public void secondsRemaining_roundsUpSoItNeverDisplaysZeroWhileStillLocked() {
        lockOut();
        // Leave 500ms on the clock: still locked, so it must report 1 second, not 0.
        clock.advanceMillis(LoginAttemptTracker.LOCKOUT_MILLIS - 500L);
        assertTrue(tracker.isLockedOut(EMAIL));
        assertEquals(1L, tracker.secondsRemaining(EMAIL));
    }

    /** Lock remains in force just before expiry. */
    @Test
    public void lockRemainsInForceJustBeforeExpiry() {
        lockOut();
        clock.advanceMillis(LoginAttemptTracker.LOCKOUT_MILLIS - 1);
        assertTrue(tracker.isLockedOut(EMAIL));
    }

    /** Lock expires exactly at the deadline. */
    @Test
    public void lockExpiresExactlyAtTheDeadline() {
        lockOut();
        clock.advanceMillis(LoginAttemptTracker.LOCKOUT_MILLIS);
        assertFalse(tracker.isLockedOut(EMAIL));
    }

    /** Expiry restores the full attempt budget. */
    @Test
    public void expiryRestoresTheFullAttemptBudget() {
        lockOut();
        clock.advanceMillis(LoginAttemptTracker.LOCKOUT_MILLIS);
        tracker.isLockedOut(EMAIL);   // the query is what performs the reset
        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS, tracker.attemptsRemaining(EMAIL));
        assertEquals(0L, tracker.secondsRemaining(EMAIL));
    }

    /** Successful sign in_clears accumulated failures. */
    @Test
    public void successfulSignIn_clearsAccumulatedFailures() {
        tracker.recordFailure(EMAIL);
        tracker.recordFailure(EMAIL);
        tracker.recordSuccess(EMAIL);
        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS, tracker.attemptsRemaining(EMAIL));
        assertFalse(tracker.isLockedOut(EMAIL));
    }

    /** Successful sign in_also clears an active lockout. */
    @Test
    public void successfulSignIn_alsoClearsAnActiveLockout() {
        lockOut();
        tracker.recordSuccess(EMAIL);
        assertFalse(tracker.isLockedOut(EMAIL));
    }

    /** Accounts are throttled independently. */
    @Test
    public void accountsAreThrottledIndependently() {
        lockOut();
        assertFalse("locking one account must not affect another",
                tracker.isLockedOut("other@example.com"));
        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS,
                tracker.attemptsRemaining("other@example.com"));
    }

    /** Email case and whitespace do not create a separate attempt budget. */
    @Test
    public void emailCaseAndWhitespaceDoNotCreateASeparateAttemptBudget() {
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS; i++) {
            tracker.recordFailure("USER@EXAMPLE.COM");
        }
        assertTrue(tracker.isLockedOut(EMAIL));
        assertTrue(tracker.isLockedOut("  User@Example.Com  "));
    }

    /** Reset_clears every tracked account. */
    @Test
    public void reset_clearsEveryTrackedAccount() {
        lockOut();
        tracker.recordFailure("other@example.com");
        tracker.reset();
        assertFalse(tracker.isLockedOut(EMAIL));
        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS,
                tracker.attemptsRemaining("other@example.com"));
    }

    /** Failures after expiry_start a fresh count rather than relocking. */
    @Test
    public void failuresAfterExpiry_startAFreshCountRatherThanRelocking() {
        lockOut();
        clock.advanceMillis(LoginAttemptTracker.LOCKOUT_MILLIS);
        tracker.isLockedOut(EMAIL);   // triggers the reset

        assertFalse("one failure after expiry must not immediately re-lock",
                tracker.recordFailure(EMAIL));
        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS - 1, tracker.attemptsRemaining(EMAIL));
    }

    /** Blank email is rejected. */
    @Test(expected = IllegalArgumentException.class)
    public void blankEmailIsRejected() {
        tracker.isLockedOut("   ");
    }

    /** Lock out. */
    private void lockOut() {
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS; i++) {
            tracker.recordFailure(EMAIL);
        }
    }
}
