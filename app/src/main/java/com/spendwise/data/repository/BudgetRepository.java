package com.spendwise.data.repository;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;

import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.dao.BudgetDao;
import com.spendwise.data.dao.TransactionDao;
import com.spendwise.data.entity.Budget;
import com.spendwise.data.sync.FirestoreSync;
import com.spendwise.data.sync.SyncResult;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Category;
import com.spendwise.domain.Guard;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * The only route to budget data. Reads come back as LiveData and writes are handed to
 * an IO executor, so no database work ever happens on the UI thread.
 */
public class BudgetRepository {
    private static final String TAG = "BudgetRepository";

    private final BudgetDao budgetDao;
    private final TransactionDao transactionDao;
    private final Executor ioExecutor;
    private final FirestoreSync sync;

    public BudgetRepository(Context context) {
        this(SpendWiseDatabase.getInstance(context).budgetDao(),
                SpendWiseDatabase.getInstance(context).transactionDao(),
                SpendWiseDatabase.IO_EXECUTOR,
                FirestoreSync.create(context));
    }

    public BudgetRepository(BudgetDao budgetDao, TransactionDao transactionDao, Executor ioExecutor) {
        this(budgetDao, transactionDao, ioExecutor, FirestoreSync.disabled());
    }

    public BudgetRepository(BudgetDao budgetDao, TransactionDao transactionDao,
                            Executor ioExecutor, FirestoreSync sync) {
        this.budgetDao = Guard.notNull(budgetDao, "budgetDao");
        this.transactionDao = Guard.notNull(transactionDao, "transactionDao");
        this.ioExecutor = Guard.notNull(ioExecutor, "ioExecutor");
        this.sync = Guard.notNull(sync, "sync");
    }

    /** Returns the current month as LiveData, so the screen updates itself. */
    public LiveData<List<Budget>> observeCurrentMonth(long userId) {
        YearMonth now = YearMonth.now();
        return budgetDao.observeForPeriod(userId, now.getYear(), now.getMonthValue());
    }

    /** Returns the for period as LiveData, so the screen updates itself. */
    public LiveData<List<Budget>> observeForPeriod(long userId, YearMonth period) {
        return budgetDao.observeForPeriod(userId, period.getYear(), period.getMonthValue());
    }

    /** Returns the for period. */
    @WorkerThread
    public List<Budget> getForPeriod(long userId, YearMonth period) {
        return budgetDao.getForPeriod(userId, period.getYear(), period.getMonthValue());
    }

    /** Upsert. */
    public void upsert(Budget budget, Runnable onComplete) {
        Guard.notNull(budget, "budget");
        ioExecutor.execute(() -> {
            Budget existing = budgetDao.findOne(budget.getUserId(), budget.getCategory(),
                    budget.getYear(), budget.getMonth());
            long replacedId = existing == null ? 0L : existing.getId();
            long rowId = budgetDao.insert(budget);
            if (rowId > 0) {
                budget.setId(rowId);
            }
            if (replacedId > 0 && replacedId != budget.getId()) {
                sync.deleteBudget(replacedId);
            }
            sync.pushBudget(budget);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /** Removes the value. */
    public void delete(Budget budget, Runnable onComplete) {
        ioExecutor.execute(() -> {
            long deletedId = budget.getId();
            budgetDao.delete(budget);
            sync.deleteBudget(deletedId);
            if (onComplete != null) {
                onComplete.run();
            }
        });
    }

    /**
     * Pulls the remote budgets and replaces the local ones with them, on the same last
     * write wins basis as the ledger.
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
     * Pulls the remote budgets and replaces the local ones with them, on the same last
     * write wins basis as the ledger.
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
        for (Budget incoming : result.getBudgets()) {
            long remoteId = incoming.getId();
            try {
                long owner = userId > 0 ? userId : incoming.getUserId();
                if (owner <= 0) {
                    continue;
                }
                if (budgetDao.findOne(owner, incoming.getCategory(),
                        incoming.getYear(), incoming.getMonth()) != null) {
                    continue;
                }
                incoming.setUserId(owner);
                incoming.setId(0L);
                long rowId = budgetDao.insert(incoming);
                if (rowId <= 0) {
                    continue;
                }
                incoming.setId(rowId);
                restored++;
                if (rowId != remoteId) {
                    sync.pushBudget(incoming);
                    sync.deleteBudget(remoteId);
                }
            } catch (RuntimeException e) {
                Log.w(TAG, "Skipped remote budget " + remoteId + ": "
                        + e.getClass().getSimpleName());
            }
        }
        if (restored > 0) {
            Log.i(TAG, "Restored " + restored + " budgets from Firestore");
        }
    }

    /** How much has gone out in one category this period. */
    @WorkerThread
    public long spentInCategory(long userId, Category category, YearMonth period) {
        Guard.notNull(category, "category");
        Guard.notNull(period, "period");
        LocalDate first = period.atDay(1);
        LocalDate last = period.atEndOfMonth();
        return transactionDao.sumExpenseForCategory(
                userId, category.name(), first.toEpochDay(), last.toEpochDay());
    }

    @WorkerThread
    public List<BudgetCalculator.BudgetStatus> evaluateAll(long userId, YearMonth period) {
        List<Budget> budgets = getForPeriod(userId, period);
        List<BudgetCalculator.BudgetStatus> statuses = new ArrayList<>();
        for (Budget budget : budgets) {
            if (budget.getLimitMinor() <= 0) {
                continue;
            }
            long spent = spentInCategory(userId, budget.categoryAsEnum(), period);
            statuses.add(BudgetCalculator.evaluate(budget, syntheticLedgerFor(budget, spent)));
        }
        return statuses;
    }

    private List<com.spendwise.data.entity.Transaction> syntheticLedgerFor(Budget budget, long spent) {
        List<com.spendwise.data.entity.Transaction> ledger = new ArrayList<>(1);
        if (spent <= 0) {
            return ledger;
        }
        com.spendwise.data.entity.Transaction t = new com.spendwise.data.entity.Transaction();
        t.setUserId(budget.getUserId());
        t.setDescription("aggregate");
        t.setAmountMinor(spent);
        t.setType(com.spendwise.domain.TransactionType.EXPENSE.name());
        t.setCategory(budget.getCategory());
        t.setPaymentMethod(com.spendwise.domain.PaymentMethod.CARD.name());
        t.setDate(budget.period().atDay(1));
        ledger.add(t);
        return ledger;
    }

    /** Mark alert sent. */
    public void markAlertSent(long budgetId) {
        ioExecutor.execute(() ->
                budgetDao.markAlertSent(budgetId, System.currentTimeMillis()));
    }

    /** Count for user. */
    @WorkerThread
    public int countForUser(long userId) {
        return budgetDao.countForUser(userId);
    }
}
