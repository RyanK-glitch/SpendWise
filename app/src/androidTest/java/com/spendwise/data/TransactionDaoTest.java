package com.spendwise.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.room.Room;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.spendwise.data.dao.BudgetDao;
import com.spendwise.data.dao.TransactionDao;
import com.spendwise.data.dao.UserDao;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.entity.User;
import com.spendwise.data.repository.TransactionRepository;
import com.spendwise.domain.AuthProvider;
import com.spendwise.domain.Category;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionFilter;
import com.spendwise.domain.TransactionType;
import com.spendwise.fixtures.LedgerFixture;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Instrumented tests for Room against a real SQLite engine, including the differential
 * test that runs twenty-one filters through both the SQL and the Java predicate and
 * checks the two agree row for row.
 */
@RunWith(AndroidJUnit4.class)
public class TransactionDaoTest {

    private SpendWiseDatabase database;
    private TransactionDao transactionDao;
    private UserDao userDao;
    private BudgetDao budgetDao;
    private TransactionRepository repository;

    private long userId;

    /** Runs submitted work immediately, so tests need no waiting or polling. */
    private static final Executor DIRECT_EXECUTOR = Runnable::run;

    /** Builds the database. */
    @Before
    public void createDatabase() {
        Context context = ApplicationProvider.getApplicationContext();
        database = Room.inMemoryDatabaseBuilder(context, SpendWiseDatabase.class)
                .allowMainThreadQueries()
                .build();

        transactionDao = database.transactionDao();
        userDao = database.userDao();
        budgetDao = database.budgetDao();
        repository = new TransactionRepository(transactionDao, DIRECT_EXECUTOR);

        userId = userDao.insert(User.create("test@example.com", "Test User",
                "hash", AuthProvider.LOCAL));
    }

    /** Close database. */
    @After
    public void closeDatabase() throws IOException {
        database.close();
    }

    // ---- Basic persistence ----------------------------------------------

    /** Writes the ed transaction can be read back intact to storage. */
    @Test
    public void insertedTransactionCanBeReadBackIntact() {
        Transaction t = Transaction.create(userId, "Keells Super", "weekly shop", 425_000L,
                TransactionType.EXPENSE, Category.GROCERIES, PaymentMethod.CARD,
                LocalDate.of(2026, 8, 3));
        long id = transactionDao.insert(t);

        Transaction loaded = transactionDao.findById(id);
        assertNotNull(loaded);
        assertEquals("Keells Super", loaded.getDescription());
        assertEquals("weekly shop", loaded.getNote());
        assertEquals(425_000L, loaded.getAmountMinor());
        assertEquals(Category.GROCERIES, loaded.categoryAsEnum());
        assertEquals(PaymentMethod.CARD, loaded.paymentMethodAsEnum());
        assertEquals(TransactionType.EXPENSE, loaded.typeAsEnum());
    }

    /** Local date survives the round trip through the type converter. */
    @Test
    public void localDateSurvivesTheRoundTripThroughTheTypeConverter() {
        // Epoch-day conversion must be exact, including across a leap day.
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        long id = transactionDao.insert(Transaction.create(userId, "Leap", null, 100L,
                TransactionType.EXPENSE, Category.OTHER_INCOME, PaymentMethod.CARD, leapDay));

        assertEquals(leapDay, transactionDao.findById(id).getDate());
    }

    /** Removes the removes only the targeted row. */
    @Test
    public void deleteRemovesOnlyTheTargetedRow() {
        long keepId = transactionDao.insert(expense("Keep", 100L, LocalDate.of(2026, 8, 1)));
        Transaction remove = expense("Remove", 200L, LocalDate.of(2026, 8, 2));
        remove.setId(transactionDao.insert(remove));

        assertEquals(1, transactionDao.delete(remove));
        assertEquals(1, transactionDao.countForUser(userId));
        assertNotNull(transactionDao.findById(keepId));
    }

    /** Find by id returns null for an absent row. */
    @Test
    public void findByIdReturnsNullForAnAbsentRow() {
        assertNull(transactionDao.findById(99_999L));
    }

    /** Deleting a user cascades to their transactions. */
    @Test
    public void deletingAUserCascadesToTheirTransactions() {
        transactionDao.insert(expense("Keells", 100L, LocalDate.of(2026, 8, 1)));
        assertEquals(1, transactionDao.countForUser(userId));

        User user = userDao.findById(userId);
        userDao.delete(user);

        // Enforced by the foreign key, not by application code.
        assertEquals(0, transactionDao.countForUser(userId));
    }

    /** Duplicate email is rejected by the unique index. */
    @Test
    public void duplicateEmailIsRejectedByTheUniqueIndex() {
        try {
            userDao.insert(User.create("test@example.com", "Impostor", "hash",
                    AuthProvider.LOCAL));
            fail("the unique index on users.email should have rejected this insert");
        } catch (android.database.sqlite.SQLiteConstraintException expected) {
            assertEquals(1, userDao.countByEmail("test@example.com"));
        }
    }

    /** Ledger is returned newest first. */
    @Test
    public void ledgerIsReturnedNewestFirst() {
        transactionDao.insert(expense("Oldest", 100L, LocalDate.of(2026, 6, 1)));
        transactionDao.insert(expense("Newest", 100L, LocalDate.of(2026, 8, 1)));
        transactionDao.insert(expense("Middle", 100L, LocalDate.of(2026, 7, 1)));

        List<Transaction> all = transactionDao.getAll(userId);
        assertEquals("Newest", all.get(0).getDescription());
        assertEquals("Middle", all.get(1).getDescription());
        assertEquals("Oldest", all.get(2).getDescription());
    }

    /** Aggregate sums only the requested category and date window. */
    @Test
    public void aggregateSumsOnlyTheRequestedCategoryAndDateWindow() {
        transactionDao.insert(expense("In window", 1_000L, LocalDate.of(2026, 8, 5)));
        transactionDao.insert(expense("Also in", 2_000L, LocalDate.of(2026, 8, 20)));
        transactionDao.insert(expense("Out of window", 4_000L, LocalDate.of(2026, 7, 15)));

        YearMonth august = YearMonth.of(2026, 8);
        long sum = transactionDao.sumExpenseForCategory(userId, Category.GROCERIES.name(),
                august.atDay(1).toEpochDay(), august.atEndOfMonth().toEpochDay());

        assertEquals(3_000L, sum);
    }

    /** Aggregate returns zero rather than null when nothing matches. */
    @Test
    public void aggregateReturnsZeroRatherThanNullWhenNothingMatches() {
        // IFNULL in the query is what prevents a NullPointerException on unboxing.
        assertEquals(0L, transactionDao.sumExpenseForCategory(userId,
                Category.TRAVEL.name(), 0L, 999_999L));
    }

    // ---- LIKE escaping ---------------------------------------------------

    /** Wildcard characters in a search term are treated literally. */
    @Test
    public void wildcardCharactersInASearchTermAreTreatedLiterally() {
        transactionDao.insert(expense("100% cotton", 1_000L, LocalDate.of(2026, 8, 1)));
        transactionDao.insert(expense("Keells Super", 200_000L, LocalDate.of(2026, 8, 2)));
        transactionDao.insert(expense("Aldi", 3_000L, LocalDate.of(2026, 8, 3)));

        // Unescaped, "%" is a LIKE wildcard and would match all three rows.
        List<Transaction> result = repository.filterBlocking(userId,
                TransactionFilter.builder().query("100%").build());

        assertEquals(1, result.size());
        assertEquals("100% cotton", result.get(0).getDescription());
    }

    /** Underscore in a search term is treated literally. */
    @Test
    public void underscoreInASearchTermIsTreatedLiterally() {
        transactionDao.insert(expense("A_B Store", 1_000L, LocalDate.of(2026, 8, 1)));
        transactionDao.insert(expense("AXB Store", 2_000L, LocalDate.of(2026, 8, 2)));

        // Unescaped, "_" matches any single character and would match both.
        List<Transaction> result = repository.filterBlocking(userId,
                TransactionFilter.builder().query("A_B").build());

        assertEquals(1, result.size());
        assertEquals("A_B Store", result.get(0).getDescription());
    }

    /** A quote in a search term cannot alter the query. */
    @Test
    public void aQuoteInASearchTermCannotAlterTheQuery() {
        transactionDao.insert(expense("Sainsbury's Local", 1_000L, LocalDate.of(2026, 8, 1)));
        transactionDao.insert(expense("Aldi", 2_000L, LocalDate.of(2026, 8, 2)));

        List<Transaction> injection = repository.filterBlocking(userId,
                TransactionFilter.builder().query("' OR '1'='1").build());
        assertEquals(0, injection.size());

        // A legitimate apostrophe still works.
        List<Transaction> legitimate = repository.filterBlocking(userId,
                TransactionFilter.builder().query("Sainsbury's").build());
        assertEquals(1, legitimate.size());
    }

    // ---- Differential tests: SQL vs specification ------------------------

    /** Sql filter agrees with the specification predicate across many criteria. */
    @Test
    public void sqlFilterAgreesWithTheSpecificationPredicateAcrossManyCriteria() {
        seedFullLedger();
        List<Transaction> everything = transactionDao.getAll(userId);

        List<TransactionFilter> cases = filterCases();
        for (TransactionFilter filter : cases) {
            List<Transaction> fromSql = repository.filterBlocking(userId, filter);
            List<Transaction> fromPredicate = filter.apply(everything);

            assertEquals("result size differs for filter with "
                            + filter.activeCriteriaCount() + " criteria",
                    fromPredicate.size(), fromSql.size());

            for (int i = 0; i < fromSql.size(); i++) {
                assertEquals("row " + i + " differs between SQL and predicate",
                        fromPredicate.get(i).getId(), fromSql.get(i).getId());
            }
        }
    }

    /** Empty filter returns the whole ledger through both paths. */
    @Test
    public void emptyFilterReturnsTheWholeLedgerThroughBothPaths() {
        seedFullLedger();
        int total = transactionDao.countForUser(userId);

        assertEquals(total, repository.filterBlocking(userId,
                TransactionFilter.empty()).size());
        assertEquals(total, TransactionFilter.empty()
                .apply(transactionDao.getAll(userId)).size());
    }

    /** Indexed filter query stays fast on a full ledger. */
    @Test
    public void indexedFilterQueryStaysFastOnAFullLedger() {
        seedFullLedger();

        TransactionFilter filter = TransactionFilter.builder()
                .category(Category.GROCERIES)
                .dateRange(LocalDate.now().minusMonths(3), LocalDate.now())
                .build();

        long start = System.nanoTime();
        List<Transaction> result = repository.filterBlocking(userId, filter);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000L;

        assertTrue("filter returned nothing, so the timing is meaningless",
                result.size() > 0);
        assertTrue("filter query took " + elapsedMillis + "ms on a seeded ledger",
                elapsedMillis < 1_000L);
    }

    /** A spread of filters covering each clause alone and several in combination. */
    private List<TransactionFilter> filterCases() {
        List<TransactionFilter> cases = new ArrayList<>();

        cases.add(TransactionFilter.empty());
        cases.add(TransactionFilter.builder().query("keells").build());
        cases.add(TransactionFilter.builder().query("KEELLS").build());
        cases.add(TransactionFilter.builder().query("weekly").build());
        cases.add(TransactionFilter.builder().query("zzznomatch").build());
        cases.add(TransactionFilter.builder().category(Category.GROCERIES).build());
        cases.add(TransactionFilter.builder()
                .categories(EnumSet.of(Category.GROCERIES, Category.DINING)).build());
        cases.add(TransactionFilter.builder().paymentMethod(PaymentMethod.CASH).build());
        cases.add(TransactionFilter.builder()
                .paymentMethods(EnumSet.of(PaymentMethod.CARD, PaymentMethod.CASH)).build());
        cases.add(TransactionFilter.builder().type(TransactionType.INCOME).build());
        cases.add(TransactionFilter.builder().type(TransactionType.EXPENSE).build());
        cases.add(TransactionFilter.builder()
                .dateRange(LocalDate.now().minusMonths(2), LocalDate.now()).build());
        cases.add(TransactionFilter.builder()
                .dateRange(LocalDate.now().minusMonths(6), null).build());
        cases.add(TransactionFilter.builder()
                .dateRange(null, LocalDate.now().minusMonths(6)).build());
        cases.add(TransactionFilter.builder().amountRange(100_000L, 500_000L).build());
        cases.add(TransactionFilter.builder().amountRange(0L, 20_000L).build());
        cases.add(TransactionFilter.builder().amountRange(5_000_000L, Long.MAX_VALUE).build());

        // Combinations, where a divergence between the two paths is most likely.
        cases.add(TransactionFilter.builder()
                .category(Category.GROCERIES)
                .paymentMethod(PaymentMethod.CARD)
                .build());
        cases.add(TransactionFilter.builder()
                .query("a")
                .type(TransactionType.EXPENSE)
                .amountRange(50_000L, 1_000_000L)
                .build());
        cases.add(TransactionFilter.builder()
                .categories(EnumSet.of(Category.DINING, Category.TRANSPORT))
                .dateRange(LocalDate.now().minusMonths(3), LocalDate.now())
                .type(TransactionType.EXPENSE)
                .build());
        cases.add(TransactionFilter.builder()
                .query("o")
                .categories(EnumSet.of(Category.GROCERIES, Category.SHOPPING))
                .paymentMethods(EnumSet.of(PaymentMethod.CARD, PaymentMethod.CASH))
                .type(TransactionType.EXPENSE)
                .dateRange(LocalDate.now().minusMonths(6), LocalDate.now())
                .amountRange(20_000L, 3_000_000L)
                .build());

        return cases;
    }

    /** Seed full ledger. */
    private void seedFullLedger() {
        List<Transaction> ledger =
                LedgerFixture.generateTransactions(userId, YearMonth.now());
        database.runInTransaction(() -> transactionDao.insertAll(ledger));
        assertTrue("seeder produced too little data to test against",
                transactionDao.countForUser(userId) > 200);
    }

    /** Expense. */
    private Transaction expense(String description, long amountMinor, LocalDate date) {
        return Transaction.create(userId, description, null, amountMinor,
                TransactionType.EXPENSE, Category.GROCERIES, PaymentMethod.CARD, date);
    }
}
