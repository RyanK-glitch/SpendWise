package com.spendwise.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The display currencies. Rates are fixed constants against the base currency and
 * conversion is for display only, so the stored amount never changes.
 */
public enum Currency {
    LKR("LKR", "LKR ", "Sri Lankan Rupee", BigDecimal.ONE),
    USD("USD", "$", "US Dollar", new BigDecimal("300.00")),
    EUR("EUR", "€", "Euro", new BigDecimal("325.00")),
    GBP("GBP", "£", "British Pound", new BigDecimal("380.00")),
    AUD("AUD", "A$", "Australian Dollar", new BigDecimal("195.00")),
    CAD("CAD", "C$", "Canadian Dollar", new BigDecimal("215.00")),
    NZD("NZD", "NZ$", "New Zealand Dollar", new BigDecimal("178.00")),
    SGD("SGD", "S$", "Singapore Dollar", new BigDecimal("220.00")),
    CHF("CHF", "CHF ", "Swiss Franc", new BigDecimal("345.00")),
    CNY("CNY", "CN¥", "Chinese Yuan", new BigDecimal("41.50")),
    INR("INR", "₹", "Indian Rupee", new BigDecimal("3.55")),
    AED("AED", "AED ", "UAE Dirham", new BigDecimal("81.70")),
    SAR("SAR", "SAR ", "Saudi Riyal", new BigDecimal("80.00")),
    MYR("MYR", "RM", "Malaysian Ringgit", new BigDecimal("67.00")),
    SDG("SDG", "SDG ", "Sudanese Pound", new BigDecimal("0.50"));

    private final String code;
    private final String symbol;
    private final String displayName;
    private final BigDecimal lkrPerUnit;

    Currency(String code, String symbol, String displayName, BigDecimal lkrPerUnit) {
        this.code = code;
        this.symbol = symbol;
        this.displayName = displayName;
        this.lkrPerUnit = lkrPerUnit;
    }

    /** Returns the code. */
    public String getCode() {
        return code;
    }

    /** Returns the symbol. */
    public String getSymbol() {
        return symbol;
    }

    /** Returns the display name. */
    public String getDisplayName() {
        return displayName;
    }

    /** True when base. */
    public boolean isBase() {
        return this == LKR;
    }

    /** Minor from lkr. */
    public long minorFromLkr(long lkrMinor) {
        if (isBase()) {
            return lkrMinor;
        }
        return BigDecimal.valueOf(lkrMinor)
                .divide(lkrPerUnit, 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Never rounds up, so the entry ceiling it produces is itself a legal amount. */
    public long minorFromLkrFloor(long lkrMinor) {
        if (isBase()) {
            return lkrMinor;
        }
        return BigDecimal.valueOf(lkrMinor)
                .divide(lkrPerUnit, 0, RoundingMode.FLOOR)
                .longValueExact();
    }

    /** Minor to lkr. */
    public long minorToLkr(long minorInThisCurrency) {
        if (isBase()) {
            return minorInThisCurrency;
        }
        return BigDecimal.valueOf(minorInThisCurrency)
                .multiply(lkrPerUnit)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /** Unit in lkr minor. */
    public long unitInLkrMinor() {
        return minorToLkr(100L);
    }

    /** From code or null. */
    public static Currency fromCodeOrNull(String code) {
        if (code == null) {
            return null;
        }
        for (Currency c : values()) {
            if (c.code.equalsIgnoreCase(code.trim())) {
                return c;
            }
        }
        return null;
    }
}
