package com.spendwise.domain;

/** Income or expense. Kept separate from the amount so an amount is always positive. */
public enum TransactionType {
    INCOME("Income"),
    EXPENSE("Expense");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    /** Returns the display name. */
    public String getDisplayName() {
        return displayName;
    }

    /** Returns the sign. */
    public int getSign() {
        return this == INCOME ? 1 : -1;
    }

    /** From name or null. */
    public static TransactionType fromNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        for (TransactionType t : values()) {
            if (t.name().equalsIgnoreCase(name.trim())) {
                return t;
            }
        }
        return null;
    }
}
