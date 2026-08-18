package com.spendwise.domain;

import com.spendwise.data.entity.Transaction;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The search and filter rule, as six independent clauses joined with AND. A clause
 * that has not been set is always true, so an empty filter matches every row. The
 * object is immutable and is rebuilt through its builder, which is what makes it safe
 * to hand between threads.
 */
public final class TransactionFilter {
    public static final long NO_MIN_AMOUNT = 0L;
    public static final long NO_MAX_AMOUNT = Long.MAX_VALUE;

    private final String query;
    private final Set<Category> categories;
    private final Set<PaymentMethod> paymentMethods;
    private final TransactionType type;
    private final LocalDate from;
    private final LocalDate to;
    private final long minAmountMinor;
    private final long maxAmountMinor;

    private TransactionFilter(Builder builder) {
        this.query = builder.query == null ? "" : builder.query.trim();
        this.categories = builder.categories.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.categories));
        this.paymentMethods = builder.paymentMethods.isEmpty()
                ? Collections.emptySet()
                : Collections.unmodifiableSet(EnumSet.copyOf(builder.paymentMethods));
        this.type = builder.type;
        this.from = builder.from;
        this.to = builder.to;
        this.minAmountMinor = builder.minAmountMinor;
        this.maxAmountMinor = builder.maxAmountMinor;

        Guard.invariant(this.minAmountMinor <= this.maxAmountMinor,
                "minAmount must not exceed maxAmount");
    }

    /** Empty. */
    public static TransactionFilter empty() {
        return new Builder().build();
    }

    /** Builds the er. */
    public static Builder builder() {
        return new Builder();
    }

    /** To builder. */
    public Builder toBuilder() {
        return new Builder()
                .query(query)
                .categories(categories)
                .paymentMethods(paymentMethods)
                .type(type)
                .dateRange(from, to)
                .amountRange(minAmountMinor, maxAmountMinor);
    }

    /**
     * The specification of the filter, as six clauses joined with AND. Total by design: no
     * search term and no malformed row can make it throw.
     */
    public boolean matches(Transaction t) {
        if (t == null) {
            return false;
        }
        return matchesQuery(t)
                && matchesCategory(t)
                && matchesPaymentMethod(t)
                && matchesType(t)
                && matchesDateRange(t)
                && matchesAmountRange(t);
    }

    /**
     * Runs the predicate over a list in memory. The DAO has an SQL copy of this same rule,
     * and the instrumented differential test checks the two agree.
     */
    public List<Transaction> apply(List<Transaction> source) {
        List<Transaction> result = new ArrayList<>();
        if (source == null) {
            return result;
        }
        for (Transaction t : source) {
            if (matches(t)) {
                result.add(t);
            }
        }
        return result;
    }

    /** Matches query. */
    private boolean matchesQuery(Transaction t) {
        if (query.isEmpty()) {
            return true;
        }
        String needle = asciiFold(query);
        return containsFold(t.getDescription(), needle)
                || containsFold(t.getNote(), needle)
                || containsFold(underscoresAsSpaces(t.getCategory()), needle)
                || containsFold(underscoresAsSpaces(t.getPaymentMethod()), needle);
    }

    /** Underscores as spaces. */
    private static String underscoresAsSpaces(String stored) {
        return stored == null ? "" : stored.replace('_', ' ');
    }

    /** Ascii fold. */
    private static String asciiFold(String value) {
        char[] out = value.toCharArray();
        for (int i = 0; i < out.length; i++) {
            if (out[i] >= 'A' && out[i] <= 'Z') {
                out[i] += 32;
            }
        }
        return new String(out);
    }

    /** Contains fold. */
    private static boolean containsFold(String haystack, String foldedNeedle) {
        return haystack != null && asciiFold(haystack).contains(foldedNeedle);
    }

    /** Matches category. */
    private boolean matchesCategory(Transaction t) {
        return categories.isEmpty() || categories.contains(t.categoryAsEnum());
    }

    /** Matches payment method. */
    private boolean matchesPaymentMethod(Transaction t) {
        return paymentMethods.isEmpty() || paymentMethods.contains(t.paymentMethodAsEnum());
    }

    /** Matches type. */
    private boolean matchesType(Transaction t) {
        return type == null || type == t.typeAsEnum();
    }

    /** Matches date range. */
    private boolean matchesDateRange(Transaction t) {
        LocalDate date = t.getDate();
        if (date == null) {
            return from == null && to == null;
        }
        if (from != null && date.isBefore(from)) {
            return false;
        }
        return to == null || !date.isAfter(to);
    }

    /** Matches amount range. */
    private boolean matchesAmountRange(Transaction t) {
        long amount = t.getAmountMinor();
        return amount >= minAmountMinor && amount <= maxAmountMinor;
    }

    /** True when empty. */
    public boolean isEmpty() {
        return query.isEmpty()
                && categories.isEmpty()
                && paymentMethods.isEmpty()
                && type == null
                && from == null
                && to == null
                && minAmountMinor == NO_MIN_AMOUNT
                && maxAmountMinor == NO_MAX_AMOUNT;
    }

    /** Active criteria count. */
    public int activeCriteriaCount() {
        int count = 0;
        if (!query.isEmpty()) count++;
        if (!categories.isEmpty()) count++;
        if (!paymentMethods.isEmpty()) count++;
        if (type != null) count++;
        if (from != null || to != null) count++;
        if (minAmountMinor != NO_MIN_AMOUNT || maxAmountMinor != NO_MAX_AMOUNT) count++;
        return count;
    }

    /** Returns the query. */
    public String getQuery() {
        return query;
    }

    /** Returns the categories. */
    public Set<Category> getCategories() {
        return categories;
    }

    /** Returns the payment methods. */
    public Set<PaymentMethod> getPaymentMethods() {
        return paymentMethods;
    }

    /** Returns the type. */
    public TransactionType getType() {
        return type;
    }

    /** Returns the from. */
    public LocalDate getFrom() {
        return from;
    }

    /** Returns the to. */
    public LocalDate getTo() {
        return to;
    }

    /** Returns the min amount minor. */
    public long getMinAmountMinor() {
        return minAmountMinor;
    }

    /** Returns the max amount minor. */
    public long getMaxAmountMinor() {
        return maxAmountMinor;
    }

    public static final class Builder {
        private String query = "";
        private final Set<Category> categories = EnumSet.noneOf(Category.class);
        private final Set<PaymentMethod> paymentMethods = EnumSet.noneOf(PaymentMethod.class);
        private TransactionType type;
        private LocalDate from;
        private LocalDate to;
        private long minAmountMinor = NO_MIN_AMOUNT;
        private long maxAmountMinor = NO_MAX_AMOUNT;

        /** Query. */
        public Builder query(String query) {
            this.query = query == null ? "" : query;
            return this;
        }

        /** Category. */
        public Builder category(Category category) {
            if (category != null) {
                this.categories.add(category);
            }
            return this;
        }

        /** Categories. */
        public Builder categories(Set<Category> categories) {
            this.categories.clear();
            if (categories != null) {
                for (Category c : categories) {
                    if (c != null) {
                        this.categories.add(c);
                    }
                }
            }
            return this;
        }

        /** Payment method. */
        public Builder paymentMethod(PaymentMethod method) {
            if (method != null) {
                this.paymentMethods.add(method);
            }
            return this;
        }

        /** Payment methods. */
        public Builder paymentMethods(Set<PaymentMethod> methods) {
            this.paymentMethods.clear();
            if (methods != null) {
                for (PaymentMethod m : methods) {
                    if (m != null) {
                        this.paymentMethods.add(m);
                    }
                }
            }
            return this;
        }

        /** Type. */
        public Builder type(TransactionType type) {
            this.type = type;
            return this;
        }

        /** Date range. */
        public Builder dateRange(LocalDate from, LocalDate to) {
            if (from != null && to != null && from.isAfter(to)) {
                this.from = to;
                this.to = from;
            } else {
                this.from = from;
                this.to = to;
            }
            return this;
        }

        /** Amount range. */
        public Builder amountRange(long minMinor, long maxMinor) {
            long low = Math.max(NO_MIN_AMOUNT, minMinor);
            long high = maxMinor < 0 ? NO_MAX_AMOUNT : maxMinor;
            if (low > high) {
                long swap = low;
                low = high;
                high = swap;
            }
            this.minAmountMinor = low;
            this.maxAmountMinor = high;
            return this;
        }

        /** Builds the value. */
        public TransactionFilter build() {
            return new TransactionFilter(this);
        }
    }
}
