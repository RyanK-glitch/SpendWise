package com.spendwise.fixtures;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;
import com.spendwise.domain.Category;
import com.spendwise.domain.TransactionType;

import org.junit.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tests the generator itself: that it is deterministic, covers every category and month,
 * creates no future dated rows, and produces enough volume to exercise the filter.
 */
public class LedgerFixtureTest {

    private static final long USER_ID = 1L;
    private static final YearMonth END_MONTH = YearMonth.of(2026, 8);

    /** Generates enough rows for the search and filter features to be meaningful. */
    @Test
    public void generatesEnoughRowsForTheSearchAndFilterFeaturesToBeMeaningful() {
        List<Transaction> ledger = LedgerFixture.generateTransactions(USER_ID, END_MONTH);
        assertTrue("expected a content-heavy ledger but got " + ledger.size(),
                ledger.size() >= 200);
    }

    /** True when deterministic so tests and demos are reproducible. */
    @Test
    public void isDeterministicSoTestsAndDemosAreReproducible() {
        List<Transaction> first = LedgerFixture.generateTransactions(USER_ID, END_MONTH);
        List<Transaction> second = LedgerFixture.generateTransactions(USER_ID, END_MONTH);

        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).getDescription(), second.get(i).getDescription());
            assertEquals(first.get(i).getAmountMinor(), second.get(i).getAmountMinor());
            assertEquals(first.get(i).getDate(), second.get(i).getDate());
            assertEquals(first.get(i).getCategory(), second.get(i).getCategory());
        }
    }

    /** Every category is represented so every filter option returns results. */
    @Test
    public void everyCategoryIsRepresentedSoEveryFilterOptionReturnsResults() {
        Set<Category> seen = EnumSet.noneOf(Category.class);
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            seen.add(t.categoryAsEnum());
        }
        for (Category category : Category.values()) {
            assertTrue("no generated transaction for " + category, seen.contains(category));
        }
    }

    /** Contains both income and expense so the type filter is exercised. */
    @Test
    public void containsBothIncomeAndExpenseSoTheTypeFilterIsExercised() {
        boolean hasIncome = false;
        boolean hasExpense = false;
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            if (t.typeAsEnum() == TransactionType.INCOME) {
                hasIncome = true;
            } else {
                hasExpense = true;
            }
        }
        assertTrue(hasIncome);
        assertTrue(hasExpense);
    }

    /** Never generates a date in the future. */
    @Test
    public void neverGeneratesADateInTheFuture() {
        LocalDate today = LocalDate.now();
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, YearMonth.from(today))) {
            assertFalse("generated a future-dated transaction: " + t.getDate(),
                    t.getDate().isAfter(today));
        }
    }

    /** Every amount is positive as the entity invariant requires. */
    @Test
    public void everyAmountIsPositiveAsTheEntityInvariantRequires() {
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            assertTrue("non-positive amount for " + t.getDescription(),
                    t.getAmountMinor() > 0);
        }
    }

    /** Every row is owned by the requested user. */
    @Test
    public void everyRowIsOwnedByTheRequestedUser() {
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            assertEquals(USER_ID, t.getUserId());
        }
    }

    /** Every row has a non blank searchable description. */
    @Test
    public void everyRowHasANonBlankSearchableDescription() {
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            assertNotNull(t.getDescription());
            assertFalse(t.getDescription().trim().isEmpty());
        }
    }

    /** Spans twelve distinct months so the date filter has range to work with. */
    @Test
    public void spansTwelveDistinctMonthsSoTheDateFilterHasRangeToWorkWith() {
        Set<YearMonth> months = new HashSet<>();
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            months.add(YearMonth.from(t.getDate()));
        }
        assertEquals(12, months.size());
    }

    /** Produces a variety of merchants so the search box is useful. */
    @Test
    public void producesAVarietyOfMerchantsSoTheSearchBoxIsUseful() {
        Set<String> merchants = new HashSet<>();
        for (Transaction t : LedgerFixture.generateTransactions(USER_ID, END_MONTH)) {
            merchants.add(t.getDescription());
        }
        assertTrue("too few distinct merchants: " + merchants.size(), merchants.size() >= 30);
    }

    /** Rent and salary occur exactly once per month once their due date has passed. */
    @Test
    public void rentAndSalaryOccurExactlyOncePerMonthOnceTheirDueDateHasPassed() {
        List<Transaction> ledger = LedgerFixture.generateTransactions(USER_ID, END_MONTH);
        LocalDate today = LocalDate.now();

        Map<YearMonth, Integer> rentByMonth = new HashMap<>();
        Map<YearMonth, Integer> salaryByMonth = new HashMap<>();
        for (Transaction t : ledger) {
            Map<YearMonth, Integer> target = null;
            if (t.categoryAsEnum() == Category.RENT) {
                target = rentByMonth;
            } else if (t.categoryAsEnum() == Category.SALARY) {
                target = salaryByMonth;
            }
            if (target != null) {
                YearMonth month = YearMonth.from(t.getDate());
                target.merge(month, 1, Integer::sum);
            }
        }

        for (int offset = 11; offset >= 0; offset--) {
            YearMonth month = END_MONTH.minusMonths(offset);

            LocalDate rentDue = month.atDay(1);
            LocalDate salaryDue = month.atDay(Math.min(28, month.lengthOfMonth()));

            int expectedRent = rentDue.isAfter(today) ? 0 : 1;
            int expectedSalary = salaryDue.isAfter(today) ? 0 : 1;

            assertEquals("rent count wrong for " + month,
                    expectedRent, rentByMonth.getOrDefault(month, 0).intValue());
            assertEquals("salary count wrong for " + month,
                    expectedSalary, salaryByMonth.getOrDefault(month, 0).intValue());
        }
    }

    /** Generates starter budgets for the current period. */
    @Test
    public void generatesStarterBudgetsForTheCurrentPeriod() {
        List<Budget> budgets = LedgerFixture.generateBudgets(USER_ID, END_MONTH);
        assertFalse(budgets.isEmpty());
        for (Budget budget : budgets) {
            assertEquals(USER_ID, budget.getUserId());
            assertEquals(END_MONTH, budget.period());
            assertTrue(budget.getLimitMinor() > 0);
        }
    }

    /** Budgets cover distinct categories. */
    @Test
    public void budgetsCoverDistinctCategories() {
        Set<Category> categories = EnumSet.noneOf(Category.class);
        for (Budget budget : LedgerFixture.generateBudgets(USER_ID, END_MONTH)) {
            assertTrue("duplicate budget category " + budget.categoryAsEnum(),
                    categories.add(budget.categoryAsEnum()));
        }
    }

    /** Rejects an invalid user id. */
    @Test
    public void rejectsAnInvalidUserId() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerFixture.generateTransactions(0L, END_MONTH));
        assertThrows(IllegalArgumentException.class,
                () -> LedgerFixture.generateTransactions(-1L, END_MONTH));
    }

    /** Rejects a null period. */
    @Test
    public void rejectsANullPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> LedgerFixture.generateTransactions(USER_ID, null));
    }
}
