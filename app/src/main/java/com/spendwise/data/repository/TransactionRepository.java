package com.spendwise.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.dao.TransactionDao;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.sync.FirestoreSync;
import com.spendwise.data.sync.SyncResult;
import com.spendwise.domain.Category;
import com.spendwise.domain.Guard;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionFilter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The only route to ledger data. Runs the filter as SQL so a long ledger is never
 * loaded into memory, escapes LIKE wildcards in the search term, and mirrors every
 * write to Cloud Firestore when sync is switched on.
 */
public class TransactionRepository {
    private static final String TAG = "TransactionRepository";

    private final TransactionDao transactionDao;
    private final Executor ioExecutor;
    private final FirestoreSync sync;

    public TransactionRepository(Context context) {
        this(SpendWiseDatabase.getInstance(context).transactionDao(),
                SpendWiseDatabase.IO_EXECUTOR,
                FirestoreSync.create(context));
    }

    public TransactionRepository(TransactionDao transactionDao, Executor ioExecutor) {
        this(transactionDao, ioExecutor, FirestoreSync.disabled());
    }

    public TransactionRepository(TransactionDao transactionDao, Executor ioExecutor,
                                 FirestoreSync sync) {
        this.transactionDao = Guard.notNull(transactionDao, "transactionDao");
        this.ioExecutor = Guard.notNull(ioExecutor, "ioExecutor");
        this.sync = Guard.notNull(sync, "sync");
    }

    /** Returns the rows as LiveData, so the screen updates itself. */
    public LiveData<List<Transaction>> observeAll(long userId) {
        return transactionDao.observeAll(userId);
    }

    /** Returns the recent as LiveData, so the screen updates itself. */
    public LiveData<List<Transaction>> observeRecent(long userId, int limit) {
        return transactionDao.observeRecent(userId, limit);
    }

    /** Runs the filter as SQL so the whole ledger never has to be read into memory. */
    public LiveData<List<Transaction>> observeFiltered(long userId, TransactionFilter filter) {
        Guard.notNull(filter, "filter");
        Bindings b = Bindings.from(filter);
        return transactionDao.filter(userId, b.query, b.categories, b.categories.size(),
                b.methods, b.methods.size(), b.type, b.fromDay, b.toDay,
                b.minAmount, b.maxAmount);
    }

    /** Filter blocking. */
    @WorkerThread
    public List<Transaction> filterBlocking(long userId, TransactionFilter filter) {
        Guard.notNull(filter, "filter");
        Bindings b = Bindings.from(filter);
        return transactionDao.filterBlocking(userId, b.query, b.categories, b.categories.size(),
                b.methods, b.methods.size(), b.type, b.fromDay, b.toDay,
                b.minAmount, b.maxAmount);
    }

    /** Search blocking. */
    @WorkerThread
    public List<Transaction> searchBlocking(long userId, TransactionFilter filter) {
        return filterBlocking(userId, filter);
    }

    /** Writes the value to storage. */
    public void insert(Transaction transaction, Runnable onComplete) {
        Guard.notNull(transaction, "transaction");
        ioExecutor.execute(() -> {
            long rowId = transactionDao.insert(transaction);
            if (rowId > 0) {
                transaction.setId(rowId);
            }
            sync.pushTransaction(transaction);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /** Updates the value. */
    public void update(Transaction transaction, Runnable onComplete) {
        ioExecutor.execute(() -> {
            transactionDao.update(transaction);
            sync.pushTransaction(transaction);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /** Removes the value. */
    public void delete(Transaction transaction, Runnable onComplete) {
        ioExecutor.execute(() -> {
            long deletedId = transaction.getId();
            transactionDao.delete(transaction);
            sync.deleteTransaction(deletedId);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Pulls the remote ledger and replaces the local rows with it. Last write wins, so
     * whichever device synchronised most recently is the copy that survives.
     */
    public void pullAndReconcile(long userId, @Nullable Runnable onComplete) {
        ioExecutor.execute(() -> {
            sync.pullAll(result -> reconcile(userId, result));
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Pulls the remote ledger and replaces the local rows with it. Last write wins, so
     * whichever device synchronised most recently is the copy that survives.
     */
    public void pullAndReconcile(long userId) {
        pullAndReconcile(userId, null);
    }

    /** Reconcile. */
    @WorkerThread
    private void reconcile(long userId, SyncResult result) {
        if (!result.isSuccess()) {
            return;
        }
        int restored = 0;
        for (Transaction incoming : result.getTransactions()) {
            try {
                if (incoming.getId() <= 0 || transactionDao.findById(incoming.getId()) != null) {
                    continue;
                }
                if (userId > 0) {
                    incoming.setUserId(userId);
                }
                transactionDao.insert(incoming);
                restored++;
            } catch (RuntimeException e) {
                Log.w(TAG, "Skipped remote transaction " + incoming.getId() + ": "
                        + e.getClass().getSimpleName());
            }
        }
        if (restored > 0) {
            Log.i(TAG, "Restored " + restored + " transactions from Firestore");
        }
    }

    /** Count for user. */
    @WorkerThread
    public int countForUser(long userId) {
        return transactionDao.countForUser(userId);
    }

    /** Returns the rows. */
    @WorkerThread
    public List<Transaction> getAll(long userId) {
        return transactionDao.getAll(userId);
    }

    /** Escapes the LIKE wildcards. Without this, searching for 100% would match every row. */
    static String escapeLikePattern(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    private static final class Bindings {
        final String query;
        final List<String> categories;
        final List<String> methods;
        final String type;
        final Long fromDay;
        final Long toDay;
        final long minAmount;
        final long maxAmount;

        private Bindings(String query, List<String> categories, List<String> methods,
                         String type, Long fromDay, Long toDay,
                         long minAmount, long maxAmount) {
            this.query = query;
            this.categories = categories;
            this.methods = methods;
            this.type = type;
            this.fromDay = fromDay;
            this.toDay = toDay;
            this.minAmount = minAmount;
            this.maxAmount = maxAmount;
        }

        /** From. */
        static Bindings from(TransactionFilter filter) {
            List<String> categories = new ArrayList<>();
            for (Category c : filter.getCategories()) {
                categories.add(c.name());
            }
            List<String> methods = new ArrayList<>();
            for (PaymentMethod m : filter.getPaymentMethods()) {
                methods.add(m.name());
            }

            LocalDate from = filter.getFrom();
            LocalDate to = filter.getTo();

            return new Bindings(
                    escapeLikePattern(filter.getQuery()),
                    categories,
                    methods,
                    filter.getType() == null ? null : filter.getType().name(),
                    from == null ? null : from.toEpochDay(),
                    to == null ? null : to.toEpochDay(),
                    filter.getMinAmountMinor(),
                    filter.getMaxAmountMinor());
        }
    }
}
