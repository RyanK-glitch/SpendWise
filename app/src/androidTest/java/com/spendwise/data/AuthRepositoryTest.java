package com.spendwise.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.spendwise.data.dao.UserDao;
import com.spendwise.data.entity.User;
import com.spendwise.data.repository.AuthRepository;
import com.spendwise.domain.AuthProvider;
import com.spendwise.domain.AuthResult;
import com.spendwise.domain.LoginAttemptTracker;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

/**
 * Instrumented tests for the two-tier identity model against a real database: sign up,
 * sign in, lockout, federated identity and the uniform failure message.
 */
@RunWith(AndroidJUnit4.class)
public class AuthRepositoryTest {

    private static final String EMAIL = "user@example.com";
    private static final String NAME = "Test User";
    private static final String PASSWORD = "correct1horse";

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

    private SpendWiseDatabase database;
    private UserDao userDao;
    private AuthRepository authRepository;
    private FakeClock clock;

    /** Sets the up. */
    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase.class)
                .allowMainThreadQueries()
                .build();
        userDao = database.userDao();
        clock = new FakeClock();
        authRepository = new AuthRepository(userDao, null, new LoginAttemptTracker(clock));
    }

    /** Tear down. */
    @After
    public void tearDown() throws IOException {
        database.close();
    }

    // ---- Sign-up ---------------------------------------------------------

    /** Sign up_creates an account and returns it. */
    @Test
    public void signUp_createsAnAccountAndReturnsIt() {
        AuthResult result = authRepository.signUp(EMAIL, NAME, PASSWORD);

        assertTrue(result.isSuccess());
        assertNotNull(result.getUser());
        assertEquals(EMAIL, result.getUser().getEmail());
        assertEquals(NAME, result.getUser().getDisplayName());
        assertEquals(1, userDao.count());
    }

    /** Sign up_stores a hash rather than the plaintext password. */
    @Test
    public void signUp_storesAHashRatherThanThePlaintextPassword() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);

        User stored = userDao.findByEmail(EMAIL);
        assertNotNull(stored.getPasswordHash());
        assertFalse("the plaintext password was persisted",
                stored.getPasswordHash().contains(PASSWORD));
        assertTrue(stored.getPasswordHash().startsWith("PBKDF2"));
    }

    /** Sign up_normalises the email to lower case. */
    @Test
    public void signUp_normalisesTheEmailToLowerCase() {
        authRepository.signUp("USER@EXAMPLE.COM", NAME, PASSWORD);
        assertNotNull(userDao.findByEmail(EMAIL));
    }

    /** Sign up_rejects a duplicate email. */
    @Test
    public void signUp_rejectsADuplicateEmail() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        AuthResult second = authRepository.signUp(EMAIL, "Someone Else", "another1pass");

        assertFalse(second.isSuccess());
        assertEquals(AuthResult.Failure.EMAIL_ALREADY_REGISTERED, second.getFailure());
        assertEquals("a duplicate account was created", 1, userDao.count());
    }

    /** Sign up_rejects a duplicate regardless of case. */
    @Test
    public void signUp_rejectsADuplicateRegardlessOfCase() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        AuthResult second = authRepository.signUp("User@Example.COM", NAME, PASSWORD);

        assertFalse(second.isSuccess());
        assertEquals(1, userDao.count());
    }

    /** Sign up_rejects invalid input before touching the database. */
    @Test
    public void signUp_rejectsInvalidInputBeforeTouchingTheDatabase() {
        assertEquals(AuthResult.Failure.VALIDATION_ERROR,
                authRepository.signUp("not-an-email", NAME, PASSWORD).getFailure());
        assertEquals(AuthResult.Failure.VALIDATION_ERROR,
                authRepository.signUp(EMAIL, NAME, "short").getFailure());
        assertEquals(AuthResult.Failure.VALIDATION_ERROR,
                authRepository.signUp(EMAIL, "", PASSWORD).getFailure());
        assertEquals(0, userDao.count());
    }

    // ---- Sign-in ---------------------------------------------------------

    /** Sign in_succeeds with the correct password. */
    @Test
    public void signIn_succeedsWithTheCorrectPassword() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        AuthResult result = authRepository.signIn(EMAIL, PASSWORD);

        assertTrue(result.isSuccess());
        assertEquals(EMAIL, result.getUser().getEmail());
    }

    /** Sign in_is case insensitive on the email. */
    @Test
    public void signIn_isCaseInsensitiveOnTheEmail() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        assertTrue(authRepository.signIn("USER@EXAMPLE.COM", PASSWORD).isSuccess());
    }

    /** Sign in_fails with the wrong password. */
    @Test
    public void signIn_failsWithTheWrongPassword() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        AuthResult result = authRepository.signIn(EMAIL, "wrong1password");

        assertFalse(result.isSuccess());
        assertEquals(AuthResult.Failure.INVALID_CREDENTIALS, result.getFailure());
        assertNull(result.getUser());
    }

    /** Sign in_gives the same failure for an unknown account as for a wrong password. */
    @Test
    public void signIn_givesTheSameFailureForAnUnknownAccountAsForAWrongPassword() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);

        AuthResult wrongPassword = authRepository.signIn(EMAIL, "wrong1password");
        AuthResult noSuchAccount = authRepository.signIn("nobody@example.com", PASSWORD);

        assertEquals(wrongPassword.getFailure(), noSuchAccount.getFailure());
        assertEquals(wrongPassword.getMessage(), noSuchAccount.getMessage());
    }

    /** Sign in_refuses a federated account that has no local password. */
    @Test
    public void signIn_refusesAFederatedAccountThatHasNoLocalPassword() {
        authRepository.signInWithFederatedIdentity(EMAIL, NAME, AuthProvider.GOOGLE);

        // The stored hash is null; verification must refuse rather than crash.
        AuthResult result = authRepository.signIn(EMAIL, PASSWORD);
        assertFalse(result.isSuccess());
        assertEquals(AuthResult.Failure.INVALID_CREDENTIALS, result.getFailure());
    }

    // ---- Lockout ---------------------------------------------------------

    /** Repeated failures_lock the account. */
    @Test
    public void repeatedFailures_lockTheAccount() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);

        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS; i++) {
            authRepository.signIn(EMAIL, "wrong1password");
        }

        AuthResult locked = authRepository.signIn(EMAIL, "wrong1password");
        assertEquals(AuthResult.Failure.ACCOUNT_LOCKED, locked.getFailure());
        assertTrue(locked.getLockoutSecondsRemaining() > 0);
    }

    /** Lockout_blocks even the correct password. */
    @Test
    public void lockout_blocksEvenTheCorrectPassword() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS; i++) {
            authRepository.signIn(EMAIL, "wrong1password");
        }

        AuthResult result = authRepository.signIn(EMAIL, PASSWORD);
        assertFalse(result.isSuccess());
        assertEquals(AuthResult.Failure.ACCOUNT_LOCKED, result.getFailure());
    }

    /** Lockout_lifts once the window expires. */
    @Test
    public void lockout_liftsOnceTheWindowExpires() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS; i++) {
            authRepository.signIn(EMAIL, "wrong1password");
        }

        clock.advanceMillis(LoginAttemptTracker.LOCKOUT_MILLIS);

        assertTrue("lockout should have expired",
                authRepository.signIn(EMAIL, PASSWORD).isSuccess());
    }

    /** Successful sign in_resets the failure count. */
    @Test
    public void successfulSignIn_resetsTheFailureCount() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        authRepository.signIn(EMAIL, "wrong1password");
        authRepository.signIn(EMAIL, "wrong1password");
        authRepository.signIn(EMAIL, PASSWORD);

        assertEquals(LoginAttemptTracker.MAX_ATTEMPTS, authRepository.attemptsRemaining(EMAIL));
    }

    // ---- Federated sign-in ------------------------------------------------

    /** Federated sign in_creates an account on first use. */
    @Test
    public void federatedSignIn_createsAnAccountOnFirstUse() {
        AuthResult result = authRepository.signInWithFederatedIdentity(
                EMAIL, NAME, AuthProvider.GOOGLE);

        assertTrue(result.isSuccess());
        assertEquals(AuthProvider.GOOGLE, result.getUser().providerAsEnum());
        assertNull("a federated account must not have a local password",
                result.getUser().getPasswordHash());
    }

    /** Federated sign in_reuses the existing account on subsequent use. */
    @Test
    public void federatedSignIn_reusesTheExistingAccountOnSubsequentUse() {
        AuthResult first = authRepository.signInWithFederatedIdentity(
                EMAIL, NAME, AuthProvider.GOOGLE);
        AuthResult second = authRepository.signInWithFederatedIdentity(
                EMAIL, NAME, AuthProvider.GOOGLE);

        assertEquals(first.getUser().getId(), second.getUser().getId());
        assertEquals("a second account was created", 1, userDao.count());
    }

    /** Federated sign in_resolves to the same ledger as an existing local account. */
    @Test
    public void federatedSignIn_resolvesToTheSameLedgerAsAnExistingLocalAccount() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        AuthResult federated = authRepository.signInWithFederatedIdentity(
                EMAIL, NAME, AuthProvider.GOOGLE);

        assertTrue(federated.isSuccess());
        assertEquals(1, userDao.count());
    }

    /** Federated sign in_derives a name when the provider supplies none. */
    @Test
    public void federatedSignIn_derivesANameWhenTheProviderSuppliesNone() {
        AuthResult result = authRepository.signInWithFederatedIdentity(
                EMAIL, null, AuthProvider.GOOGLE);

        assertTrue(result.isSuccess());
        assertEquals("user", result.getUser().getDisplayName());
    }

    /** Federated sign in_rejects an unusable email from the provider. */
    @Test
    public void federatedSignIn_rejectsAnUnusableEmailFromTheProvider() {
        AuthResult result = authRepository.signInWithFederatedIdentity(
                "not-an-email", NAME, AuthProvider.GOOGLE);

        assertFalse(result.isSuccess());
        assertEquals(AuthResult.Failure.PROVIDER_ERROR, result.getFailure());
    }

    // ---- Password reset ---------------------------------------------------

    /** Password reset_responds identically for known and unknown addresses. */
    @Test
    public void passwordReset_respondsIdenticallyForKnownAndUnknownAddresses() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);

        AuthResult known = authRepository.requestPasswordReset(EMAIL);
        AuthResult unknown = authRepository.requestPasswordReset("nobody@example.com");

        assertEquals(known.getMessage(), unknown.getMessage());
    }

    /** Completing a reset changes the password and clears any lockout. */
    @Test
    public void completingAResetChangesThePasswordAndClearsAnyLockout() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        for (int i = 0; i < LoginAttemptTracker.MAX_ATTEMPTS; i++) {
            authRepository.signIn(EMAIL, "wrong1password");
        }

        assertTrue(authRepository.completePasswordReset(EMAIL, "brandnew1pass").isSuccess());

        // The new password works immediately, despite the earlier lockout.
        assertTrue(authRepository.signIn(EMAIL, "brandnew1pass").isSuccess());
        // The old one no longer does.
        assertFalse(authRepository.signIn(EMAIL, PASSWORD).isSuccess());
    }

    /** Completing a reset rejects a weak new password. */
    @Test
    public void completingAResetRejectsAWeakNewPassword() {
        authRepository.signUp(EMAIL, NAME, PASSWORD);
        AuthResult result = authRepository.completePasswordReset(EMAIL, "weak");

        assertFalse(result.isSuccess());
        assertEquals(AuthResult.Failure.VALIDATION_ERROR, result.getFailure());
        assertTrue("the original password should still work",
                authRepository.signIn(EMAIL, PASSWORD).isSuccess());
    }
}
