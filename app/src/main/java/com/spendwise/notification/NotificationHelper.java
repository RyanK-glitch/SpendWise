package com.spendwise.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.spendwise.R;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.Guard;
import com.spendwise.domain.TransactionType;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.MainActivity;
import com.spendwise.util.CurrencyFormatter;

/**
 * Creates the four notification channels and posts every notification the app sends.
 * Each PendingIntent is immutable, so no other app can rewrite it and launch this one
 * with extras of its own choosing.
 */
public final class NotificationHelper {
    public static final String CHANNEL_BUDGET_ALERTS = "budget_alerts";
    public static final String CHANNEL_SUMMARIES = "weekly_summaries";
    public static final String CHANNEL_ACCOUNT_ACTIVITY = "account_activity";
    public static final String CHANNEL_TRANSACTION_ACTIVITY = "transaction_activity";

    private static final String CHANNEL_RETIRED_BILLS = "bill_reminders";

    public static final String EXTRA_DESTINATION = "com.spendwise.extra.DESTINATION";
    public static final String DESTINATION_BUDGETS = "budgets";
    public static final String DESTINATION_TRANSACTIONS = "transactions";
    public static final String DESTINATION_REPORTS = "reports";

    private static final int ID_SIGNED_IN = 4000;
    private static final int ID_SIGNED_OUT = 4001;
    private static final int ID_INCOME_RECORDED = 4100;
    private static final int ID_EXPENSE_RECORDED = 4101;

    public enum SignInMethod {
        EMAIL(R.string.notif_x_method_email),
        GOOGLE(R.string.notif_x_method_google);

        @StringRes
        private final int labelRes;

        SignInMethod(@StringRes int labelRes) {
            this.labelRes = labelRes;
        }
    }

    private NotificationHelper() {
    }

    /** Builds the channels. */
    public static void createChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }
        createChannelsApi26(context);
    }

    /** Builds the channels api26. */
    @RequiresApi(Build.VERSION_CODES.O)
    private static void createChannelsApi26(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        NotificationChannel budgets = new NotificationChannel(
                CHANNEL_BUDGET_ALERTS,
                context.getString(R.string.channel_budget_alerts),
                NotificationManager.IMPORTANCE_HIGH);
        budgets.setDescription(context.getString(R.string.channel_budget_alerts_desc));
        budgets.enableVibration(true);

        NotificationChannel summaries = new NotificationChannel(
                CHANNEL_SUMMARIES,
                context.getString(R.string.channel_summaries),
                NotificationManager.IMPORTANCE_LOW);
        summaries.setDescription(context.getString(R.string.channel_summaries_desc));

        NotificationChannel account = new NotificationChannel(
                CHANNEL_ACCOUNT_ACTIVITY,
                context.getString(R.string.notif_x_channel_account),
                NotificationManager.IMPORTANCE_DEFAULT);
        account.setDescription(context.getString(R.string.notif_x_channel_account_desc));

        NotificationChannel ledger = new NotificationChannel(
                CHANNEL_TRANSACTION_ACTIVITY,
                context.getString(R.string.notif_x_channel_transaction),
                NotificationManager.IMPORTANCE_DEFAULT);
        ledger.setDescription(context.getString(R.string.notif_x_channel_transaction_desc));

        manager.createNotificationChannel(budgets);
        manager.createNotificationChannel(summaries);
        manager.createNotificationChannel(account);
        manager.createNotificationChannel(ledger);

        manager.deleteNotificationChannel(CHANNEL_RETIRED_BILLS);
    }

    /** Can post notifications. */
    public static boolean canPostNotifications(Context context) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true;
        }
        return ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** True when channel enabled. */
    public static boolean isChannelEnabled(Context context, String channelId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return true;
        }
        return importanceOf(context, channelId) != NotificationManager.IMPORTANCE_NONE;
    }

    /** Importance of. */
    @RequiresApi(Build.VERSION_CODES.O)
    private static int importanceOf(Context context, String channelId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return NotificationManager.IMPORTANCE_NONE;
        }
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        return channel == null ? NotificationManager.IMPORTANCE_NONE : channel.getImportance();
    }

    /** True when known channel. */
    public static boolean isKnownChannel(@Nullable String channelId) {
        return CHANNEL_BUDGET_ALERTS.equals(channelId)
                || CHANNEL_SUMMARIES.equals(channelId)
                || CHANNEL_ACCOUNT_ACTIVITY.equals(channelId)
                || CHANNEL_TRANSACTION_ACTIVITY.equals(channelId);
    }

    /** Post budget alert. */
    public static boolean postBudgetAlert(Context context, BudgetCalculator.BudgetStatus status) {
        Guard.notNull(status, "status");
        Category category = status.getBudget().categoryAsEnum();

        boolean exceeded = status.getState() == BudgetCalculator.BudgetState.EXCEEDED;
        Currency currency = CurrencyManager.getCurrency(context);
        String title = exceeded
                ? context.getString(R.string.notif_budget_exceeded_title,
                        category.getDisplayName())
                : context.getString(R.string.notif_budget_warning_title,
                        category.getDisplayName());

        String body = exceeded
                ? context.getString(R.string.notif_budget_exceeded_body,
                        CurrencyFormatter.formatDisplay(status.getOverspendMinor(), currency),
                        CurrencyFormatter.formatDisplay(
                                status.getBudget().getLimitMinor(), currency))
                : context.getString(R.string.notif_budget_warning_body,
                        status.getRawPercentUsed(),
                        CurrencyFormatter.formatDisplay(status.getRemainingMinor(), currency));

        return post(context,
                CHANNEL_BUDGET_ALERTS,
                1000 + category.ordinal(),
                title,
                body,
                DESTINATION_BUDGETS);
    }

    /** Post weekly summary. */
    public static boolean postWeeklySummary(Context context, long spentMinor,
                                            int transactionCount) {
        String title = context.getString(R.string.notif_summary_title);
        String body = context.getString(R.string.notif_summary_body,
                CurrencyFormatter.formatDisplay(spentMinor, CurrencyManager.getCurrency(context)),
                transactionCount);
        return post(context, CHANNEL_SUMMARIES, 3000, title, body, DESTINATION_REPORTS);
    }

    /** Post signed in. */
    public static void postSignedIn(Context context, @Nullable String displayName,
                                    @Nullable SignInMethod method) {
        Guard.notNull(context, "context");
        createChannels(context);

        String name = (displayName == null || displayName.trim().isEmpty())
                ? context.getString(R.string.notif_x_signed_in_fallback_name)
                : displayName.trim();
        SignInMethod resolved = method == null ? SignInMethod.EMAIL : method;

        post(context,
                CHANNEL_ACCOUNT_ACTIVITY,
                ID_SIGNED_IN,
                context.getString(R.string.notif_x_signed_in_title),
                context.getString(R.string.notif_x_signed_in_body, name,
                        context.getString(resolved.labelRes)),
                DESTINATION_TRANSACTIONS,
                R.drawable.ic_profile,
                R.color.brand_primary,
                false);
    }

    /** Post signed out. */
    public static void postSignedOut(Context context) {
        Guard.notNull(context, "context");
        createChannels(context);
        post(context,
                CHANNEL_ACCOUNT_ACTIVITY,
                ID_SIGNED_OUT,
                context.getString(R.string.notif_x_signed_out_title),
                context.getString(R.string.notif_x_signed_out_body),
                DESTINATION_TRANSACTIONS,
                R.drawable.ic_logout,
                R.color.text_secondary,
                true);
    }

    /** Post transaction recorded. */
    public static void postTransactionRecorded(Context context, TransactionType type,
                                               long amountMinor, Category category) {
        Guard.notNull(context, "context");
        Guard.notNull(category, "category");
        createChannels(context);

        boolean income = type == TransactionType.INCOME;
        String amount = CurrencyFormatter.formatDisplay(
                Math.abs(amountMinor), CurrencyManager.getCurrency(context));

        post(context,
                CHANNEL_TRANSACTION_ACTIVITY,
                income ? ID_INCOME_RECORDED : ID_EXPENSE_RECORDED,
                context.getString(income
                        ? R.string.notif_x_income_title
                        : R.string.notif_x_expense_title),
                context.getString(income
                                ? R.string.notif_x_income_body
                                : R.string.notif_x_expense_body,
                        amount, category.getDisplayName()),
                DESTINATION_TRANSACTIONS,
                income ? R.drawable.ic_trending_up : R.drawable.ic_trending_down,
                income ? R.color.status_income : R.color.status_expense,
                !income);
    }

    /** Post. */
    public static boolean post(Context context, String channelId, int notificationId,
                               String title, String body, String destination) {
        return post(context, channelId, notificationId, title, body, destination,
                R.drawable.ic_notification, 0, false);
    }

    /** Post. */
    private static boolean post(Context context, String channelId, int notificationId,
                                String title, String body, String destination,
                                @DrawableRes int smallIcon, @ColorRes int accentColour,
                                boolean quiet) {
        Guard.notNull(context, "context");

        String channel = isKnownChannel(channelId) ? channelId : CHANNEL_BUDGET_ALERTS;

        createChannels(context);

        if (!canPostNotifications(context) || !isChannelEnabled(context, channel)) {
            return false;
        }

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_DESTINATION, destination);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channel)
                .setSmallIcon(smallIcon)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(quiet
                        ? NotificationCompat.PRIORITY_LOW
                        : NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(quiet)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        if (accentColour != 0) {
            builder.setColor(ContextCompat.getColor(context, accentColour));
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build());
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }
}
