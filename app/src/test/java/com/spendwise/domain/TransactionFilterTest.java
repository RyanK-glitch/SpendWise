package com.spendwise.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.spendwise.TestData;
import com.spendwise.data.entity.Transaction;

import org.junit.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * Tests each filter clause alone and in combination, plus the four algebraic properties
 * the filter was specified with: totality, identity, monotonicity and order independence.
 */
public class TransactionFilterTest {

    private final List<Transaction> ledger = TestData.sampleLedger();

    // ---- Identity property ----------------------------------------------

    /** Empty filter_matches every transaction. */
    @Test
    public void emptyFilter_matchesEveryTransaction() {
        TransactionFilter filter = TransactionFilter.empty();
        for (Transaction t : ledger) {
            assertTrue("empty filter must match " + t.getDescription(), filter.matches(t));
        }
        assertEquals(ledger.size(), filter.apply(ledger).size());
    }

    /** Empty filter_reports itself as empty with no active criteria. */
    @Test
    public void emptyFilter_reportsItselfAsEmptyWithNoActiveCriteria() {
        assertTrue(TransactionFilter.empty().isEmpty());
        assertEquals(0, TransactionFilter.empty().activeCriteriaCount());
    }

    // ---- Totality property ----------------------------------------------

    /** Matches_returns false for null rather than throwing. */
    @Test
    public void matches_returnsFalseForNullRatherThanThrowing() {
        assertFalse(TransactionFilter.empty().matches(null));
    }

    /** Applies the _handles null list without throwing. */
    @Test
    public void apply_handlesNullListWithoutThrowing() {
        assertEquals(0, TransactionFilter.empty().apply(null).size());
    }

    /** Matches_tolerates a transaction with null note. */
    @Test
    public void matches_toleratesATransactionWithNullNote() {
        Transaction noNote = TestData.expense("Aldi", 1000L, Category.GROCERIES,
                LocalDate.of(2026, 8, 1));
        TransactionFilter filter = TransactionFilter.builder().query("weekly").build();
        // Must return false, not throw a NullPointerException on the absent note.
        assertFalse(filter.matches(noNote));
    }

    // ---- Query clause ---------------------------------------------------

    /** Query_matches description case insensitively. */
    @Test
    public void query_matchesDescriptionCaseInsensitively() {
        TransactionFilter filter = TransactionFilter.builder().query("TESCO").build();
        List<Transaction> result = filter.apply(ledger);
        assertEquals(1, result.size());
        assertEquals("Tesco Metro", result.get(0).getDescription());
    }

    /** Query_matches partial substring. */
    @Test
    public void query_matchesPartialSubstring() {
        TransactionFilter filter = TransactionFilter.builder().query("esco").build();
        assertEquals(1, filter.apply(ledger).size());
    }

    /** Query_also searches the note field. */
    @Test
    public void query_alsoSearchesTheNoteField() {
        TransactionFilter filter = TransactionFilter.builder().query("weekly").build();
        List<Transaction> result = filter.apply(ledger);
        assertEquals(1, result.size());
        assertEquals("Tesco Metro", result.get(0).getDescription());
    }

    /** Query_is trimmed so stray spaces do not break the search. */
    @Test
    public void query_isTrimmedSoStraySpacesDoNotBreakTheSearch() {
        assertEquals(1, TransactionFilter.builder().query("  Netflix  ").build()
                .apply(ledger).size());
    }

    /** Query_returns nothing when no row matches. */
    @Test
    public void query_returnsNothingWhenNoRowMatches() {
        assertEquals(0, TransactionFilter.builder().query("zzzznomatch").build()
                .apply(ledger).size());
    }

    // ---- Category clause ------------------------------------------------

    /** Category_matches only selected categories. */
    @Test
    public void category_matchesOnlySelectedCategories() {
        TransactionFilter filter = TransactionFilter.builder()
                .category(Category.GROCERIES).build();
        assertEquals(2, filter.apply(ledger).size());
    }

    /** Category_treats multiple selections as union. */
    @Test
    public void category_treatsMultipleSelectionsAsUnion() {
        TransactionFilter filter = TransactionFilter.builder()
                .categories(EnumSet.of(Category.GROCERIES, Category.DINING)).build();
        assertEquals(3, filter.apply(ledger).size());
    }

    /** Category_empty set is vacuously true. */
    @Test
    public void category_emptySetIsVacuouslyTrue() {
        TransactionFilter filter = TransactionFilter.builder()
                .categories(EnumSet.noneOf(Category.class)).build();
        assertEquals(ledger.size(), filter.apply(ledger).size());
    }

    // ---- Payment method and type clauses --------------------------------

    /** Payment method_narrows to the selected method. */
    @Test
    public void paymentMethod_narrowsToTheSelectedMethod() {
        TransactionFilter filter = TransactionFilter.builder()
                .paymentMethod(PaymentMethod.CASH).build();
        assertEquals(1, filter.apply(ledger).size());
    }

    /** Type_separates income from expense. */
    @Test
    public void type_separatesIncomeFromExpense() {
        assertEquals(1, TransactionFilter.builder().type(TransactionType.INCOME)
                .build().apply(ledger).size());
        assertEquals(5, TransactionFilter.builder().type(TransactionType.EXPENSE)
                .build().apply(ledger).size());
    }

    /** Type_null means both. */
    @Test
    public void type_nullMeansBoth() {
        assertEquals(ledger.size(),
                TransactionFilter.builder().type(null).build().apply(ledger).size());
    }

    // ---- Date range clause ----------------------------------------------

    /** Date range_bounds are inclusive at both ends. */
    @Test
    public void dateRange_boundsAreInclusiveAtBothEnds() {
        TransactionFilter filter = TransactionFilter.builder()
                .dateRange(LocalDate.of(2026, 8, 3), LocalDate.of(2026, 8, 10))
                .build();
        // 3 Aug and 10 Aug are both in the ledger and must both be included.
        assertEquals(3, filter.apply(ledger).size());
    }

    /** Date range_open ended lower bound. */
    @Test
    public void dateRange_openEndedLowerBound() {
        TransactionFilter filter = TransactionFilter.builder()
                .dateRange(null, LocalDate.of(2026, 8, 7)).build();
        assertEquals(2, filter.apply(ledger).size());
    }

    /** Date range_open ended upper bound. */
    @Test
    public void dateRange_openEndedUpperBound() {
        TransactionFilter filter = TransactionFilter.builder()
                .dateRange(LocalDate.of(2026, 8, 18), null).build();
        assertEquals(2, filter.apply(ledger).size());
    }

    /** Date range_inverted input is normalised rather than rejected. */
    @Test
    public void dateRange_invertedInputIsNormalisedRatherThanRejected() {
        // A user dragging the pickers backwards means "between these dates".
        TransactionFilter filter = TransactionFilter.builder()
                .dateRange(LocalDate.of(2026, 8, 10), LocalDate.of(2026, 8, 3))
                .build();
        assertEquals(LocalDate.of(2026, 8, 3), filter.getFrom());
        assertEquals(LocalDate.of(2026, 8, 10), filter.getTo());
        assertEquals(3, filter.apply(ledger).size());
    }

    // ---- Amount range clause --------------------------------------------

    /** Amount range_bounds are inclusive. */
    @Test
    public void amountRange_boundsAreInclusive() {
        TransactionFilter filter = TransactionFilter.builder()
                .amountRange(1_099L, 3_120L).build();
        // Exactly the 1099, 2890 and 3120 rows.
        assertEquals(3, filter.apply(ledger).size());
    }

    /** Amount range_inverted input is swapped. */
    @Test
    public void amountRange_invertedInputIsSwapped() {
        TransactionFilter filter = TransactionFilter.builder()
                .amountRange(5_000L, 1_000L).build();
        assertEquals(1_000L, filter.getMinAmountMinor());
        assertEquals(5_000L, filter.getMaxAmountMinor());
    }

    /** Amount range_negative minimum is clamped to zero. */
    @Test
    public void amountRange_negativeMinimumIsClampedToZero() {
        TransactionFilter filter = TransactionFilter.builder()
                .amountRange(-100L, 5_000L).build();
        assertEquals(TransactionFilter.NO_MIN_AMOUNT, filter.getMinAmountMinor());
    }

    // ---- Conjunction of clauses -----------------------------------------

    /** Multiple criteria_are combined with logical and. */
    @Test
    public void multipleCriteria_areCombinedWithLogicalAnd() {
        TransactionFilter filter = TransactionFilter.builder()
                .category(Category.GROCERIES)
                .paymentMethod(PaymentMethod.CARD)
                .build();
        // Two groceries rows exist, but only one was paid by card.
        List<Transaction> result = filter.apply(ledger);
        assertEquals(1, result.size());
        assertEquals("Tesco Metro", result.get(0).getDescription());
    }

    /** Contradictory criteria_yield nothing rather than throwing. */
    @Test
    public void contradictoryCriteria_yieldNothingRatherThanThrowing() {
        TransactionFilter filter = TransactionFilter.builder()
                .type(TransactionType.INCOME)
                .category(Category.GROCERIES)   // groceries are never income
                .build();
        assertEquals(0, filter.apply(ledger).size());
    }

    // ---- Monotonicity property ------------------------------------------

    /** Adding a criterion_can only ever shrink the result set. */
    @Test
    public void addingACriterion_canOnlyEverShrinkTheResultSet() {
        // Refinement: for every base filter, adding a clause must yield a subset.
        List<TransactionFilter> bases = new ArrayList<>();
        bases.add(TransactionFilter.empty());
        bases.add(TransactionFilter.builder().type(TransactionType.EXPENSE).build());
        bases.add(TransactionFilter.builder().query("a").build());

        for (TransactionFilter base : bases) {
            int baseCount = base.apply(ledger).size();

            int withCategory = base.toBuilder().category(Category.GROCERIES)
                    .build().apply(ledger).size();
            assertTrue("adding a category grew the result set",
                    withCategory <= baseCount);

            int withAmount = base.toBuilder().amountRange(1_000L, 5_000L)
                    .build().apply(ledger).size();
            assertTrue("adding an amount range grew the result set",
                    withAmount <= baseCount);

            int withDate = base.toBuilder()
                    .dateRange(LocalDate.of(2026, 8, 5), LocalDate.of(2026, 8, 15))
                    .build().apply(ledger).size();
            assertTrue("adding a date range grew the result set", withDate <= baseCount);
        }
    }

    // ---- Commutativity property -----------------------------------------

    /** Criterion order_does not affect the result. */
    @Test
    public void criterionOrder_doesNotAffectTheResult() {
        TransactionFilter categoryThenMethod = TransactionFilter.builder()
                .category(Category.GROCERIES)
                .paymentMethod(PaymentMethod.CARD)
                .type(TransactionType.EXPENSE)
                .build();

        TransactionFilter methodThenCategory = TransactionFilter.builder()
                .type(TransactionType.EXPENSE)
                .paymentMethod(PaymentMethod.CARD)
                .category(Category.GROCERIES)
                .build();

        assertEquals(categoryThenMethod.apply(ledger).size(),
                methodThenCategory.apply(ledger).size());
    }

    // ---- Order preservation ---------------------------------------------

    /** Applies the _preserves the input ordering. */
    @Test
    public void apply_preservesTheInputOrdering() {
        List<Transaction> result = TransactionFilter.builder()
                .type(TransactionType.EXPENSE).build().apply(ledger);

        int previousIndex = -1;
        for (Transaction t : result) {
            int index = ledger.indexOf(t);
            assertTrue("filtering reordered the ledger", index > previousIndex);
            previousIndex = index;
        }
    }

    // ---- Immutability ---------------------------------------------------

    /** To builder_does not mutate the original filter. */
    @Test
    public void toBuilder_doesNotMutateTheOriginalFilter() {
        TransactionFilter original = TransactionFilter.builder()
                .query("Tesco").category(Category.GROCERIES).build();
        int originalCount = original.apply(ledger).size();

        original.toBuilder().query("Netflix").build();

        assertEquals("original filter was mutated by toBuilder()",
                originalCount, original.apply(ledger).size());
        assertEquals("Tesco", original.getQuery());
    }

    /** Exposed category set_is unmodifiable. */
    @Test(expected = UnsupportedOperationException.class)
    public void exposedCategorySet_isUnmodifiable() {
        TransactionFilter filter = TransactionFilter.builder()
                .category(Category.GROCERIES).build();
        filter.getCategories().add(Category.DINING);
    }

    // ---- Active criteria counting ---------------------------------------

    /** Active criteria count_counts each dimension once. */
    @Test
    public void activeCriteriaCount_countsEachDimensionOnce() {
        TransactionFilter filter = TransactionFilter.builder()
                .query("tesco")
                .category(Category.GROCERIES)
                .paymentMethod(PaymentMethod.CARD)
                .type(TransactionType.EXPENSE)
                .dateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .amountRange(100L, 10_000L)
                .build();
        assertEquals(6, filter.activeCriteriaCount());
    }

    /** Active criteria count_treats a date range as one criterion not two. */
    @Test
    public void activeCriteriaCount_treatsADateRangeAsOneCriterionNotTwo() {
        TransactionFilter filter = TransactionFilter.builder()
                .dateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))
                .build();
        assertEquals(1, filter.activeCriteriaCount());
    }
}
