package com.spendwise.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.firestore.QuerySnapshot;
import com.spendwise.BuildConfig;
import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;
import com.spendwise.domain.Guard;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Replication between the phone and Cloud Firestore. Writes are pushed a row at a
 * time and the ledger is pulled back on sign in, so a user who reinstalls does not
 * lose their history.
 *
 * This is the class the CN6035 paper presentation criticises. pullAll() reads the
 * whole collection rather than the rows that changed, which is the same wholesale
 * transfer that Starobinski, Trachtenberg and Agarwal (2003) attack in Palm HotSync.
 * Conflicts are resolved by last write wins, so a row edited on two devices while
 * offline keeps whichever copy synchronises second.
 */
public final class FirestoreSync {
    private static final String TAG = "FirestoreSync";

    private static final String USERS = "users";
    private static final String TRANSACTIONS = "transactions";
    private static final String BUDGETS = "budgets";

    private static final long PULL_TIMEOUT_SECONDS = 20L;

    private static final AtomicBoolean SETTINGS_APPLIED = new AtomicBoolean(false);

    @Nullable
    private final Context appContext;

    @Nullable
    private volatile FirebaseFirestore firestore;

    public interface PullCallback {
        void onPulled(SyncResult result);
    }

    private FirestoreSync(@Nullable Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    /** Builds a live syncer for a signed-in user, or the disabled one if Firebase is absent. */
    public static FirestoreSync create(@NonNull Context context) {
        return new FirestoreSync(Guard.notNull(context, "context"));
    }

    /**
     * A do-nothing syncer, so callers never have to null check. Every method succeeds
     * quietly and reports that sync is off.
     */
    public static FirestoreSync disabled() {
        return new FirestoreSync(null);
    }

    /**
     * True when Firebase is configured and a user is signed in, so callers can tell the
     * user where their data actually lives.
     */
    public boolean isEnabled() {
        return collection(TRANSACTIONS) != null;
    }

    /**
     * Mirrors one saved transaction upward. Fire and forget: the local database is the
     * source of truth, so a failed push must not fail the user's save.
     */
    public void pushTransaction(@Nullable Transaction transaction) {
        if (transaction == null || transaction.getId() <= 0) {
            return;
        }
        try {
            CollectionReference collection = collection(TRANSACTIONS);
            if (collection == null) {
                return;
            }
            set(collection, String.valueOf(transaction.getId()), toMap(transaction),
                    "transaction");
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Transaction push skipped: " + e.getClass().getSimpleName());
        }
    }

    /** Removes a deleted row from the remote copy so it does not come back on the next pull. */
    public void deleteTransaction(long transactionId) {
        delete(TRANSACTIONS, transactionId, "transaction");
    }

    /** Mirrors one saved budget upward, on the same fire and forget basis as a transaction. */
    public void pushBudget(@Nullable Budget budget) {
        if (budget == null || budget.getId() <= 0) {
            return;
        }
        try {
            CollectionReference collection = collection(BUDGETS);
            if (collection == null) {
                return;
            }
            set(collection, String.valueOf(budget.getId()), toMap(budget), "budget");
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Budget push skipped: " + e.getClass().getSimpleName());
        }
    }

    /** Removes a deleted budget from the remote copy. */
    public void deleteBudget(long budgetId) {
        delete(BUDGETS, budgetId, "budget");
    }

    /** One remote write, with the failure logged rather than thrown. */
    private void set(CollectionReference collection, String documentId,
                     Map<String, Object> values, String what) {
        collection.document(documentId).set(values)
                .addOnFailureListener(e ->
                        Log.w(TAG, "Deferred " + what + " push failed: "
                                + e.getClass().getSimpleName()));
    }

    /** One remote delete, ignored quietly when there is nothing to delete. */
    private void delete(String collectionName, long id, String what) {
        if (id <= 0) {
            return;
        }
        try {
            CollectionReference collection = collection(collectionName);
            if (collection == null) {
                return;
            }
            collection.document(String.valueOf(id)).delete()
                    .addOnFailureListener(e ->
                            Log.w(TAG, "Deferred " + what + " delete failed: "
                                    + e.getClass().getSimpleName()));
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Delete of " + what + " skipped: " + e.getClass().getSimpleName());
        }
    }

    /**
     * Pulls the user's ledger back from Firestore on sign in. Note what this does not do:
     * it asks for the whole collection rather than the rows that changed since last time.
     */
    @WorkerThread
    public void pullAll(@Nullable PullCallback callback) {
        if (callback == null) {
            return;
        }
        SyncResult result;
        try {
            CollectionReference transactions = collection(TRANSACTIONS);
            CollectionReference budgets = collection(BUDGETS);
            result = transactions == null || budgets == null
                    ? SyncResult.disabled()
                    : fetch(transactions, budgets);
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Pull failed: " + e.getClass().getSimpleName());
            result = SyncResult.failed("Firestore is unavailable");
        }
        callback.onPulled(result);
    }

    /**
     * The blocking half of the pull. Two whole-collection reads, each with its own timeout
     * so a dead network cannot hang the caller for ever.
     */
    private SyncResult fetch(CollectionReference transactions, CollectionReference budgets) {
        try {
            QuerySnapshot remoteTransactions =
                    // .get() with no filter: the whole collection, however little has changed.
                    Tasks.await(transactions.get(), PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            QuerySnapshot remoteBudgets =
                    Tasks.await(budgets.get(), PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            return SyncResult.success(readTransactions(remoteTransactions),
                    readBudgets(remoteBudgets));
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            Log.w(TAG, "Pull was rejected: " + cause.getClass().getSimpleName());
            return SyncResult.failed("Firestore rejected the read");
        } catch (TimeoutException e) {
            Log.w(TAG, "Pull timed out after " + PULL_TIMEOUT_SECONDS + "s");
            return SyncResult.failed("Firestore did not respond in time");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SyncResult.failed("Interrupted while waiting for Firestore");
        } catch (RuntimeException e) {
            Log.w(TAG, "Pull failed: " + e.getClass().getSimpleName());
            return SyncResult.failed("Firestore is unavailable");
        }
    }

    /** Turns remote documents back into Transaction objects. */
    private List<Transaction> readTransactions(@Nullable QuerySnapshot snapshot) {
        List<Transaction> out = new ArrayList<>();
        if (snapshot == null) {
            return out;
        }
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Transaction transaction = toTransaction(document);
            if (transaction == null) {
                Log.w(TAG, "Ignored malformed remote transaction " + document.getId());
            } else {
                out.add(transaction);
            }
        }
        return out;
    }

    /** Turns remote documents back into Budget objects. */
    private List<Budget> readBudgets(@Nullable QuerySnapshot snapshot) {
        List<Budget> out = new ArrayList<>();
        if (snapshot == null) {
            return out;
        }
        for (DocumentSnapshot document : snapshot.getDocuments()) {
            Budget budget = toBudget(document);
            if (budget == null) {
                Log.w(TAG, "Ignored malformed remote budget " + document.getId());
            } else {
                out.add(budget);
            }
        }
        return out;
    }

    /** Flattens a row into the field map Firestore stores. */
    private Map<String, Object> toMap(Transaction t) {
        LocalDate date = t.getDate() == null ? LocalDate.now() : t.getDate();
        Map<String, Object> values = new HashMap<>();
        values.put("id", t.getId());
        values.put("userId", t.getUserId());
        values.put("description", t.getDescription());
        values.put("note", t.getNote());
        values.put("amountMinor", t.getAmountMinor());
        values.put("type", t.getType());
        values.put("category", t.getCategory());
        values.put("paymentMethod", t.getPaymentMethod());
        values.put("dateEpochDay", date.toEpochDay());
        values.put("date", date.toString());
        values.put("createdAt", t.getCreatedAt());
        values.put("updatedAt", System.currentTimeMillis());
        return values;
    }

    /** Flattens a row into the field map Firestore stores. */
    private Map<String, Object> toMap(Budget b) {
        Map<String, Object> values = new HashMap<>();
        values.put("id", b.getId());
        values.put("userId", b.getUserId());
        values.put("category", b.getCategory());
        values.put("limitMinor", b.getLimitMinor());
        values.put("year", b.getYear());
        values.put("month", b.getMonth());
        values.put("alertThresholdPercent", b.getAlertThresholdPercent());
        values.put("alertSentAt", b.getAlertSentAt());
        values.put("updatedAt", System.currentTimeMillis());
        return values;
    }

    /** To transaction. */
    @Nullable
    private Transaction toTransaction(DocumentSnapshot document) {
        long id = documentId(document);
        long amountMinor = number(document, "amountMinor");
        String description = text(document, "description");
        LocalDate date = date(document);
        if (id <= 0 || amountMinor <= 0 || description == null || date == null) {
            return null;
        }
        Transaction t = new Transaction();
        t.setId(id);
        t.setUserId(number(document, "userId"));
        t.setDescription(description);
        t.setNote(text(document, "note"));
        t.setAmountMinor(amountMinor);
        String type = text(document, "type");
        if (type != null) {
            t.setType(type);
        }
        String category = text(document, "category");
        if (category != null) {
            t.setCategory(category);
        }
        String paymentMethod = text(document, "paymentMethod");
        if (paymentMethod != null) {
            t.setPaymentMethod(paymentMethod);
        }
        t.setDate(date);
        t.setCreatedAt(number(document, "createdAt"));
        return t;
    }

    /** To budget. */
    @Nullable
    private Budget toBudget(DocumentSnapshot document) {
        long id = documentId(document);
        long limitMinor = number(document, "limitMinor");
        String category = text(document, "category");
        int year = (int) number(document, "year");
        int month = (int) number(document, "month");
        if (id <= 0 || limitMinor <= 0 || category == null || year <= 0 || month < 1 || month > 12) {
            return null;
        }
        Budget b = new Budget();
        b.setId(id);
        b.setUserId(number(document, "userId"));
        b.setCategory(category);
        b.setLimitMinor(limitMinor);
        b.setYear(year);
        b.setMonth(month);
        int threshold = (int) number(document, "alertThresholdPercent");
        b.setAlertThresholdPercent(threshold <= 0
                ? Budget.DEFAULT_ALERT_THRESHOLD_PERCENT : threshold);
        b.setAlertSentAt(number(document, "alertSentAt"));
        return b;
    }

    /** Document id. */
    private long documentId(DocumentSnapshot document) {
        try {
            return Long.parseLong(document.getId());
        } catch (NumberFormatException e) {
            return number(document, "id");
        }
    }

    /** Date. */
    @Nullable
    private LocalDate date(DocumentSnapshot document) {
        long epochDay = number(document, "dateEpochDay");
        if (epochDay != 0L) {
            try {
                return LocalDate.ofEpochDay(epochDay);
            } catch (RuntimeException e) {
                return null;
            }
        }
        String iso = text(document, "date");
        if (iso == null) {
            return null;
        }
        try {
            return LocalDate.parse(iso);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** Number. */
    private long number(DocumentSnapshot document, String field) {
        Object raw = document.get(field);
        if (raw instanceof Number) {
            return ((Number) raw).longValue();
        }
        if (raw instanceof String) {
            try {
                return Long.parseLong(((String) raw).trim());
            } catch (NumberFormatException e) {
                return 0L;
            }
        }
        return 0L;
    }

    /** Reads a string field out of a remote document, defaulting rather than throwing. */
    @Nullable
    private String text(DocumentSnapshot document, String field) {
        Object raw = document.get(field);
        if (!(raw instanceof String)) {
            return null;
        }
        String value = ((String) raw).trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * The per-user path, users/{uid}/{name}. Scoping by uid is what keeps one account's
     * ledger out of another's.
     */
    @Nullable
    private CollectionReference collection(String name) {
        if (!BuildConfig.FIREBASE_CONFIGURED || appContext == null) {
            return null;
        }
        String uid = currentUid();
        if (uid == null) {
            return null;
        }
        FirebaseFirestore db = resolveFirestore();
        if (db == null) {
            return null;
        }
        try {
            return db.collection(USERS).document(uid).collection(name);
        } catch (RuntimeException e) {
            Log.w(TAG, "Collection unavailable: " + e.getClass().getSimpleName());
            return null;
        }
    }

    /** Resolve firestore. */
    @Nullable
    private FirebaseFirestore resolveFirestore() {
        if (!BuildConfig.FIREBASE_CONFIGURED || appContext == null) {
            return null;
        }
        FirebaseFirestore local = firestore;
        if (local != null) {
            return local;
        }
        synchronized (this) {
            if (firestore != null) {
                return firestore;
            }
            try {
                if (FirebaseApp.getApps(appContext).isEmpty()
                        && FirebaseApp.initializeApp(appContext) == null) {
                    return null;
                }
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                enablePersistence(db);
                firestore = db;
                return db;
            } catch (RuntimeException | LinkageError e) {
                Log.w(TAG, "Firestore unavailable: " + e.getClass().getSimpleName());
                return null;
            }
        }
    }

    /** Enable persistence. */
    private static void enablePersistence(FirebaseFirestore db) {
        if (!SETTINGS_APPLIED.compareAndSet(false, true)) {
            return;
        }
        try {
            db.setFirestoreSettings(new FirebaseFirestoreSettings.Builder(db.getFirestoreSettings())
                    .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                    .build());
        } catch (RuntimeException e) {
            Log.w(TAG, "Firestore settings not applied: " + e.getClass().getSimpleName());
        }
    }

    /** Current uid. */
    @Nullable
    private String currentUid() {
        try {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            return user == null ? null : user.getUid();
        } catch (RuntimeException | LinkageError e) {
            Log.w(TAG, "Firebase Auth unavailable: " + e.getClass().getSimpleName());
            return null;
        }
    }
}
