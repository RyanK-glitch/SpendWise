package com.spendwise.domain;

import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * All the money arithmetic in one place: balance, income and expense totals, spend per
 * category and budget threshold states. Addition saturates instead of wrapping round,
 * so a very large total can never turn negative.
 */
public final class BudgetCalculator {
    private BudgetCalculator() {
    }

    public enum BudgetState {
        ON_TRACK,

        WARNING,

        EXCEEDED
    }

    public static final class BudgetStatus {
        private final Budget budget;
        private final long spentMinor;
        private final int percentUsed;
        private final BudgetState state;

        BudgetStatus(Budget budget, long spentMinor, int percentUsed, BudgetState state) {
            this.budget = budget;
            this.spentMinor = spentMinor;
            this.percentUsed = percentUsed;
            this.state = state;
        }

        /** Returns the budget. */
        public Budget getBudget() {
            return budget;
        }

        /** Returns the spent minor. */
        public long getSpentMinor() {
            return spentMinor;
        }

        /** Returns the remaining minor. */
        public long getRemainingMinor() {
            return Math.max(0L, budget.getLimitMinor() - spentMinor);
        }

        /** Returns the overspend minor. */
        public long getOverspendMinor() {
            return Math.max(0L, spentMinor - budget.getLimitMinor());
        }

        /** Returns the percent used. */
        public int getPercentUsed() {
            return Math.min(100, percentUsed);
        }

        /** Returns the raw percent used. */
        public int getRawPercentUsed() {
            return percentUsed;
        }

        /** Returns the state. */
        public BudgetState getState() {
            return state;
        }

        /** Requires alert. */
        public boolean requiresAlert() {
            return state == BudgetState.WARNING || state == BudgetState.EXCEEDED;
        }
    }

    /**
     * Income minus expenditure over the whole ledger. signedAmountMinor() applies the sign
     * from the type column, and the addition saturates rather than wrapping round.
     */
    public static long balanceMinor(List<Transaction> ledger) {
        if (ledger == null) {
            return 0L;
        }
        long balance = 0L;
        for (Transaction t : ledger) {
            if (t == null) {
                continue;
            }
            balance = addExactSaturating(balance, t.signedAmountMinor());
        }
        return balance;
    }

    /** Total income minor. */
    public static long totalIncomeMinor(List<Transaction> ledger) {
        return sumWhere(ledger, TransactionType.INCOME);
    }

    /** Total expense minor. */
    public static long totalExpenseMinor(List<Transaction> ledger) {
        return sumWhere(ledger, TransactionType.EXPENSE);
    }

    /** Sum where. */
    private static long sumWhere(List<Transaction> ledger, TransactionType type) {
        if (ledger == null) {
            return 0L;
        }
        long total = 0L;
        for (Transaction t : ledger) {
            if (t != null && t.typeAsEnum() == type) {
                total = addExactSaturating(total, t.getAmountMinor());
            }
        }
        return total;
    }

    /** Totals the expenses in one category, ignoring income. */
    public static long spentInCategory(List<Transaction> ledger, Category category, YearMonth period) {
        if (ledger == null || category == null || period == null) {
            return 0L;
        }
        long total = 0L;
        for (Transaction t : ledger) {
            if (t == null || t.typeAsEnum() != TransactionType.EXPENSE) {
                continue;
            }
            if (t.categoryAsEnum() != category) {
                continue;
            }
            LocalDate date = t.getDate();
            if (date != null
                    && date.getYear() == period.getYear()
                    && date.getMonthValue() == period.getMonthValue()) {
                total = addExactSaturating(total, t.getAmountMinor());
            }
        }
        return total;
    }

    /** Spend by category. */
    public static Map<Category, Long> spendByCategory(List<Transaction> ledger) {
        Map<Category, Long> totals = new EnumMap<>(Category.class);
        if (ledger == null) {
            return totals;
        }
        for (Transaction t : ledger) {
            if (t == null || t.typeAsEnum() != TransactionType.EXPENSE) {
                continue;
            }
            Category category = t.categoryAsEnum();
            long current = totals.containsKey(category) ? totals.get(category) : 0L;
            totals.put(category, addExactSaturating(current, t.getAmountMinor()));
        }
        return totals;
    }

    /** Evaluate. */
    public static BudgetStatus evaluate(Budget budget, List<Transaction> ledger) {
        Guard.notNull(budget, "budget");
        Guard.invariant(budget.getLimitMinor() > 0, "budget limit must be positive");

        long spent = spentInCategory(ledger, budget.categoryAsEnum(), budget.period());

        int percent = (int) Math.min(Integer.MAX_VALUE,
                (spent * 100L) / budget.getLimitMinor());

        BudgetState state;
        if (spent >= budget.getLimitMinor()) {
            state = BudgetState.EXCEEDED;
        } else if (percent >= budget.getAlertThresholdPercent()) {
            state = BudgetState.WARNING;
        } else {
            state = BudgetState.ON_TRACK;
        }

        return new BudgetStatus(budget, spent, percent, state);
    }

    /** Evaluate all. */
    public static List<BudgetStatus> evaluateAll(List<Budget> budgets, List<Transaction> ledger) {
        List<BudgetStatus> statuses = new ArrayList<>();
        if (budgets == null) {
            return statuses;
        }
        for (Budget b : budgets) {
            if (b != null && b.getLimitMinor() > 0) {
                statuses.add(evaluate(b, ledger));
            }
        }
        Collections.sort(statuses, (a, b) ->
                Integer.compare(b.getRawPercentUsed(), a.getRawPercentUsed()));
        return statuses;
    }

    /**
     * Stops a very large total from overflowing into a negative number, which would be a
     * worse answer than a clamped one.
     */
    static long addExactSaturating(long a, long b) {
        long result = a + b;

        if (((a ^ result) & (b ^ result)) < 0) {
            return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        }
        return result;
    }
}
