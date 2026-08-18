package com.spendwise.ui.dashboard;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.repository.BudgetRepository;
import com.spendwise.databinding.FragmentDashboardBinding;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Currency;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.budgets.BudgetAdapter;
import com.spendwise.ui.transactions.TransactionAdapter;
import com.spendwise.ui.transactions.TransactionsViewModel;
import com.spendwise.util.CurrencyFormatter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * The Home tab. Balance, money in and money out for the current month, the budget
 * summary and the most recent entries. The balance is derived from the ledger every
 * time rather than cached, so it cannot go stale.
 */
public class DashboardFragment extends Fragment {
    private static final int RECENT_LIMIT = 5;

    private static final int BUDGET_PREVIEW_LIMIT = 3;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FragmentDashboardBinding binding;
    private TransactionsViewModel viewModel;
    private TransactionAdapter recentAdapter;
    private BudgetAdapter budgetAdapter;

    /** Called by the framework to inflate the fragment's layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentDashboardBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** Called by the framework once the fragment's views exist. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(TransactionsViewModel.class);

        String name = SessionManager.getInstance(requireContext()).getDisplayName();
        binding.textGreeting.setText(getString(R.string.dashboard_greeting,
                name == null ? "there" : name.split(" ")[0]));

        recentAdapter = new TransactionAdapter(transaction -> navigateToTransactions());
        binding.recyclerRecent.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerRecent.setAdapter(recentAdapter);

        budgetAdapter = new BudgetAdapter();
        binding.recyclerBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBudgets.setAdapter(budgetAdapter);

        binding.textSeeAll.setOnClickListener(v -> navigateToTransactions());
        binding.searchEntry.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), com.spendwise.ui.search.GlobalSearchActivity.class)));

        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), this::render);
    }

    /** Draws the value on screen. */
    private void render(@Nullable List<Transaction> ledger) {
        if (binding == null) {
            return;
        }
        List<Transaction> safe = ledger == null ? new ArrayList<>() : ledger;
        Currency currency = CurrencyManager.getCurrency(requireContext());

        binding.textBalance.setText(CurrencyFormatter.formatDisplay(
                BudgetCalculator.balanceMinor(safe), currency));

        List<Transaction> thisMonth = filterToCurrentMonth(safe);
        binding.textIncome.setText(CurrencyFormatter.formatDisplay(
                BudgetCalculator.totalIncomeMinor(thisMonth), currency));
        binding.textExpenses.setText(CurrencyFormatter.formatDisplay(
                BudgetCalculator.totalExpenseMinor(thisMonth), currency));

        List<Transaction> recent = safe.size() > RECENT_LIMIT
                ? new ArrayList<>(safe.subList(0, RECENT_LIMIT))
                : new ArrayList<>(safe);
        recentAdapter.submitList(recent);

        boolean ledgerEmpty = safe.isEmpty();
        binding.layoutEmptyRecent.setVisibility(ledgerEmpty ? View.VISIBLE : View.GONE);
        binding.cardRecent.setVisibility(ledgerEmpty ? View.GONE : View.VISIBLE);

        loadBudgets();
    }

    /** Filter to current month. */
    private List<Transaction> filterToCurrentMonth(List<Transaction> ledger) {
        YearMonth now = YearMonth.now();
        List<Transaction> result = new ArrayList<>();
        for (Transaction t : ledger) {
            LocalDate date = t.getDate();
            if (date != null && date.getYear() == now.getYear()
                    && date.getMonthValue() == now.getMonthValue()) {
                result.add(t);
            }
        }
        return result;
    }

    /** Loads the budgets. */
    private void loadBudgets() {
        long userId = SessionManager.getInstance(requireContext()).getUserId();
        BudgetRepository repository = new BudgetRepository(requireContext());

        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            List<BudgetCalculator.BudgetStatus> statuses;
            try {
                statuses = repository.evaluateAll(userId, YearMonth.now());
            } catch (RuntimeException e) {
                Log.w("DashboardFragment",
                        "Budget evaluation failed: " + e.getClass().getSimpleName());
                statuses = new ArrayList<>();
            }
            final List<BudgetCalculator.BudgetStatus> result = statuses;
            mainHandler.post(() -> {
                if (binding != null) {
                    renderBudgets(result);
                }
            });
        });
    }

    /** Draws the budgets on screen. */
    private void renderBudgets(@Nullable List<BudgetCalculator.BudgetStatus> statuses) {
        List<BudgetCalculator.BudgetStatus> safe =
                statuses == null ? new ArrayList<>() : statuses;
        boolean empty = safe.isEmpty();

        binding.layoutEmptyBudgets.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.groupBudgetSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
        binding.recyclerBudgets.setVisibility(empty ? View.GONE : View.VISIBLE);

        if (empty) {
            budgetAdapter.submitList(new ArrayList<>());
            return;
        }

        long spentMinor = 0L;
        long limitMinor = 0L;
        BudgetCalculator.BudgetState worst = BudgetCalculator.BudgetState.ON_TRACK;
        for (BudgetCalculator.BudgetStatus status : safe) {
            spentMinor += status.getSpentMinor();
            limitMinor += status.getBudget().getLimitMinor();
            if (status.getState().ordinal() > worst.ordinal()) {
                worst = status.getState();
            }
        }

        int percent = limitMinor > 0
                ? (int) Math.min(100L, (spentMinor * 100L) / limitMinor)
                : 0;

        int accent = ContextCompat.getColor(requireContext(), accentFor(worst));
        binding.progressBudgetOverall.setIndicatorColor(accent);
        binding.progressBudgetOverall.setProgressCompat(percent, true);
        binding.textBudgetPercent.setTextColor(accent);
        binding.textBudgetPercent.setText(getString(R.string.budgets_percent, percent));
        Currency currency = CurrencyManager.getCurrency(requireContext());
        binding.textBudgetSummary.setText(getString(R.string.budgets_spent_of,
                CurrencyFormatter.formatDisplay(spentMinor, currency),
                CurrencyFormatter.formatDisplay(limitMinor, currency)));

        List<BudgetCalculator.BudgetStatus> preview = safe.size() > BUDGET_PREVIEW_LIMIT
                ? new ArrayList<>(safe.subList(0, BUDGET_PREVIEW_LIMIT))
                : new ArrayList<>(safe);
        budgetAdapter.submitList(preview);
    }

    /** Accent for. */
    @ColorRes
    private static int accentFor(BudgetCalculator.BudgetState state) {
        switch (state) {
            case EXCEEDED:
                return R.color.status_exceeded;
            case WARNING:
                return R.color.status_warning;
            default:
                return R.color.brand_primary;
        }
    }

    /** Navigate to transactions. */
    private void navigateToTransactions() {
        NavHostFragment.findNavController(this).navigate(R.id.transactionsFragment);
    }

    /** Called by the framework as the fragment's views are torn down. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
