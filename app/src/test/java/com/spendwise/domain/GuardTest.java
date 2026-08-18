package com.spendwise.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.spendwise.TestData;
import com.spendwise.data.entity.Transaction;

import org.junit.Test;

import java.time.LocalDate;

/**
 * Tests that each guard raises the right exception type, because a precondition and a
 * postcondition failure mean different things and are handled differently.
 */
public class GuardTest {

    // ---- require: preconditions ------------------------------------------

    /** Require_passes silently when the condition holds. */
    @Test
    public void require_passesSilentlyWhenTheConditionHolds() {
        Guard.require(true, "should not throw");
    }

    /** Require_throws illegal argument exception for the caller. */
    @Test
    public void require_throwsIllegalArgumentExceptionForTheCaller() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Guard.require(false, "amount must be positive"));
        assertTrue(e.getMessage().contains("Precondition violated"));
        assertTrue(e.getMessage().contains("amount must be positive"));
    }

    // ---- ensure: postconditions ------------------------------------------

    /** Ensure_throws illegal state exception for this component. */
    @Test
    public void ensure_throwsIllegalStateExceptionForThisComponent() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Guard.ensure(false, "result must be non-empty"));
        assertTrue(e.getMessage().contains("Postcondition violated"));
    }

    // ---- invariant --------------------------------------------------------

    /** Invariant_throws illegal state exception when broken. */
    @Test
    public void invariant_throwsIllegalStateExceptionWhenBroken() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> Guard.invariant(false, "min must not exceed max"));
        assertTrue(e.getMessage().contains("Invariant violated"));
    }

    // ---- notNull / notBlank ----------------------------------------------

    /** Not null_returns the value so it can be used inline. */
    @Test
    public void notNull_returnsTheValueSoItCanBeUsedInline() {
        String value = "hello";
        assertSame(value, Guard.notNull(value, "value"));
    }

    /** Not null_names the offending parameter. */
    @Test
    public void notNull_namesTheOffendingParameter() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Guard.notNull(null, "customerName"));
        assertTrue("message should name the parameter",
                e.getMessage().contains("customerName"));
    }

    /** Not blank_rejects null empty and whitespace only. */
    @Test
    public void notBlank_rejectsNullEmptyAndWhitespaceOnly() {
        assertThrows(IllegalArgumentException.class, () -> Guard.notBlank(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> Guard.notBlank("", "x"));
        assertThrows(IllegalArgumentException.class, () -> Guard.notBlank("   ", "x"));
    }

    /** Not blank_preserves the original string including its whitespace. */
    @Test
    public void notBlank_preservesTheOriginalStringIncludingItsWhitespace() {
        // The guard validates; it must not silently transform the caller's value.
        assertEquals("  padded  ", Guard.notBlank("  padded  ", "x"));
    }

    // ---- inRange ----------------------------------------------------------

    /** In range_accepts both bounds inclusively. */
    @Test
    public void inRange_acceptsBothBoundsInclusively() {
        assertEquals(1L, Guard.inRange(1L, 1L, 10L, "value"));
        assertEquals(10L, Guard.inRange(10L, 1L, 10L, "value"));
        assertEquals(5L, Guard.inRange(5L, 1L, 10L, "value"));
    }

    /** In range_rejects values just outside either bound. */
    @Test
    public void inRange_rejectsValuesJustOutsideEitherBound() {
        assertThrows(IllegalArgumentException.class, () -> Guard.inRange(0L, 1L, 10L, "value"));
        assertThrows(IllegalArgumentException.class, () -> Guard.inRange(11L, 1L, 10L, "value"));
    }

    /** In range_reports the bounds and the offending value. */
    @Test
    public void inRange_reportsTheBoundsAndTheOffendingValue() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> Guard.inRange(42L, 1L, 10L, "amount"));
        assertTrue(e.getMessage().contains("amount"));
        assertTrue(e.getMessage().contains("42"));
    }

    // ---- Entity preconditions built on the guards -------------------------

    /** Transaction factory_rejects a non positive amount. */
    @Test
    public void transactionFactory_rejectsANonPositiveAmount() {
        assertThrows(IllegalArgumentException.class, () -> TestData.expense(
                "Test", 0L, Category.GROCERIES, LocalDate.of(2026, 8, 1)));
        assertThrows(IllegalArgumentException.class, () -> TestData.expense(
                "Test", -100L, Category.GROCERIES, LocalDate.of(2026, 8, 1)));
    }

    /** Transaction factory_rejects a blank description. */
    @Test
    public void transactionFactory_rejectsABlankDescription() {
        assertThrows(IllegalArgumentException.class, () -> TestData.expense(
                "   ", 100L, Category.GROCERIES, LocalDate.of(2026, 8, 1)));
    }

    /** Transaction factory_rejects an amount above the ceiling. */
    @Test
    public void transactionFactory_rejectsAnAmountAboveTheCeiling() {
        assertThrows(IllegalArgumentException.class, () -> TestData.expense(
                "Test", Long.MAX_VALUE, Category.GROCERIES, LocalDate.of(2026, 8, 1)));
    }

    /** Transaction factory_rejects an invalid user id. */
    @Test
    public void transactionFactory_rejectsAnInvalidUserId() {
        assertThrows(IllegalArgumentException.class, () -> Transaction.create(
                0L, "Test", null, 100L, TransactionType.EXPENSE,
                Category.GROCERIES, PaymentMethod.CARD, LocalDate.of(2026, 8, 1)));
    }

    /** Transaction factory_normalises an empty note to null. */
    @Test
    public void transactionFactory_normalisesAnEmptyNoteToNull() {
        Transaction t = Transaction.create(1L, "Test", "   ", 100L,
                TransactionType.EXPENSE, Category.GROCERIES, PaymentMethod.CARD,
                LocalDate.of(2026, 8, 1));
        // Storing "" and null for "no note" would need two checks everywhere it is read.
        assertEquals(null, t.getNote());
    }

    /** Transaction factory_trims the description. */
    @Test
    public void transactionFactory_trimsTheDescription() {
        Transaction t = TestData.expense("  Tesco Metro  ", 100L,
                Category.GROCERIES, LocalDate.of(2026, 8, 1));
        assertEquals("Tesco Metro", t.getDescription());
    }

    // ---- Defensive enum parsing -------------------------------------------

    /** Enum lookups_degrade to null rather than throwing on unknown values. */
    @Test
    public void enumLookups_degradeToNullRatherThanThrowingOnUnknownValues() {
        // A corrupted database row must not crash the ledger screen.
        assertEquals(null, Category.fromNameOrNull("NOT_A_CATEGORY"));
        assertEquals(null, PaymentMethod.fromNameOrNull("NOT_A_METHOD"));
        assertEquals(null, TransactionType.fromNameOrNull("NOT_A_TYPE"));
        assertEquals(null, Category.fromNameOrNull(null));
    }

    /** Enum lookups_are case insensitive and trimmed. */
    @Test
    public void enumLookups_areCaseInsensitiveAndTrimmed() {
        assertEquals(Category.GROCERIES, Category.fromNameOrNull("  groceries  "));
        assertEquals(TransactionType.INCOME, TransactionType.fromNameOrNull("Income"));
    }

    /** Corrupted row falls back to a safe default instead of throwing. */
    @Test
    public void corruptedRowFallsBackToASafeDefaultInsteadOfThrowing() {
        Transaction t = TestData.expense("Test", 100L, Category.GROCERIES,
                LocalDate.of(2026, 8, 1));
        t.setCategory("SOMETHING_REMOVED_IN_A_LATER_VERSION");
        t.setType("GARBAGE");

        // Reading it back must yield a usable value rather than an exception.
        assertEquals(Category.OTHER_INCOME, t.categoryAsEnum());
        assertEquals(TransactionType.EXPENSE, t.typeAsEnum());
    }

    /** Signed amount_is positive for income and negative for expense. */
    @Test
    public void signedAmount_isPositiveForIncomeAndNegativeForExpense() {
        Transaction income = TestData.income("Salary", 1000L, LocalDate.of(2026, 8, 1));
        Transaction expense = TestData.expense("Shop", 1000L, Category.GROCERIES,
                LocalDate.of(2026, 8, 1));
        assertEquals(1000L, income.signedAmountMinor());
        assertEquals(-1000L, expense.signedAmountMinor());
    }
}
