package com.spendwise.util;

import com.spendwise.domain.Currency;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/**
 * Turns text into a whole number of minor units and back again. Money is never a double,
 * so totals cannot drift. Parsing whitelists the accepted shape and turns every bad
 * input into a message the screen can show.
 */
public final class CurrencyFormatter {
    public static final long MAX_AMOUNT_MINOR = 1_000_000_000L;

    public static final String CURRENCY_CODE = "LKR";

    private static final String SYMBOL = CURRENCY_CODE + " ";

    private static final Locale MONEY_LOCALE = new Locale("en", "LK");

    private static final String[] ACCEPTED_SYMBOLS = {
            CURRENCY_CODE, "රු.", "රු", "Rs.", "Rs", "₨"
    };

    private CurrencyFormatter() {
    }

    /** Formats the value for display. */
    public static String format(long amountMinor) {
        DecimalFormat df = new DecimalFormat("#,##0.00",
                new DecimalFormatSymbols(MONEY_LOCALE));
        String sign = amountMinor < 0 ? "-" : "";
        BigDecimal major = BigDecimal.valueOf(amountMinor)
                .abs()
                .movePointLeft(2);
        return sign + SYMBOL + df.format(major);
    }

    /** Formats the signed for display. */
    public static String formatSigned(long amountMinor, boolean isIncome) {
        return (isIncome ? "+" : "-") + format(Math.abs(amountMinor));
    }

    /**
     * Text to whole minor units. Every bad input throws with a reason attached, and never
     * returns a silently wrong number.
     */
    public static long parseToMinor(String input) {
        if (input == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        // Strip every currency symbol a user might paste in before parsing.
        String cleaned = input.trim();
        for (String symbol : ACCEPTED_SYMBOLS) {
            cleaned = cleaned.replace(symbol, "");
        }
        cleaned = cleaned
                .replace(",", "")
                .replace(" ", "")

                .replace(" ", "");

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (cleaned.startsWith("-")) {
            throw new IllegalArgumentException(
                    "Amount must be positive, use the Income/Expense switch instead");
        }
        if (!cleaned.matches("\\d*(\\.\\d{0,2})?") || cleaned.equals(".")) {
            throw new IllegalArgumentException("Enter a valid amount, e.g. 12.50");
        }

        try {
            long minor = new BigDecimal(cleaned)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            if (minor <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }
            if (minor > MAX_AMOUNT_MINOR) {
                throw new IllegalArgumentException(
                        "Amount cannot exceed " + format(MAX_AMOUNT_MINOR));
            }
            return minor;
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid amount, e.g. 12.50");
        }
    }

    /** Reads the to minor or default back out of text. */
    public static long parseToMinorOrDefault(String input, long fallback) {
        if (input == null || input.trim().isEmpty()) {
            return fallback;
        }
        try {
            return parseToMinor(input);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Formats the display for display. */
    public static String formatDisplay(long amountMinorLkr, Currency currency) {
        if (currency.isBase()) {
            return format(amountMinorLkr);
        }
        return formatMinorIn(currency.minorFromLkr(amountMinorLkr), currency);
    }

    /** Formats an amount already counted in {@code currency}'s own minor units. */
    private static String formatMinorIn(long minorInCurrency, Currency currency) {
        DecimalFormat df = new DecimalFormat("#,##0.00", new DecimalFormatSymbols(MONEY_LOCALE));
        String sign = minorInCurrency < 0 ? "-" : "";
        BigDecimal major = BigDecimal.valueOf(minorInCurrency).abs().movePointLeft(2);
        return sign + currency.getSymbol() + df.format(major);
    }

    /** Formats the signed display for display. */
    public static String formatSignedDisplay(long amountMinorLkr, boolean isIncome,
                                              Currency currency) {
        return (isIncome ? "+" : "-") + formatDisplay(Math.abs(amountMinorLkr), currency);
    }

    /** Reads the to minor display back out of text. */
    public static long parseToMinorDisplay(String input, Currency currency) {
        if (currency.isBase()) {
            return parseToMinor(input);
        }
        if (input == null) {
            throw new IllegalArgumentException("Amount is required");
        }

        // Strip every currency symbol a user might paste in before parsing.
        String cleaned = input.trim();
        cleaned = cleaned.replace(currency.getSymbol(), "").replace(currency.getCode(), "");
        for (String symbol : ACCEPTED_SYMBOLS) {
            cleaned = cleaned.replace(symbol, "");
        }
        cleaned = cleaned.replace(",", "").replace(" ", "").replace(" ", "");

        if (cleaned.isEmpty()) {
            throw new IllegalArgumentException("Amount is required");
        }
        if (cleaned.startsWith("-")) {
            throw new IllegalArgumentException(
                    "Amount must be positive, use the Income/Expense switch instead");
        }
        if (!cleaned.matches("\\d*(\\.\\d{0,2})?") || cleaned.equals(".")) {
            throw new IllegalArgumentException("Enter a valid amount, e.g. 12.50");
        }

        try {
            long minorInCurrency = new BigDecimal(cleaned)
                    .movePointRight(2)
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

            if (minorInCurrency <= 0) {
                throw new IllegalArgumentException("Amount must be greater than zero");
            }

            long ceiling = currency.minorFromLkrFloor(MAX_AMOUNT_MINOR);
            if (minorInCurrency > ceiling) {
                throw new IllegalArgumentException("Amount cannot exceed "
                        + formatMinorIn(ceiling, currency));
            }
            return currency.minorToLkr(minorInCurrency);
        } catch (ArithmeticException | NumberFormatException e) {
            throw new IllegalArgumentException("Enter a valid amount, e.g. 12.50");
        }
    }

    /** Reads the to minor display or default back out of text. */
    public static long parseToMinorDisplayOrDefault(String input, Currency currency,
                                                     long fallback) {
        if (input == null || input.trim().isEmpty()) {
            return fallback;
        }
        try {
            return parseToMinorDisplay(input, currency);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Strip symbol. */
    public static String stripSymbol(String input, Currency currency) {
        String cleaned = input == null ? "" : input.trim();
        cleaned = cleaned.replace(currency.getSymbol(), "").replace(currency.getCode(), "");
        for (String symbol : ACCEPTED_SYMBOLS) {
            cleaned = cleaned.replace(symbol, "");
        }
        return cleaned.replace(",", "").trim();
    }
}
