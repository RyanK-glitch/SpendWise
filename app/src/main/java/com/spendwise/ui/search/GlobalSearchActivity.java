package com.spendwise.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.dao.BudgetDao;
import com.spendwise.data.dao.TransactionDao;
import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;
import com.spendwise.databinding.ActivityGlobalSearchBinding;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.TransactionFilter;
import com.spendwise.notification.NotificationHelper;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.MainActivity;
import com.spendwise.ui.SimpleTextWatcher;
import com.spendwise.ui.SystemBars;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Searches transactions, budgets and categories together and shows the results in one
 * grouped list.
 */
public class GlobalSearchActivity extends AppCompatActivity
        implements SearchResultAdapter.OnResultClickListener {
    public static final String EXTRA_DESTINATION = "com.spendwise.extra.SEARCH_DESTINATION";
    public static final String DESTINATION_TRANSACTIONS = "transactions";
    public static final String DESTINATION_BUDGETS = "budgets";

    public static final String EXTRA_QUERY = "com.spendwise.extra.SEARCH_QUERY";

    public static final String EXTRA_CATEGORY = "com.spendwise.extra.SEARCH_CATEGORY";

    private static final long DEBOUNCE_MILLIS = 250L;
    private static final int MAX_TRANSACTIONS = 20;
    private static final int MAX_BUDGETS = 10;

    private static final int BUDGET_MONTHS = 12;

    private static final String STATE_QUERY = "gs_query";

    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM", Locale.UK);
    private static final DateTimeFormatter PERIOD_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK);

    private ActivityGlobalSearchBinding binding;
    private SearchResultAdapter adapter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private int searchToken;

    private long userId;
    private TransactionDao transactionDao;
    private BudgetDao budgetDao;

    /** Called by the framework when the screen is first created. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityGlobalSearchBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        userId = SessionManager.getInstance(this).getUserId();
        SpendWiseDatabase database = SpendWiseDatabase.getInstance(this);
        transactionDao = database.transactionDao();
        budgetDao = database.budgetDao();

        SystemBars.addStatusBarTopMargin(binding.layoutSearchBar);

        adapter = new SearchResultAdapter(this);
        binding.recyclerResults.setLayoutManager(new LinearLayoutManager(this));
        binding.recyclerResults.setAdapter(adapter);

        binding.buttonBack.setOnClickListener(v -> closeSearch());
        binding.buttonClearSearch.setOnClickListener(v -> {
            binding.editSearch.setText("");
            binding.editSearch.requestFocus();
        });

        String restored = savedInstanceState == null
                ? "" : savedInstanceState.getString(STATE_QUERY, "");
        binding.editSearch.setText(restored);
        binding.editSearch.setSelection(restored.length());
        binding.buttonClearSearch.setVisibility(
                restored.isEmpty() ? View.GONE : View.VISIBLE);
        wireSearchInput();
        runSearch(restored);

        binding.editSearch.requestFocus();
        if (savedInstanceState == null) {
            binding.editSearch.post(this::showKeyboard);
        }
    }

    /** Called by the framework so state survives the screen being recreated. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_QUERY, currentQuery());
    }

    /** Wire search input. */
    private void wireSearchInput() {
        binding.editSearch.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (binding == null) {
                return;
            }
            final String query = currentQuery();
            binding.buttonClearSearch.setVisibility(
                    query.isEmpty() ? View.GONE : View.VISIBLE);

            cancelPendingSearch();
            pendingSearch = () -> runSearch(query);
            mainHandler.postDelayed(pendingSearch, DEBOUNCE_MILLIS);
        }));

        binding.editSearch.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                cancelPendingSearch();
                runSearch(currentQuery());
                hideKeyboard();
                return true;
            }
            return false;
        });

        binding.recyclerResults.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    hideKeyboard();
                }
            }
        });
    }

    /** Current query. */
    private String currentQuery() {
        if (binding == null || binding.editSearch.getText() == null) {
            return "";
        }
        return binding.editSearch.getText().toString().trim();
    }

    /** Run search. */
    private void runSearch(@Nullable String raw) {
        final String query = raw == null ? "" : raw.trim();
        searchToken++;
        if (query.isEmpty()) {
            adapter.submit(new ArrayList<>(), "");
            showPrompt();
            return;
        }
        final int token = searchToken;
        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            final Matches matches = collect(query);
            mainHandler.post(() -> {
                if (binding == null || token != searchToken) {
                    return;
                }
                render(query, matches);
            });
        });
    }

    /** Collect. */
    @WorkerThread
    private Matches collect(String query) {
        Matches matches = new Matches();

        TransactionFilter filter = TransactionFilter.builder().query(query).build();
        matches.transactions = filter.apply(transactionDao.getAll(userId));

        String needle = query.toLowerCase(Locale.ROOT);

        YearMonth cursor = YearMonth.now();
        for (int i = 0; i < BUDGET_MONTHS; i++) {
            for (Budget budget : budgetDao.getForPeriod(
                    userId, cursor.getYear(), cursor.getMonthValue())) {
                if (budget.categoryAsEnum().getDisplayName()
                        .toLowerCase(Locale.ROOT).contains(needle)) {
                    matches.budgets.add(budget);
                }
            }
            cursor = cursor.minusMonths(1);
        }

        for (Category category : Category.values()) {
            if (category.getDisplayName().toLowerCase(Locale.ROOT).contains(needle)) {
                matches.categories.add(category);
            }
        }
        return matches;
    }

    /** Draws the value on screen. */
    private void render(String query, Matches matches) {
        List<SearchResult> rows = new ArrayList<>();
        Currency currency = CurrencyManager.getCurrency(this);

        if (!matches.transactions.isEmpty()) {
            rows.add(SearchResult.section(getString(R.string.gs_section_transactions),
                    countLabel(matches.transactions.size(), MAX_TRANSACTIONS)));
            int shown = Math.min(matches.transactions.size(), MAX_TRANSACTIONS);
            for (int i = 0; i < shown; i++) {
                Transaction t = matches.transactions.get(i);
                Category category = t.categoryAsEnum();
                rows.add(SearchResult.transaction(
                        t.getId(),
                        t.getDescription(),
                        getString(R.string.gs_transaction_meta,
                                category.getDisplayName(),
                                t.paymentMethodAsEnum().getDisplayName(),
                                t.getDate().format(DATE_FORMAT)),
                        t.formattedAmount(currency),
                        category,
                        t.isIncome() ? R.color.status_income : R.color.status_expense));
            }
        }

        if (!matches.budgets.isEmpty()) {
            rows.add(SearchResult.section(getString(R.string.gs_section_budgets),
                    countLabel(matches.budgets.size(), MAX_BUDGETS)));
            int shown = Math.min(matches.budgets.size(), MAX_BUDGETS);
            for (int i = 0; i < shown; i++) {
                Budget budget = matches.budgets.get(i);
                rows.add(SearchResult.budget(
                        budget.categoryAsEnum(),
                        getString(R.string.gs_budget_meta,
                                budget.period().format(PERIOD_FORMAT)),
                        budget.formattedLimit(currency)));
            }
        }

        if (!matches.categories.isEmpty()) {
            rows.add(SearchResult.section(getString(R.string.gs_section_categories),
                    String.valueOf(matches.categories.size())));
            for (Category category : matches.categories) {
                rows.add(SearchResult.category(category,
                        getString(R.string.gs_category_meta)));
            }
        }

        adapter.submit(rows, query);
        if (rows.isEmpty()) {
            showEmpty(query);
        } else {
            showResults();
        }
    }

    /** Count label. */
    private String countLabel(int total, int cap) {
        return total > cap
                ? getString(R.string.gs_count_truncated, cap, total)
                : String.valueOf(total);
    }

    /** Shows the prompt. */
    private void showPrompt() {
        binding.recyclerResults.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.layoutPrompt.setVisibility(View.VISIBLE);
    }

    /** Shows the empty. */
    private void showEmpty(String query) {
        binding.textEmptyBody.setText(getString(R.string.gs_empty_body, query));
        binding.recyclerResults.setVisibility(View.GONE);
        binding.layoutPrompt.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.VISIBLE);
    }

    /** Shows the results. */
    private void showResults() {
        binding.layoutPrompt.setVisibility(View.GONE);
        binding.layoutEmpty.setVisibility(View.GONE);
        binding.recyclerResults.setVisibility(View.VISIBLE);
        binding.recyclerResults.scrollToPosition(0);
    }

    /** Called by the framework at the matching point in the lifecycle. */
    @Override
    public void onResultClick(SearchResult result) {
        switch (result.getType()) {
            case SearchResult.TYPE_TRANSACTION:

                returnToMain(DESTINATION_TRANSACTIONS, result.getTitle(), null);
                break;
            case SearchResult.TYPE_CATEGORY:
                returnToMain(DESTINATION_TRANSACTIONS, null, result.getCategory());
                break;
            case SearchResult.TYPE_BUDGET:
                returnToMain(DESTINATION_BUDGETS, null, result.getCategory());
                break;
            default:
                break;
        }
    }

    /** Return to main. */
    private void returnToMain(String destination,
                              @Nullable String query,
                              @Nullable Category category) {
        hideKeyboard();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(EXTRA_DESTINATION, destination);

        intent.putExtra(NotificationHelper.EXTRA_DESTINATION, destination);
        if (query != null && !query.isEmpty()) {
            intent.putExtra(EXTRA_QUERY, query);
        }
        if (category != null) {
            intent.putExtra(EXTRA_CATEGORY, category.name());
        }
        startActivity(intent);
        finish();
    }

    /** Close search. */
    private void closeSearch() {
        hideKeyboard();
        finish();
    }

    /** Shows the keyboard. */
    private void showKeyboard() {
        if (binding == null) {
            return;
        }
        WindowCompat.getInsetsController(getWindow(), binding.editSearch)
                .show(WindowInsetsCompat.Type.ime());
    }

    /** Hide keyboard. */
    private void hideKeyboard() {
        if (binding == null) {
            return;
        }
        WindowCompat.getInsetsController(getWindow(), binding.editSearch)
                .hide(WindowInsetsCompat.Type.ime());
    }

    /** Cancel pending search. */
    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            mainHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    /** Called by the framework as the screen is torn down. */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelPendingSearch();
        mainHandler.removeCallbacksAndMessages(null);
        binding = null;
    }

    private static final class Matches {
        List<Transaction> transactions = new ArrayList<>();
        final List<Budget> budgets = new ArrayList<>();
        final List<Category> categories = new ArrayList<>();
    }
}
