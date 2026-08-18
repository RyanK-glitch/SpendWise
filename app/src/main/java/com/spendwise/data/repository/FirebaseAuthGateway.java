package com.spendwise.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.spendwise.BuildConfig;
import com.spendwise.domain.Guard;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A thin wrapper around Firebase Authentication, treated as a remote call rather than
 * a library call. Every request blocks on a Task with a timeout, translates a remote
 * failure into a local Outcome, and reports itself unconfigured when
 * google-services.json is absent so the app still builds without a Firebase project.
 */
public final class FirebaseAuthGateway {
    private static final String TAG = "FirebaseAuthGateway";

    private static final long TIMEOUT_SECONDS = 30L;

    @Nullable
    private final Context appContext;

    private FirebaseAuthGateway(@Nullable Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    /** Builds the value. */
    public static FirebaseAuthGateway create(@NonNull Context context) {
        return new FirebaseAuthGateway(Guard.notNull(context, "context"));
    }

    /** Unconfigured. */
    public static FirebaseAuthGateway unconfigured() {
        return new FirebaseAuthGateway(null);
    }

    /**
     * False when google-services.json was never added, which lets the whole app run and be
     * tested without a Firebase project.
     */
    public boolean isConfigured() {
        return resolveAuth() != null;
    }

    /** Exchanges a Google ID token for a Firebase session. */
    @WorkerThread
    public Outcome signInWithGoogle(@Nullable String idToken) {
        FirebaseAuth auth = resolveAuth();
        if (auth == null) {
            return Outcome.notConfigured();
        }
        if (isBlank(idToken)) {
            return Outcome.failed("Google returned no ID token for this build");
        }
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        return awaitAuthentication(auth, auth.signInWithCredential(credential),
                "signInWithCredential");
    }

    /** Creates the remote half of a new account, so the password can later be reset by email. */
    @WorkerThread
    public Outcome registerEmailPassword(String email, String password) {
        FirebaseAuth auth = resolveAuth();
        if (auth == null) {
            return Outcome.notConfigured();
        }
        if (isBlank(email) || isBlank(password)) {
            return Outcome.failed("Email and password are both required");
        }

        Outcome created = awaitAuthentication(auth,
                auth.createUserWithEmailAndPassword(email, password), "createUser");
        if (created.isAlreadyRegistered()) {
            return signInEmailPassword(email, password);
        }
        return created;
    }

    /** Asks Firebase to verify a password the local hash has already rejected. */
    @WorkerThread
    public Outcome signInEmailPassword(String email, String password) {
        FirebaseAuth auth = resolveAuth();
        if (auth == null) {
            return Outcome.notConfigured();
        }
        if (isBlank(email) || isBlank(password)) {
            return Outcome.failed("Email and password are both required");
        }
        return awaitAuthentication(auth,
                auth.signInWithEmailAndPassword(email, password), "signInWithPassword");
    }

    /** Send password reset email. */
    @WorkerThread
    public Outcome sendPasswordResetEmail(String email) {
        FirebaseAuth auth = resolveAuth();
        if (auth == null) {
            return Outcome.notConfigured();
        }
        if (isBlank(email)) {
            return Outcome.failed("Email is required");
        }
        return await(auth.sendPasswordResetEmail(email), "sendPasswordResetEmail");
    }

    /** Ends the remote session. The local session is cleared separately. */
    public void signOut() {
        FirebaseAuth auth = resolveAuth();
        if (auth == null) {
            return;
        }
        try {
            auth.signOut();
        } catch (RuntimeException e) {
            Log.w(TAG, "Firebase sign-out failed: " + e.getClass().getSimpleName());
        }
    }

    /** Resolve auth. */
    @Nullable
    private FirebaseAuth resolveAuth() {
        if (!BuildConfig.FIREBASE_CONFIGURED || appContext == null) {
            return null;
        }
        try {
            if (FirebaseApp.getApps(appContext).isEmpty()
                    && FirebaseApp.initializeApp(appContext) == null) {
                return null;
            }
            return FirebaseAuth.getInstance();
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Firebase Auth unavailable: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** Runs a remote sign in and turns the result, or the failure, into a local Outcome. */
    private Outcome awaitAuthentication(FirebaseAuth auth, Task<?> task, String operation) {
        Outcome outcome = await(task, operation);
        if (!outcome.isSuccess()) {
            return outcome;
        }
        FirebaseUser user = auth.getCurrentUser();
        return Outcome.success(user == null ? null : user.getUid());
    }

    /**
     * Blocks on a Google Play Task with a timeout. Anything remote gets a deadline, because
     * a mobile connection can stall without ever failing.
     */
    private Outcome await(Task<?> task, String operation) {
        try {
            Tasks.await(task, TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return Outcome.success(null);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Log.w(TAG, operation + " was rejected: " + cause.getClass().getSimpleName());
            return Outcome.failed("Firebase rejected the request",
                    cause instanceof FirebaseAuthUserCollisionException);
        } catch (TimeoutException e) {
            Log.w(TAG, operation + " timed out after " + TIMEOUT_SECONDS + "s");
            return Outcome.failed("Firebase did not respond in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Outcome.failed("Interrupted while waiting for Firebase");
        } catch (RuntimeException e) {
            Log.w(TAG, operation + " failed: " + e.getClass().getSimpleName());
            return Outcome.failed("Firebase is unavailable");
        }
    }

    /** True when blank. */
    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }

    public static final class Outcome {
        public enum Status { SUCCESS, NOT_CONFIGURED, FAILED }

        private final Status status;
        @Nullable
        private final String uid;
        private final String message;
        private final boolean alreadyRegistered;

        private Outcome(Status status, @Nullable String uid, String message,
                        boolean alreadyRegistered) {
            this.status = status;
            this.uid = uid;
            this.message = message;
            this.alreadyRegistered = alreadyRegistered;
        }

        /** A remote call that returned what was asked for. */
        static Outcome success(@Nullable String uid) {
            return new Outcome(Status.SUCCESS, uid, "Signed in to Firebase", false);
        }

        /** Not configured. */
        static Outcome notConfigured() {
            return new Outcome(Status.NOT_CONFIGURED, null,
                    "Firebase is not configured for this build", false);
        }

        /** A remote call that did not, with the reason kept for the log. */
        static Outcome failed(String message) {
            return failed(message, false);
        }

        /** A remote call that did not, with the reason kept for the log. */
        static Outcome failed(String message, boolean alreadyRegistered) {
            return new Outcome(Status.FAILED, null, message, alreadyRegistered);
        }

        /** Returns the status. */
        public Status getStatus() {
            return status;
        }

        /** True when success. */
        public boolean isSuccess() {
            return status == Status.SUCCESS;
        }

        /** True when failure. */
        public boolean isFailure() {
            return status == Status.FAILED;
        }

        /** True when not configured. */
        public boolean isNotConfigured() {
            return status == Status.NOT_CONFIGURED;
        }

        /** True when already registered. */
        public boolean isAlreadyRegistered() {
            return alreadyRegistered;
        }

        /** Returns the uid. */
        @Nullable
        public String getUid() {
            return uid;
        }

        /** Returns the message. */
        public String getMessage() {
            return message;
        }

        @NonNull
        @Override
        public String toString() {
            return "Outcome{" + status + ": " + message + "}";
        }
    }
}
