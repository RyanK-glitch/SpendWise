package com.spendwise.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.dao.UserDao;
import com.spendwise.data.entity.User;
import com.spendwise.domain.AuthProvider;
import com.spendwise.domain.AuthResult;
import com.spendwise.domain.Guard;
import com.spendwise.domain.LoginAttemptTracker;
import com.spendwise.domain.PasswordHasher;
import com.spendwise.domain.Validators;
import com.spendwise.notification.NotificationHelper;

/**
 * Sign up, sign in, sign out and password recovery, across two credential stores.
 * Credentials are checked against the local PBKDF2 hash first and Firebase is only
 * asked after a local rejection, so a password reset made by email still works on the
 * next sign in. Every method here runs on a worker thread.
 */
public class AuthRepository {
    private static final String TAG = "AuthRepository";

    private final UserDao userDao;
    private final SessionManager sessionManager;
    private final LoginAttemptTracker attemptTracker;
    private final FirebaseAuthGateway firebaseAuth;

    @Nullable
    private final Context appContext;

    public AuthRepository(Context context) {
        this(SpendWiseDatabase.getInstance(context).userDao(),
                SessionManager.getInstance(context),
                new LoginAttemptTracker(),
                FirebaseAuthGateway.create(context),
                context.getApplicationContext());
    }

    public AuthRepository(UserDao userDao,
                          SessionManager sessionManager,
                          LoginAttemptTracker attemptTracker) {
        this(userDao, sessionManager, attemptTracker, FirebaseAuthGateway.unconfigured());
    }

    public AuthRepository(UserDao userDao,
                          SessionManager sessionManager,
                          LoginAttemptTracker attemptTracker,
                          @Nullable FirebaseAuthGateway firebaseAuth) {
        this(userDao, sessionManager, attemptTracker, firebaseAuth, null);
    }

    private AuthRepository(UserDao userDao,
                           SessionManager sessionManager,
                           LoginAttemptTracker attemptTracker,
                           @Nullable FirebaseAuthGateway firebaseAuth,
                           @Nullable Context appContext) {
        this.userDao = Guard.notNull(userDao, "userDao");
        this.sessionManager = sessionManager;
        this.attemptTracker = Guard.notNull(attemptTracker, "attemptTracker");

        this.firebaseAuth = firebaseAuth == null
                ? FirebaseAuthGateway.unconfigured()
                : firebaseAuth;
        this.appContext = appContext;
    }

    /** Creates the local account, then mirrors it to Firebase if that is configured. */
    @WorkerThread
    public AuthResult signUp(String email, String displayName, String password) {
        Validators.Result emailCheck = Validators.validateEmail(email);
        if (!emailCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR, emailCheck.getMessage());
        }
        Validators.Result nameCheck = Validators.validateDisplayName(displayName);
        if (!nameCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR, nameCheck.getMessage());
        }
        Validators.Result passwordCheck = Validators.validatePassword(password);
        if (!passwordCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR, passwordCheck.getMessage());
        }

        String normalisedEmail = User.normaliseEmail(email);
        try {
            if (userDao.countByEmail(normalisedEmail) > 0) {
                return AuthResult.failure(AuthResult.Failure.EMAIL_ALREADY_REGISTERED,
                        "An account with that email already exists");
            }

            User user = User.create(normalisedEmail, displayName,
                    PasswordHasher.hash(password), AuthProvider.LOCAL);
            long id = userDao.insert(user);
            user.setId(id);

            beginSession(user);

            mirror("sign-up", firebaseAuth.registerEmailPassword(normalisedEmail, password));

            return AuthResult.success(user);
        } catch (android.database.sqlite.SQLiteConstraintException e) {
            return AuthResult.failure(AuthResult.Failure.EMAIL_ALREADY_REGISTERED,
                    "An account with that email already exists");
        } catch (RuntimeException e) {
            Log.e(TAG, "Sign-up failed: " + e.getClass().getSimpleName());
            return AuthResult.failure(AuthResult.Failure.STORAGE_ERROR,
                    "Could not create your account. Please try again.");
        }
    }

    /**
     * Checks the local hash first and only asks Firebase if that fails, which is how a
     * password reset done by email still works the next time the user signs in.
     */
    @WorkerThread
    public AuthResult signIn(String email, String password) {
        Validators.Result emailCheck = Validators.validateEmail(email);
        if (!emailCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR, emailCheck.getMessage());
        }
        if (password == null || password.isEmpty()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR, "Password is required");
        }

        String normalisedEmail = User.normaliseEmail(email);

        if (attemptTracker.isLockedOut(normalisedEmail)) {
            return AuthResult.lockedOut(attemptTracker.secondsRemaining(normalisedEmail));
        }

        try {
            User user = userDao.findByEmail(normalisedEmail);

            if (user == null || user.getPasswordHash() == null) {
                attemptTracker.recordFailure(normalisedEmail);
                return invalidCredentials(normalisedEmail);
            }

            if (!PasswordHasher.verify(password, user.getPasswordHash())
                    && !adoptPasswordChangedRemotely(user, normalisedEmail, password)) {
                boolean nowLocked = attemptTracker.recordFailure(normalisedEmail);
                if (nowLocked) {
                    return AuthResult.lockedOut(attemptTracker.secondsRemaining(normalisedEmail));
                }
                return invalidCredentials(normalisedEmail);
            }

            attemptTracker.recordSuccess(normalisedEmail);
            beginSession(user);

            mirror("sign-in", firebaseAuth.signInEmailPassword(normalisedEmail, password));

            return AuthResult.success(user);
        } catch (RuntimeException e) {
            Log.e(TAG, "Sign-in failed: " + e.getClass().getSimpleName());
            return AuthResult.failure(AuthResult.Failure.STORAGE_ERROR,
                    "Could not sign you in. Please try again.");
        }
    }

    /** Sign in with google. */
    @WorkerThread
    public AuthResult signInWithGoogle(@Nullable String idToken, String email,
                                       @Nullable String displayName) {
        mirror("google sign-in", firebaseAuth.signInWithGoogle(idToken));
        return signInWithFederatedIdentity(email, displayName, AuthProvider.GOOGLE);
    }

    /** Sign in with federated identity. */
    @WorkerThread
    public AuthResult signInWithFederatedIdentity(String email, String displayName,
                                                  AuthProvider provider) {
        Validators.Result emailCheck = Validators.validateEmail(email);
        if (!emailCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.PROVIDER_ERROR,
                    "The provider did not return a usable email address");
        }
        String normalisedEmail = User.normaliseEmail(email);
        String name = (displayName == null || displayName.trim().isEmpty())
                ? normalisedEmail.split("@")[0]
                : displayName.trim();

        try {
            User existing = userDao.findByEmail(normalisedEmail);
            if (existing != null) {
                beginSession(existing);
                return AuthResult.success(existing);
            }

            User user = User.create(normalisedEmail, name, null, provider);
            long id = userDao.insert(user);
            user.setId(id);
            beginSession(user);
            return AuthResult.success(user);
        } catch (RuntimeException e) {
            Log.e(TAG, "Federated sign-in failed: " + e.getClass().getSimpleName());
            return AuthResult.failure(AuthResult.Failure.STORAGE_ERROR,
                    "Could not complete sign-in. Please try again.");
        }
    }

    /**
     * Asks Firebase to send the reset email. The reply is deliberately the same whether or
     * not the address is registered.
     */
    @WorkerThread
    public AuthResult requestPasswordReset(String email) {
        Validators.Result emailCheck = Validators.validateEmail(email);
        if (!emailCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR, emailCheck.getMessage());
        }

        String normalisedEmail = User.normaliseEmail(email);
        try {
            if (firebaseAuth.isConfigured()) {
                FirebaseAuthGateway.Outcome outcome =
                        firebaseAuth.sendPasswordResetEmail(normalisedEmail);
                if (outcome.isFailure()) {
                    Log.i(TAG, "Password reset email not sent: " + outcome.getMessage());
                }
            } else if (userDao.findByEmail(normalisedEmail) != null) {
                Log.i(TAG, "Password reset requested for a registered account");
            }

            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR,
                    "If that email is registered, a reset link has been sent.");
        } catch (RuntimeException e) {
            Log.e(TAG, "Password reset failed: " + e.getClass().getSimpleName());
            return AuthResult.failure(AuthResult.Failure.STORAGE_ERROR,
                    "Could not process the request. Please try again.");
        }
    }

    /** Adopt password changed remotely. */
    @WorkerThread
    private boolean adoptPasswordChangedRemotely(User user, String email, String password) {
        if (!firebaseAuth.isConfigured()) {
            return false;
        }
        if (!firebaseAuth.signInEmailPassword(email, password).isSuccess()) {
            return false;
        }

        String refreshed = PasswordHasher.hash(password);
        userDao.updatePasswordHash(user.getId(), refreshed);
        user.setPasswordHash(refreshed);
        Log.i(TAG, "Local credential refreshed after a remote password reset");
        return true;
    }

    /** Complete password reset. */
    @WorkerThread
    public AuthResult completePasswordReset(String email, String newPassword) {
        Validators.Result passwordCheck = Validators.validatePassword(newPassword);
        if (!passwordCheck.isValid()) {
            return AuthResult.failure(AuthResult.Failure.VALIDATION_ERROR,
                    passwordCheck.getMessage());
        }
        String normalisedEmail = User.normaliseEmail(email);
        User user = userDao.findByEmail(normalisedEmail);
        if (user == null) {
            return AuthResult.failure(AuthResult.Failure.INVALID_CREDENTIALS,
                    "Could not reset the password for that account");
        }
        userDao.updatePasswordHash(user.getId(), PasswordHasher.hash(newPassword));
        attemptTracker.recordSuccess(normalisedEmail);
        user.setPasswordHash(PasswordHasher.hash(newPassword));
        return AuthResult.success(user);
    }

    /** Ends the session locally and remotely, then clears the stored user id. */
    public void signOut() {
        if (sessionManager != null) {
            sessionManager.endSession();
        }
        firebaseAuth.signOut();

        if (appContext != null) {
            NotificationHelper.postSignedOut(appContext);
        }
    }

    /** Current user. */
    @Nullable
    @WorkerThread
    public User currentUser() {
        if (sessionManager == null || !sessionManager.isSignedIn()) {
            return null;
        }
        return userDao.findById(sessionManager.getUserId());
    }

    /** Attempts remaining. */
    public int attemptsRemaining(String email) {
        return attemptTracker.attemptsRemaining(User.normaliseEmail(email));
    }

    /** Invalid credentials. */
    private AuthResult invalidCredentials(String email) {
        int remaining = attemptTracker.attemptsRemaining(email);
        String message = "Incorrect email or password";
        if (remaining <= 2 && remaining > 0) {
            message += ", " + remaining + " attempt" + (remaining == 1 ? "" : "s") + " remaining";
        }
        return AuthResult.failure(AuthResult.Failure.INVALID_CREDENTIALS, message);
    }

    /** Mirror. */
    private void mirror(String operation, FirebaseAuthGateway.Outcome outcome) {
        if (outcome.isFailure()) {
            Log.w(TAG, "Firebase mirror failed on " + operation + ": " + outcome.getMessage());
        }
    }

    /** Begin session. */
    private void beginSession(User user) {
        if (sessionManager != null) {
            sessionManager.beginSession(user.getId(), user.getEmail(),
                    user.getDisplayName(), user.getProvider());
        }
    }
}
