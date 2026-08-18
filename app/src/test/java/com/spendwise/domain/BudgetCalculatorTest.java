package com.spendwise.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.spendwise.TestData;
import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tests the money arithmetic: the balance invariant after every single append, category
 * totals, budget threshold states and saturation at the extremes.
 */
public class BudgetCalculatorTest {

    private final List<Transaction> ledger = TestData.sampleLedger();
    private static final YearMonth AUGUST_2026 = YearMonth.of(2026, 8);

    // ---- Balance invariant ----------------------------------------------

    /** Balance_of empty ledger is zero. */
    @Test
    public void balance_ofEmptyLedgerIsZero() {
        // Base case of the induction.
        assertEquals(0L, BudgetCalculator.balanceMinor(Collections.emptyList()));
    }

    /** Balance_of null ledger is zero rather than an exception. */
    @Test
    public void balance_ofNullLedgerIsZeroRatherThanAnException() {
        assertEquals(0L, BudgetCalculator.balanceMinor(null));
    }

    /** Balance_equals income minus expense. */
    @Test
    public void balance_equalsIncomeMinusExpense() {
        long income = BudgetCalculator.totalIncomeMinor(ledger);
        long expense = BudgetCalculator.totalExpenseMinor(ledger);
        assertEquals(income - expense, BudgetCalculator.balanceMinor(ledger));
    }

    /** Balance_matches hand calculated total. */
    @Test
    public void balance_matchesHandCalculatedTotal() {
        // 245000 income − (4250 + 2890 + 550 + 1099 + 3120) expense
        assertEquals(245_000L - 11_909L, BudgetCalculator.balanceMinor(ledger));
    }

    /** Balance invariant_holds after every individual append. */
    @Test
    public void balanceInvariant_holdsAfterEveryIndividualAppend() {
        List<Transaction> growing = new ArrayList<>();
        for (Transaction t : ledger) {
            growing.add(t);
            long balance = BudgetCalculator.balanceMinor(growing);
            long recomputed = BudgetCalculator.totalIncomeMinor(growing)
                    - BudgetCalculator.totalExpenseMinor(growing);
            assertEquals("invariant broke at size " + growing.size(), recomputed, balance);
        }
    }

    /** Balance_is independent of ledger order. */
    @Test
    public void balance_isIndependentOfLedgerOrder() {
        // Addition is commutative, so shuffling must not change the result.
        List<Transaction> shuffled = new ArrayList<>(ledger);
        Collections.shuffle(shuffled, new Random(42));
        assertEquals(BudgetCalculator.balanceMinor(ledger),
                BudgetCalculator.balanceMinor(shuffled));
    }

    /** Balance_skips null entries without failing. */
    @Test
    public void balance_skipsNullEntriesWithoutFailing() {
        List<Transaction> withNull = new ArrayList<>(ledger);
        withNull.add(null);
        assertEquals(BudgetCalculator.balanceMinor(ledger),
                BudgetCalculator.balanceMinor(withNull));
    }

    // ---- Overflow handling ----------------------------------------------

    /** Addition_saturates instead of wrapping on overflow. */
    @Test
    public void addition_saturatesInsteadOfWrappingOnOverflow() {
        // Two's-complement wrap-around would turn a huge positive balance negative.
        assertEquals(Long.MAX_VALUE,
                BudgetCalculator.addExactSaturating(Long.MAX_VALUE, 1L));
        assertEquals(Long.MIN_VALUE,
                BudgetCalculator.addExactSaturating(Long.MIN_VALUE, -1L));
    }

    /** Addition_behaves normally within range. */
    @Test
    public void addition_behavesNormallyWithinRange() {
        assertEquals(300L, BudgetCalculator.addExactSaturating(100L, 200L));
        assertEquals(-100L, BudgetCalculator.addExactSaturating(100L, -200L));
        assertEquals(0L, BudgetCalculator.addExactSaturating(0L, 0L));
    }

    // ---- Category aggregation -------------------------------------------

    /** Spent in category_sums only matching category and month. */
    @Test
    public void spentInCategory_sumsOnlyMatchingCategoryAndMonth() {
        assertEquals(4_250L + 3_120L,
                BudgetCalculator.spentInCategory(ledger, Category.GROCERIES, AUGUST_2026));
    }

    /** Spent in category_excludes other months. */
    @Test
    public void spentInCategory_excludesOtherMonths() {
        assertEquals(0L, BudgetCalculator.spentInCategory(
                ledger, Category.GROCERIES, YearMonth.of(2026, 7)));
    }

    /** Spent in category_excludes income rows. */
    @Test
    public void spentInCategory_excludesIncomeRows() {
        // SALARY is an income category, so it must never count as spending.
        assertEquals(0L,
                BudgetCalculator.spentInCategory(ledger, Category.SALARY, AUGUST_2026));
    }

    /** Spent in category_handles null arguments safely. */
    @Test
    public void spentInCategory_handlesNullArgumentsSafely() {
        assertEquals(0L, BudgetCalculator.spentInCategory(null, Category.GROCERIES, AUGUST_2026));
        assertEquals(0L, BudgetCalculator.spentInCategory(ledger, null, AUGUST_2026));
        assertEquals(0L, BudgetCalculator.spentInCategory(ledger, Category.GROCERIES, null));
    }

    /** Spend by category_omits categories with no spending. */
    @Test
    public void spendByCategory_omitsCategoriesWithNoSpending() {
        Map<Category, Long> totals = BudgetCalculator.spendByCategory(ledger);
        assertEquals(4, totals.size());   // groceries, dining, transport, entertainment
        assertFalse(totals.containsKey(Category.TRAVEL));
        assertEquals(Long.valueOf(7_370L), totals.get(Category.GROCERIES));
    }

    /** Spend by category_totals match the overall expense total. */
    @Test
    public void spendByCategory_totalsMatchTheOverallExpenseTotal() {
        long sum = 0L;
        for (Long value : BudgetCalculator.spendByCategory(ledger).values()) {
            sum += value;
        }
        assertEquals(BudgetCalculator.totalExpenseMinor(ledger), sum);
    }

    // ---- Budget evaluation ----------------------------------------------

    /** Evaluate_reports on track below the alert threshold. */
    @Test
    public void evaluate_reportsOnTrackBelowTheAlertThreshold() {
        // LKR 73.70 spent against a LKR 200.00 limit is 36%.
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 20_000L, AUGUST_2026);
        BudgetCalculator.BudgetStatus status = BudgetCalculator.evaluate(budget, ledger);

        assertEquals(BudgetCalculator.BudgetState.ON_TRACK, status.getState());
        assertEquals(36, status.getRawPercentUsed());
        assertFalse(status.requiresAlert());
    }

    /** Evaluate_reports warning at the threshold. */
    @Test
    public void evaluate_reportsWarningAtTheThreshold() {
        // LKR 73.70 against LKR 85.00 is 86%, above the default 80% threshold.
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 8_500L, AUGUST_2026);
        BudgetCalculator.BudgetStatus status = BudgetCalculator.evaluate(budget, ledger);

        assertEquals(BudgetCalculator.BudgetState.WARNING, status.getState());
        assertTrue(status.requiresAlert());
    }

    /** Evaluate_reports exceeded when spend equals the limit exactly. */
    @Test
    public void evaluate_reportsExceededWhenSpendEqualsTheLimitExactly() {
        // Boundary: spend == limit must count as exceeded, not as merely a warning.
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 7_370L, AUGUST_2026);
        BudgetCalculator.BudgetStatus status = BudgetCalculator.evaluate(budget, ledger);

        assertEquals(BudgetCalculator.BudgetState.EXCEEDED, status.getState());
        assertEquals(100, status.getRawPercentUsed());
        assertEquals(0L, status.getOverspendMinor());
    }

    /** Evaluate_reports overspend amount when past the limit. */
    @Test
    public void evaluate_reportsOverspendAmountWhenPastTheLimit() {
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 5_000L, AUGUST_2026);
        BudgetCalculator.BudgetStatus status = BudgetCalculator.evaluate(budget, ledger);

        assertEquals(BudgetCalculator.BudgetState.EXCEEDED, status.getState());
        assertEquals(2_370L, status.getOverspendMinor());
        assertEquals(0L, status.getRemainingMinor());
    }

    /** Evaluate_clamps progress bar value but not the text percentage. */
    @Test
    public void evaluate_clampsProgressBarValueButNotTheTextPercentage() {
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 5_000L, AUGUST_2026);
        BudgetCalculator.BudgetStatus status = BudgetCalculator.evaluate(budget, ledger);

        assertEquals(100, status.getPercentUsed());        // progress bar
        assertEquals(147, status.getRawPercentUsed());     // displayed text
    }

    /** Evaluate_multiplies before dividing so small spends are not rounded to zero. */
    @Test
    public void evaluate_multipliesBeforeDividingSoSmallSpendsAreNotRoundedToZero() {
        // LKR 0.05 against a LKR 100.00 limit. Dividing first would give 0 * 100 = 0%.
        List<Transaction> tiny = new ArrayList<>();
        tiny.add(TestData.expense("Sweet", 5L, Category.GROCERIES, LocalDate.of(2026, 8, 4)));
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 10_000L, AUGUST_2026);

        assertEquals(0, BudgetCalculator.evaluate(budget, tiny).getRawPercentUsed());
        assertEquals(5L, BudgetCalculator.evaluate(budget, tiny).getSpentMinor());
    }

    /** Evaluate_reports zero for an empty ledger. */
    @Test
    public void evaluate_reportsZeroForAnEmptyLedger() {
        Budget budget = Budget.create(TestData.USER_ID, Category.GROCERIES, 10_000L, AUGUST_2026);
        BudgetCalculator.BudgetStatus status =
                BudgetCalculator.evaluate(budget, Collections.emptyList());

        assertEquals(0L, status.getSpentMinor());
        assertEquals(0, status.getRawPercentUsed());
        assertEquals(10_000L, status.getRemainingMinor());
        assertEquals(BudgetCalculator.BudgetState.ON_TRACK, status.getState());
    }

    /** Evaluate_rejects a null budget. */
    @Test(expected = IllegalArgumentException.class)
    public void evaluate_rejectsANullBudget() {
        BudgetCalculator.evaluate(null, ledger);
    }

    /** Evaluate all_orders by percentage used descending. */
    @Test
    public void evaluateAll_ordersByPercentageUsedDescending() {
        List<Budget> budgets = new ArrayList<>();
        budgets.add(Budget.create(TestData.USER_ID, Category.GROCERIES, 20_000L, AUGUST_2026));
        budgets.add(Budget.create(TestData.USER_ID, Category.DINING, 3_000L, AUGUST_2026));

        List<BudgetCalculator.BudgetStatus> statuses =
                BudgetCalculator.evaluateAll(budgets, ledger);

        assertEquals(2, statuses.size());
        assertTrue("most-breached budget must sort first",
                statuses.get(0).getRawPercentUsed() >= statuses.get(1).getRawPercentUsed());
        assertEquals(Category.DINING, statuses.get(0).getBudget().categoryAsEnum());
    }

    /** Evaluate all_skips budgets with a non positive limit. */
    @Test
    public void evaluateAll_skipsBudgetsWithANonPositiveLimit() {
        List<Budget> budgets = new ArrayList<>();
        Budget zeroLimit = new Budget();
        zeroLimit.setUserId(TestData.USER_ID);
        zeroLimit.setCategory(Category.GROCERIES.name());
        zeroLimit.setLimitMinor(0L);
        zeroLimit.setYear(2026);
        zeroLimit.setMonth(8);
        budgets.add(zeroLimit);

        // Must skip rather than divide by zero.
        assertEquals(0, BudgetCalculator.evaluateAll(budgets, ledger).size());
    }

    /** Evaluate all_handles a null budget list. */
    @Test
    public void evaluateAll_handlesANullBudgetList() {
        assertEquals(0, BudgetCalculator.evaluateAll(null, ledger).size());
    }
}
