package com.spendwise.domain;

/** How a payment was made. Used as a filter dimension as well as a label. */
public enum PaymentMethod {
    CARD("Card"),
    CASH("Cash"),
    BANK_TRANSFER("Bank Transfer"),
    DIRECT_DEBIT("Direct Debit"),
    MOBILE_WALLET("Mobile Wallet");

    private final String displayName;

    PaymentMethod(String displayName) {
        this.displayName = displayName;
    }

    /** Returns the display name. */
    public String getDisplayName() {
        return displayName;
    }

    /** From name or null. */
    public static PaymentMethod fromNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        for (PaymentMethod m : values()) {
            if (m.name().equalsIgnoreCase(name.trim())) {
                return m;
            }
        }
        return null;
    }
}
