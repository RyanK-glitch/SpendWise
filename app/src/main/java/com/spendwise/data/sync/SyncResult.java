package com.spendwise.data.sync;

import androidx.annotation.NonNull;

import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of a sync attempt: success with the rows that came back, disabled, or
 * failed with a reason. Three states rather than two, so a partial or unavailable
 * sync can be reported without throwing.
 */
public final class SyncResult {
    public enum Status { SUCCESS, DISABLED, FAILED }

    private final Status status;
    private final List<Transaction> transactions;
    private final List<Budget> budgets;
    private final String message;

    private SyncResult(Status status, List<Transaction> transactions, List<Budget> budgets,
                       String message) {
        this.status = status;
        this.transactions = Collections.unmodifiableList(
                transactions == null ? new ArrayList<>() : new ArrayList<>(transactions));
        this.budgets = Collections.unmodifiableList(
                budgets == null ? new ArrayList<>() : new ArrayList<>(budgets));
        this.message = message;
    }

    /** A completed sync, carrying the rows that came back. */
    public static SyncResult success(List<Transaction> transactions, List<Budget> budgets) {
        return new SyncResult(Status.SUCCESS, transactions, budgets, "Pulled from Firestore");
    }

    /** Sync is switched off, which is not an error. */
    public static SyncResult disabled() {
        return new SyncResult(Status.DISABLED, null, null, "Cloud sync is not available");
    }

    /** Sync was attempted and did not finish, with a reason for the screen. */
    public static SyncResult failed(String message) {
        return new SyncResult(Status.FAILED, null, null, message);
    }

    /** Returns the status. */
    public Status getStatus() {
        return status;
    }

    /** True when success. */
    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }

    /** True when disabled. */
    public boolean isDisabled() {
        return status == Status.DISABLED;
    }

    /** True when failure. */
    public boolean isFailure() {
        return status == Status.FAILED;
    }

    /** Returns the transactions. */
    public List<Transaction> getTransactions() {
        return transactions;
    }

    /** Returns the budgets. */
    public List<Budget> getBudgets() {
        return budgets;
    }

    /** Returns the message. */
    public String getMessage() {
        return message;
    }

    @NonNull
    @Override
    public String toString() {
        return "SyncResult{" + status + ": " + transactions.size() + " transactions, "
                + budgets.size() + " budgets}";
    }
}
