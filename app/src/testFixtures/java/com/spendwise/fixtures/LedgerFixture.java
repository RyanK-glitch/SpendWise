package com.spendwise.fixtures;

import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;
import com.spendwise.domain.Category;
import com.spendwise.domain.Guard;
import com.spendwise.domain.PaymentMethod;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Generates a realistic twelve-month ledger for verification.
 *
 * <p>SpendWise ships with an empty database: a personal finance application that
 * invents transactions on its user's behalf is worse than one that starts empty.
 * Search and filtering nevertheless have to be <em>verified</em> at a scale a
 * hand-typed ledger cannot reach, which is what this class exists for.
 *
 * <p>It lives in {@code src/testFixtures}, a source directory added only to the
 * {@code test} and {@code androidTest} source sets. The shipped APK therefore
 * cannot contain fabricated data by construction rather than by discipline.
 *
 * <p>Amounts are dimensionless minor units. The domain never names a currency ,
 * only {@code CurrencyFormatter} does, so the fixture's ranges are chosen to
 * exercise the amount filter across several orders of magnitude rather than to
 * model any particular economy.
 */
public final class LedgerFixture {

    /** Initial capacity for the backing list, not a cap. The generator produces about
     *  450 rows; the exact count varies with month lengths and the run date, because
     *  it refuses to create future-dated transactions. */
    public static final int TARGET_TRANSACTIONS = 460;

    private static final long RANDOM_SEED = 20260903L;
    private static final int MONTHS_OF_HISTORY = 12;

    private LedgerFixture() {
        // Utility class.
    }

    /** Merchant names per category, what makes the search box worth exercising. */
    private static final String[][] MERCHANTS = {
            // GROCERIES
            {"Keells Super", "Cargills Food City", "Arpico Supercentre", "Laugfs Super",
                    "Glomark", "Lanka Sathosa"},
            // RENT
            {"Monthly Rent", "Landlord Standing Order"},
            // UTILITIES
            {"CEB Electricity", "National Water Board", "SLT-Mobitel", "Dialog Broadband",
                    "Litro Gas", "Municipal Rates"},
            // TRANSPORT
            {"PickMe", "Uber Lanka", "Sri Lanka Railways", "Ceypetco Fuel", "SLTB Bus",
                    "Lanka IOC"},
            // DINING
            {"Pilawoos", "Burger King", "KFC Sri Lanka", "Barista Coffee", "Dinemore",
                    "Perera & Sons", "Chinese Dragon", "Cafe Kumbuk"},
            // ENTERTAINMENT
            {"Netflix", "Spotify", "Scope Cinemas", "Savoy Cinema", "Steam", "PlayStation Store"},
            // HEALTH
            {"Osu Sala Pharmacy", "Nawaloka Hospital", "Asiri Health", "Healthguard Pharmacy",
                    "Power World Gym"},
            // SHOPPING
            {"Daraz", "Odel", "House of Fashion", "Abans", "Singer Sri Lanka", "Nolimit",
                    "Softlogic"},
            // EDUCATION
            {"UEL Bookshop", "Coursera", "Udemy", "Sarasavi Bookshop", "Vijitha Yapa"},
            // TRAVEL
            {"SriLankan Airlines", "Booking.com", "Airbnb", "Cinnamon Hotels", "Jetwing"},
            // SALARY
            {"Monthly Salary"},
            // OTHER_INCOME
            {"Freelance Invoice", "Refund", "Cashback", "Gift"}
    };

    /**
     * {min, max} amount in minor units per category, index-aligned with
     * {@link Category#values()}. The spread is deliberately wide, four orders of
     * magnitude from the cheapest bus fare to a month's salary, so that the amount
     * clause of the filter is exercised rather than merely called.
     */
    private static final long[][] AMOUNT_RANGE = {
            {35_000, 450_000},          // GROCERIES
            {3_500_000, 6_500_000},     // RENT
            {120_000, 900_000},         // UTILITIES
            {15_000, 350_000},          // TRANSPORT
            {45_000, 450_000},          // DINING
            {60_000, 250_000},          // ENTERTAINMENT
            {80_000, 600_000},          // HEALTH
            {150_000, 1_800_000},       // SHOPPING
            {200_000, 1_500_000},       // EDUCATION
            {500_000, 4_500_000},       // TRAVEL
            {18_000_000, 26_000_000},   // SALARY
            {150_000, 2_500_000}        // OTHER_INCOME
    };

    /** Relative frequency per category, groceries recur far more often than travel. */
    private static final int[] WEIGHT = {
            34,  // GROCERIES
            12,  // RENT (once a month)
            18,  // UTILITIES
            30,  // TRANSPORT
            34,  // DINING
            16,  // ENTERTAINMENT
            12,  // HEALTH
            22,  // SHOPPING
            8,   // EDUCATION
            6,   // TRAVEL
            12,  // SALARY (once a month)
            8    // OTHER_INCOME
    };

    private static final String[] NOTES = {
            "weekly shop", "with colleagues", "monthly subscription", "reimbursed by work",
            "split with flatmate", "one-off", "annual renewal", "gift for family",
            "emergency", "planned purchase", null, null, null, null
    };

    /** Generate transactions. */
    public static List<Transaction> generateTransactions(long userId, YearMonth endMonth) {
        Guard.require(userId > 0, "userId must be positive");
        Guard.notNull(endMonth, "endMonth");

        Random random = new Random(RANDOM_SEED);
        List<Transaction> ledger = new ArrayList<>(TARGET_TRANSACTIONS);
        Category[] categories = Category.values();
        PaymentMethod[] methods = PaymentMethod.values();

        for (int monthOffset = MONTHS_OF_HISTORY - 1; monthOffset >= 0; monthOffset--) {
            YearMonth month = endMonth.minusMonths(monthOffset);

            for (int categoryIndex = 0; categoryIndex < categories.length; categoryIndex++) {
                Category category = categories[categoryIndex];

                int count = countForMonth(category, categoryIndex, random);
                for (int i = 0; i < count; i++) {
                    int dayOfMonth = dayFor(category, month, random);
                    LocalDate date = month.atDay(dayOfMonth);

                    if (date.isAfter(LocalDate.now())) {
                        continue;
                    }

                    String merchant = MERCHANTS[categoryIndex][
                            random.nextInt(MERCHANTS[categoryIndex].length)];
                    long amount = randomAmount(categoryIndex, random);
                    String note = NOTES[random.nextInt(NOTES.length)];

                    PaymentMethod method = methodFor(category, methods, random);

                    ledger.add(Transaction.create(
                            userId,
                            merchant,
                            note,
                            amount,
                            category.getNaturalType(),
                            category,
                            method,
                            date));
                }
            }
        }
        return ledger;
    }

    /** A matching set of monthly budgets, so budget states can be exercised too. */
    public static List<Budget> generateBudgets(long userId, YearMonth month) {
        Guard.require(userId > 0, "userId must be positive");
        List<Budget> budgets = new ArrayList<>();
        budgets.add(Budget.create(userId, Category.GROCERIES, 1_200_000L, month));
        budgets.add(Budget.create(userId, Category.DINING, 800_000L, month));
        budgets.add(Budget.create(userId, Category.TRANSPORT, 500_000L, month));
        budgets.add(Budget.create(userId, Category.ENTERTAINMENT, 300_000L, month));
        budgets.add(Budget.create(userId, Category.SHOPPING, 1_500_000L, month));
        budgets.add(Budget.create(userId, Category.UTILITIES, 900_000L, month));
        return budgets;
    }

    /** Count for month. */
    private static int countForMonth(Category category, int index, Random random) {
        // Rent and salary occur exactly once a month; everything else varies.
        if (category == Category.RENT || category == Category.SALARY) {
            return 1;
        }
        int base = WEIGHT[index] / 6;
        return Math.max(1, base + random.nextInt(3));
    }

    /** Day for. */
    private static int dayFor(Category category, YearMonth month, Random random) {
        int lengthOfMonth = month.lengthOfMonth();
        if (category == Category.RENT) {
            return Math.min(1, lengthOfMonth);
        }
        if (category == Category.SALARY) {
            return Math.min(28, lengthOfMonth);
        }
        return 1 + random.nextInt(lengthOfMonth);
    }

    /** Random amount. */
    private static long randomAmount(int categoryIndex, Random random) {
        long min = AMOUNT_RANGE[categoryIndex][0];
        long max = AMOUNT_RANGE[categoryIndex][1];
        long span = max - min + 1;
        long amount = min + (long) (random.nextDouble() * span);
        // Round down to a whole multiple of five major units so the ledger reads like
        // real prices rather than like output from a random number generator.
        amount = Math.max(min, (amount / 500) * 500);
        return amount;
    }

    /** Method for. */
    private static PaymentMethod methodFor(Category category, PaymentMethod[] methods, Random random) {
        if (category == Category.RENT) {
            return PaymentMethod.BANK_TRANSFER;
        }
        if (category == Category.UTILITIES) {
            return PaymentMethod.DIRECT_DEBIT;
        }
        if (category == Category.SALARY) {
            return PaymentMethod.BANK_TRANSFER;
        }
        return methods[random.nextInt(methods.length)];
    }
}
