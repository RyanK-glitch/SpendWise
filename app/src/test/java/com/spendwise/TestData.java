package com.spendwise;

import com.spendwise.data.entity.Transaction;
import com.spendwise.domain.Category;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Small hand-built objects shared by the unit tests, so each test file does not repeat
 * the same setup.
 */
public final class TestData {

    public static final long USER_ID = 1L;

    private TestData() {
    }

    /** An expense with sensible defaults. */
    public static Transaction expense(String description, long amountMinor,
                                      Category category, LocalDate date) {
        return Transaction.create(USER_ID, description, null, amountMinor,
                TransactionType.EXPENSE, category, PaymentMethod.CARD, date);
    }

    /** Expense. */
    public static Transaction expense(String description, long amountMinor, Category category,
                                      PaymentMethod method, LocalDate date, String note) {
        return Transaction.create(USER_ID, description, note, amountMinor,
                TransactionType.EXPENSE, category, method, date);
    }

    /** Income. */
    public static Transaction income(String description, long amountMinor, LocalDate date) {
        return Transaction.create(USER_ID, description, null, amountMinor,
                TransactionType.INCOME, Category.SALARY, PaymentMethod.BANK_TRANSFER, date);
    }

    /** A small mixed ledger used across several suites. */
    public static List<Transaction> sampleLedger() {
        List<Transaction> ledger = new ArrayList<>();
        ledger.add(expense("Tesco Metro", 4_250L, Category.GROCERIES,
                PaymentMethod.CARD, LocalDate.of(2026, 8, 3), "weekly shop"));
        ledger.add(expense("Nando's", 2_890L, Category.DINING,
                PaymentMethod.CARD, LocalDate.of(2026, 8, 7), "with colleagues"));
        ledger.add(expense("TfL Travel", 550L, Category.TRANSPORT,
                PaymentMethod.MOBILE_WALLET, LocalDate.of(2026, 8, 10), null));
        ledger.add(expense("Netflix", 1_099L, Category.ENTERTAINMENT,
                PaymentMethod.DIRECT_DEBIT, LocalDate.of(2026, 8, 12), "monthly subscription"));
        ledger.add(expense("Aldi", 3_120L, Category.GROCERIES,
                PaymentMethod.CASH, LocalDate.of(2026, 8, 18), null));
        ledger.add(income("Monthly Salary", 245_000L, LocalDate.of(2026, 8, 28)));
        return ledger;
    }
}
