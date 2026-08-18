package com.spendwise.notification;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.spendwise.data.SessionManager;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.repository.BudgetRepository;
import com.spendwise.data.repository.TransactionRepository;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.TransactionType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Re-checks every budget on a WorkManager schedule and raises an alert when one is
 * close to or past its limit. WorkManager rather than a thread or an alarm, because
 * its queue is written to disk and survives both Doze and a reboot, which is exactly
 * when an overnight budget check matters. A sent timestamp keeps a fifteen minute
 * schedule from announcing the same overspend four times an hour.
 */
public class BudgetAlertWorker extends Worker {
    private static final String TAG = "BudgetAlertWorker";
    private static final String UNIQUE_WORK_NAME = "spendwise_budget_alerts";

    private static final long REPEAT_INTERVAL_MINUTES = 15L;

    private static final long RE_ALERT_COOLDOWN_MILLIS = 12 * 60 * 60 * 1000L;

    private static final String PREFS_NAME = "spendwise_notification_state";
    private static final String KEY_LAST_SUMMARY_PREFIX = "weekly_summary_at_";
    private static final long SUMMARY_INTERVAL_MILLIS = 7 * 24 * 60 * 60 * 1000L;
    private static final int SUMMARY_WINDOW_DAYS = 7;

    public BudgetAlertWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    /** Schedule. */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                BudgetAlertWorker.class, REPEAT_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request);
    }

    /** Cancel. */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME);
    }

    /**
     * Runs on WorkManager's own thread, off the main thread by construction of the API.
     * Re-checks every budget and raises an alert for any that crossed a threshold.
     */
    @NonNull
    @Override
    public Result doWork() {
        try {
            SessionManager session = SessionManager.getInstance(getApplicationContext());
            if (!session.isSignedIn()) {
                return Result.success();
            }
            long userId = session.getUserId();

            runBudgetChecks(userId);

            try {
                maybePostWeeklySummary(userId);
            } catch (RuntimeException e) {
                Log.w(TAG, "Weekly summary skipped: " + e.getClass().getSimpleName());
            }
            return Result.success();
        } catch (RuntimeException e) {
            Log.e(TAG, "Budget evaluation failed: " + e.getClass().getSimpleName());
            return Result.retry();
        }
    }

    /** Run budget checks. */
    private void runBudgetChecks(long userId) {
        BudgetRepository repository = new BudgetRepository(getApplicationContext());
        List<BudgetCalculator.BudgetStatus> statuses =
                repository.evaluateAll(userId, YearMonth.now());

        long now = System.currentTimeMillis();
        for (BudgetCalculator.BudgetStatus status : statuses) {
            if (!status.requiresAlert()) {
                continue;
            }
            long lastAlert = status.getBudget().getAlertSentAt();
            if (now - lastAlert < RE_ALERT_COOLDOWN_MILLIS) {
                continue;
            }
            if (NotificationHelper.postBudgetAlert(getApplicationContext(), status)) {
                repository.markAlertSent(status.getBudget().getId());
            }
        }
    }

    /** Maybe post weekly summary. */
    private void maybePostWeeklySummary(long userId) {
        Context context = getApplicationContext();
        SharedPreferences prefs =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String key = KEY_LAST_SUMMARY_PREFIX + userId;

        long now = System.currentTimeMillis();
        if (now - prefs.getLong(key, 0L) < SUMMARY_INTERVAL_MILLIS) {
            return;
        }
        if (!NotificationHelper.canPostNotifications(context)
                || !NotificationHelper.isChannelEnabled(
                        context, NotificationHelper.CHANNEL_SUMMARIES)) {
            return;
        }

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.minusDays(SUMMARY_WINDOW_DAYS);

        long spentMinor = 0L;
        int count = 0;
        for (Transaction transaction : new TransactionRepository(context).getAll(userId)) {
            LocalDate date = transaction.getDate();
            if (date == null || !date.isAfter(cutoff) || date.isAfter(today)) {
                continue;
            }
            if (transaction.typeAsEnum() != TransactionType.EXPENSE) {
                continue;
            }
            spentMinor += Math.abs(transaction.getAmountMinor());
            count++;
        }

        if (count == 0) {
            return;
        }
        if (NotificationHelper.postWeeklySummary(context, spentMinor, count)) {
            prefs.edit().putLong(key, now).apply();
        }
    }
}
