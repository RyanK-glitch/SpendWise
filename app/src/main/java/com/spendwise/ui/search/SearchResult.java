package com.spendwise.ui.search;

import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;

import com.spendwise.domain.Category;

/**
 * One row of the global search list. It is either a section header or a result, which is
 * what lets a single adapter draw a grouped list.
 */
public final class SearchResult {
    public static final int TYPE_SECTION = 0;
    public static final int TYPE_TRANSACTION = 1;
    public static final int TYPE_BUDGET = 2;
    public static final int TYPE_CATEGORY = 3;

    public static final int NO_COLOUR = 0;

    private final int type;
    private final String title;
    private final String subtitle;
    private final String trailing;
    private final Category category;
    private final long transactionId;
    private final int trailingColourRes;

    private SearchResult(int type,
                         String title,
                         @Nullable String subtitle,
                         @Nullable String trailing,
                         @Nullable Category category,
                         long transactionId,
                         @ColorRes int trailingColourRes) {
        this.type = type;
        this.title = title == null ? "" : title;
        this.subtitle = subtitle;
        this.trailing = trailing;
        this.category = category;
        this.transactionId = transactionId;
        this.trailingColourRes = trailingColourRes;
    }

    /** Section. */
    public static SearchResult section(String label, String count) {
        return new SearchResult(TYPE_SECTION, label, null, count, null, 0L, NO_COLOUR);
    }

    /** Transaction. */
    public static SearchResult transaction(long id,
                                           String description,
                                           String meta,
                                           String amount,
                                           Category category,
                                           @ColorRes int amountColourRes) {
        return new SearchResult(TYPE_TRANSACTION, description, meta, amount,
                category, id, amountColourRes);
    }

    /** Budget. */
    public static SearchResult budget(Category category, String period, String limit) {
        return new SearchResult(TYPE_BUDGET, category.getDisplayName(), period, limit,
                category, 0L, NO_COLOUR);
    }

    /** Category. */
    public static SearchResult category(Category category, String hint) {
        return new SearchResult(TYPE_CATEGORY, category.getDisplayName(), hint, null,
                category, 0L, NO_COLOUR);
    }

    /** Returns the type. */
    public int getType() {
        return type;
    }

    /** True when section. */
    public boolean isSection() {
        return type == TYPE_SECTION;
    }

    /** Returns the title. */
    public String getTitle() {
        return title;
    }

    /** Returns the subtitle. */
    @Nullable
    public String getSubtitle() {
        return subtitle;
    }

    /** Returns the trailing. */
    @Nullable
    public String getTrailing() {
        return trailing;
    }

    /** Returns the category. */
    @Nullable
    public Category getCategory() {
        return category;
    }

    /** Returns the transaction id. */
    public long getTransactionId() {
        return transactionId;
    }

    /** Returns the trailing colour res. */
    @ColorRes
    public int getTrailingColourRes() {
        return trailingColourRes;
    }
}
