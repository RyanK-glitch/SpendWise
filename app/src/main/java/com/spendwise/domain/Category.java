package com.spendwise.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The twelve categories, ten expense and two income. Each carries its display name,
 * its colour and the direction it belongs to, so the entry screen can offer only the
 * categories that match the chosen direction.
 */
public enum Category {
    GROCERIES("Groceries", "#4CAF50", TransactionType.EXPENSE),
    RENT("Rent & Mortgage", "#795548", TransactionType.EXPENSE),
    UTILITIES("Utilities", "#00BCD4", TransactionType.EXPENSE),
    TRANSPORT("Transport", "#3F51B5", TransactionType.EXPENSE),
    DINING("Dining Out", "#FF9800", TransactionType.EXPENSE),
    ENTERTAINMENT("Entertainment", "#E91E63", TransactionType.EXPENSE),
    HEALTH("Health & Fitness", "#009688", TransactionType.EXPENSE),
    SHOPPING("Shopping", "#9C27B0", TransactionType.EXPENSE),
    EDUCATION("Education", "#607D8B", TransactionType.EXPENSE),
    TRAVEL("Travel", "#FF5722", TransactionType.EXPENSE),
    SALARY("Salary", "#2E7D32", TransactionType.INCOME),
    OTHER_INCOME("Other Income", "#8BC34A", TransactionType.INCOME);

    private final String displayName;
    private final String colourHex;
    private final TransactionType naturalType;

    Category(String displayName, String colourHex, TransactionType naturalType) {
        this.displayName = displayName;
        this.colourHex = colourHex;
        this.naturalType = naturalType;
    }

    /** Returns the display name. */
    public String getDisplayName() {
        return displayName;
    }

    /** Returns the colour hex. */
    public String getColourHex() {
        return colourHex;
    }

    /** Returns the natural type. */
    public TransactionType getNaturalType() {
        return naturalType;
    }

    /** Expense categories. */
    public static List<Category> expenseCategories() {
        List<Category> result = new ArrayList<>();
        for (Category c : values()) {
            if (c.naturalType == TransactionType.EXPENSE) {
                result.add(c);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** From name or null. */
    public static Category fromNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        for (Category c : values()) {
            if (c.name().equalsIgnoreCase(name.trim())) {
                return c;
            }
        }
        return null;
    }
}
