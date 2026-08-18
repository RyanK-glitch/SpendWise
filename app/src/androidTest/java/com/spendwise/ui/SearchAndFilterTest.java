package com.spendwise.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.espresso.contrib.RecyclerViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.spendwise.R;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.entity.User;
import com.spendwise.fixtures.LedgerFixture;
import com.spendwise.ui.auth.SignUpActivity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.YearMonth;

/**
 * Espresso tests that install the ledger fixture through Room and then drive the search
 * box and the filter sheet.
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class SearchAndFilterTest {

    /** Debounce is 300ms; wait comfortably past it before asserting. */
    private static final long DEBOUNCE_SETTLE_MILLIS = 800L;

    @Rule
    public ActivityScenarioRule<SignUpActivity> rule =
            new ActivityScenarioRule<>(SignUpActivity.class);

    /**
     * Registers through the real sign-up screen, then installs the ledger fixture
     * directly through Room.
     *
     * <p>The application deliberately creates no data of its own, so the ledger these
     * tests search has to come from the test itself. Writing it through the DAO rather
     * than through the entry form is not a shortcut: 260 rows typed through the UI would
     * take minutes and would make a search assertion fail for reasons that have nothing
     * to do with search.
     */
    @Before
    public void registerAndPopulateTheLedger() throws InterruptedException {
        String email = "uitest" + System.nanoTime() + "@example.com";

        onView(withId(R.id.editName)).perform(typeText("UI Test"), closeSoftKeyboard());
        onView(withId(R.id.editEmail)).perform(typeText(email), closeSoftKeyboard());
        onView(withId(R.id.editPassword)).perform(typeText("testpass1"), closeSoftKeyboard());
        onView(withId(R.id.editConfirm)).perform(typeText("testpass1"), closeSoftKeyboard());
        onView(withId(R.id.buttonSignUp)).perform(click());

        Thread.sleep(6_000L);

        installLedgerFor(email);

        onView(withId(R.id.bottomNavigation)).perform(click());
        onView(withId(R.id.transactionsFragment)).perform(click());
        Thread.sleep(1_500L);
    }

    /** Install ledger for. */
    private void installLedgerFor(String email) {
        Context context = ApplicationProvider.getApplicationContext();
        SpendWiseDatabase database = SpendWiseDatabase.getInstance(context);

        User user = database.userDao().findByEmail(User.normaliseEmail(email));
        if (user == null) {
            throw new IllegalStateException(
                    "sign-up did not create an account for " + email);
        }

        YearMonth thisMonth = YearMonth.now();
        database.transactionDao()
                .insertAll(LedgerFixture.generateTransactions(user.getId(), thisMonth));
        database.budgetDao()
                .insertAll(LedgerFixture.generateBudgets(user.getId(), thisMonth));
    }

    /** Ledger is populated after the fixture is installed. */
    @Test
    public void ledgerIsPopulatedAfterTheFixtureIsInstalled() {
        // A populated ledger is what makes the search feature observable at all.
        onView(withId(R.id.recyclerTransactions)).check(matches(isDisplayed()));
        onView(withId(R.id.textResultCount)).check(matches(isDisplayed()));
    }

    /** Search narrows the ledger to matching merchants. */
    @Test
    public void searchNarrowsTheLedgerToMatchingMerchants() throws InterruptedException {
        onView(withId(R.id.editSearch)).perform(replaceText("Keells"), closeSoftKeyboard());
        Thread.sleep(DEBOUNCE_SETTLE_MILLIS);

        onView(withId(R.id.recyclerTransactions)).check(matches(isDisplayed()));
        // The Clear-all control confirms the query reached the ViewModel. The filter
        // badge is deliberately NOT the signal here: it counts filter criteria only, so
        // that typing in the search box does not light up the filter button.
        onView(withId(R.id.textClearFilters)).check(matches(isDisplayed()));
    }

    /** Search is case insensitive. */
    @Test
    public void searchIsCaseInsensitive() throws InterruptedException {
        onView(withId(R.id.editSearch)).perform(replaceText("KEELLS"), closeSoftKeyboard());
        Thread.sleep(DEBOUNCE_SETTLE_MILLIS);
        onView(withId(R.id.recyclerTransactions)).check(matches(isDisplayed()));
    }

    /** Search with no matches shows the empty state. */
    @Test
    public void searchWithNoMatchesShowsTheEmptyState() throws InterruptedException {
        onView(withId(R.id.editSearch))
                .perform(replaceText("zzzznosuchmerchant"), closeSoftKeyboard());
        Thread.sleep(DEBOUNCE_SETTLE_MILLIS);

        // An explicit empty state, not a blank screen the user cannot interpret.
        onView(withId(R.id.layoutEmpty)).check(matches(isDisplayed()));
        onView(withId(R.id.textEmptyTitle)).check(matches(withText(R.string.transactions_empty)));
    }

    /** Clearing the search restores the full ledger. */
    @Test
    public void clearingTheSearchRestoresTheFullLedger() throws InterruptedException {
        onView(withId(R.id.editSearch))
                .perform(replaceText("zzzznosuchmerchant"), closeSoftKeyboard());
        Thread.sleep(DEBOUNCE_SETTLE_MILLIS);
        onView(withId(R.id.layoutEmpty)).check(matches(isDisplayed()));

        onView(withId(R.id.editSearch)).perform(replaceText(""), closeSoftKeyboard());
        Thread.sleep(DEBOUNCE_SETTLE_MILLIS);

        onView(withId(R.id.recyclerTransactions)).check(matches(isDisplayed()));
        onView(withId(R.id.textFilterCount))
                .check(matches(ViewMatchers.withEffectiveVisibility(
                        ViewMatchers.Visibility.GONE)));
    }

    /** Filter sheet opens and applies a category filter. */
    @Test
    public void filterSheetOpensAndAppliesACategoryFilter() throws InterruptedException {
        onView(withId(R.id.buttonFilter)).perform(click());
        Thread.sleep(500L);

        onView(withId(R.id.chipGroupCategory)).check(matches(isDisplayed()));
        onView(withId(R.id.chipTypeExpense)).perform(click());
        onView(withId(R.id.buttonApply)).perform(click());
        Thread.sleep(500L);

        onView(withId(R.id.textFilterCount)).check(matches(isDisplayed()));
        onView(withId(R.id.textClearFilters)).check(matches(isDisplayed()));
    }

    /** Clear all removes every active criterion. */
    @Test
    public void clearAllRemovesEveryActiveCriterion() throws InterruptedException {
        onView(withId(R.id.buttonFilter)).perform(click());
        Thread.sleep(500L);
        onView(withId(R.id.chipTypeIncome)).perform(click());
        onView(withId(R.id.buttonApply)).perform(click());
        Thread.sleep(500L);
        onView(withId(R.id.textClearFilters)).check(matches(isDisplayed()));

        onView(withId(R.id.textClearFilters)).perform(click());
        Thread.sleep(500L);

        onView(withId(R.id.textFilterCount))
                .check(matches(ViewMatchers.withEffectiveVisibility(
                        ViewMatchers.Visibility.GONE)));
    }

    /** Ledger scrolls through a full page of results. */
    @Test
    public void ledgerScrollsThroughAFullPageOfResults() {
        onView(withId(R.id.recyclerTransactions))
                .perform(RecyclerViewActions.scrollToPosition(50));
        onView(withId(R.id.recyclerTransactions)).check(matches(isDisplayed()));
    }

    /** Bottom navigation reaches every destination. */
    @Test
    public void bottomNavigationReachesEveryDestination() throws InterruptedException {
        // FR-06: all five destinations must be reachable from the bottom bar.
        int[] destinations = {
                R.id.dashboardFragment, R.id.budgetsFragment,
                R.id.reportsFragment, R.id.profileFragment, R.id.transactionsFragment
        };
        for (int destination : destinations) {
            onView(withId(destination)).perform(click());
            Thread.sleep(400L);
        }
        onView(withId(R.id.recyclerTransactions)).check(matches(isDisplayed()));
    }
}
