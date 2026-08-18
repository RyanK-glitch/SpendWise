package com.spendwise.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests parsing and formatting, round trip losslessness, and demonstrates the floating
 * point drift that storing money as a double would have introduced.
 */
public class CurrencyFormatterTest {

    // ---- Formatting -----------------------------------------------------

    /** Formats the _inserts thousands separator and two decimals for display. */
    @Test
    public void format_insertsThousandsSeparatorAndTwoDecimals() {
        assertEquals("LKR 1,234.56", CurrencyFormatter.format(123_456L));
    }

    /** Formats the _pads single penny to two decimal places for display. */
    @Test
    public void format_padsSinglePennyToTwoDecimalPlaces() {
        assertEquals("LKR 0.01", CurrencyFormatter.format(1L));
    }

    /** Formats the _handles zero for display. */
    @Test
    public void format_handlesZero() {
        assertEquals("LKR 0.00", CurrencyFormatter.format(0L));
    }

    /** Formats the _places minus sign before currency symbol for display. */
    @Test
    public void format_placesMinusSignBeforeCurrencySymbol() {
        // "-LKR 5.00" reads correctly; "LKR -5.00" does not.
        assertEquals("-LKR 5.00", CurrencyFormatter.format(-500L));
    }

    /** Formats the signed_marks income and expense distinctly for display. */
    @Test
    public void formatSigned_marksIncomeAndExpenseDistinctly() {
        assertEquals("+LKR 10.00", CurrencyFormatter.formatSigned(1000L, true));
        assertEquals("-LKR 10.00", CurrencyFormatter.formatSigned(1000L, false));
    }

    // ---- Parsing: valid input -------------------------------------------

    /** Reads the _accepts plain decimal back out of text. */
    @Test
    public void parse_acceptsPlainDecimal() {
        assertEquals(1250L, CurrencyFormatter.parseToMinor("12.50"));
    }

    /** Reads the _accepts whole number back out of text. */
    @Test
    public void parse_acceptsWholeNumber() {
        assertEquals(1200L, CurrencyFormatter.parseToMinor("12"));
    }

    /** Reads the _accepts single decimal place back out of text. */
    @Test
    public void parse_acceptsSingleDecimalPlace() {
        assertEquals(1250L, CurrencyFormatter.parseToMinor("12.5"));
    }

    /** Reads the _strips currency symbol thousands separators and spaces back out of text. */
    @Test
    public void parse_stripsCurrencySymbolThousandsSeparatorsAndSpaces() {
        assertEquals(123_456L, CurrencyFormatter.parseToMinor(" LKR 1,234.56 "));
    }

    /** Reads the _accepts smallest valid amount back out of text. */
    @Test
    public void parse_acceptsSmallestValidAmount() {
        assertEquals(1L, CurrencyFormatter.parseToMinor("0.01"));
    }

    /** Reads the _accepts maximum amount back out of text. */
    @Test
    public void parse_acceptsMaximumAmount() {
        assertEquals(CurrencyFormatter.MAX_AMOUNT_MINOR,
                CurrencyFormatter.parseToMinor("10000000.00"));
    }

    // ---- Parsing: rejected input ----------------------------------------

    /** Reads the _rejects null back out of text. */
    @Test
    public void parse_rejectsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.parseToMinor(null));
    }

    /** Reads the _rejects empty and whitespace back out of text. */
    @Test
    public void parse_rejectsEmptyAndWhitespace() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parseToMinor(""));
        assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parseToMinor("   "));
    }

    /** Reads the _rejects zero back out of text. */
    @Test
    public void parse_rejectsZero() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parseToMinor("0"));
        assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parseToMinor("0.00"));
    }

    /** Reads the _rejects negative with an actionable message back out of text. */
    @Test
    public void parse_rejectsNegativeWithAnActionableMessage() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.parseToMinor("-5.00"));
        // The message must tell the user what to do instead, not just that it failed.
        assertTrue(e.getMessage().contains("Income/Expense"));
    }

    /** Reads the _rejects letters back out of text. */
    @Test
    public void parse_rejectsLetters() {
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.parseToMinor("twelve"));
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.parseToMinor("12abc"));
    }

    /** Reads the _rejects more than two decimal places back out of text. */
    @Test
    public void parse_rejectsMoreThanTwoDecimalPlaces() {
        // Rounding silently would misreport the amount the user believed they entered.
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.parseToMinor("12.505"));
    }

    /** Reads the _rejects scientific notation back out of text. */
    @Test
    public void parse_rejectsScientificNotation() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parseToMinor("1e9"));
    }

    /** Reads the _rejects lone decimal point back out of text. */
    @Test
    public void parse_rejectsLoneDecimalPoint() {
        assertThrows(IllegalArgumentException.class, () -> CurrencyFormatter.parseToMinor("."));
    }

    /** Reads the _rejects amount above maximum back out of text. */
    @Test
    public void parse_rejectsAmountAboveMaximum() {
        assertThrows(IllegalArgumentException.class,
                () -> CurrencyFormatter.parseToMinor("10000000.01"));
    }

    // ---- Non-throwing variant -------------------------------------------

    /** Reads the or default_returns fallback for blank input back out of text. */
    @Test
    public void parseOrDefault_returnsFallbackForBlankInput() {
        assertEquals(99L, CurrencyFormatter.parseToMinorOrDefault("", 99L));
        assertEquals(99L, CurrencyFormatter.parseToMinorOrDefault(null, 99L));
    }

    /** Reads the or default_returns fallback for invalid input back out of text. */
    @Test
    public void parseOrDefault_returnsFallbackForInvalidInput() {
        assertEquals(0L, CurrencyFormatter.parseToMinorOrDefault("not a number", 0L));
    }

    /** Reads the or default_returns parsed value when valid back out of text. */
    @Test
    public void parseOrDefault_returnsParsedValueWhenValid() {
        assertEquals(1250L, CurrencyFormatter.parseToMinorOrDefault("12.50", 0L));
    }

    // ---- Round-trip and the floating-point argument ----------------------

    /** Formats the then parse_is lossless for display. */
    @Test
    public void formatThenParse_isLossless() {
        // Round-trip property: parse(format(x)) == x for every representative value.
        long[] values = {1L, 99L, 100L, 12_345L, 999_999L, CurrencyFormatter.MAX_AMOUNT_MINOR};
        for (long value : values) {
            assertEquals("round-trip failed for " + value,
                    value, CurrencyFormatter.parseToMinor(CurrencyFormatter.format(value)));
        }
    }

    /** Integer minor units_avoid the floating point drift that doubles would introduce. */
    @Test
    public void integerMinorUnits_avoidTheFloatingPointDriftThatDoublesWouldIntroduce() {
        // The canonical demonstration of why money is not a double.
        double floatingSum = 0.1 + 0.2;
        assertNotEquals(0.3, floatingSum, 0.0);

        // The same arithmetic in minor units is exact.
        long a = CurrencyFormatter.parseToMinor("0.10");
        long b = CurrencyFormatter.parseToMinor("0.20");
        assertEquals(CurrencyFormatter.parseToMinor("0.30"), a + b);
    }

    /** Repeated addition stays exact. */
    @Test
    public void repeatedAdditionStaysExact() {
        long total = 0L;
        for (int i = 0; i < 10_000; i++) {
            total += CurrencyFormatter.parseToMinor("0.01");
        }
        assertEquals("LKR 100.00", CurrencyFormatter.format(total));
    }
}
