package com.spendwise.ui.reports;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.spendwise.R;
import com.spendwise.data.entity.Transaction;
import com.spendwise.databinding.FragmentReportsBinding;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.TransactionType;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.transactions.TransactionsViewModel;
import com.spendwise.util.CurrencyFormatter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Reports tab, which is the separate charts page. Spending is broken down by
 * category for a chosen month.
 */
public class ReportsFragment extends Fragment {
    private static final DateTimeFormatter PERIOD_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK);

    private static final String NO_VALUE = "\u2014";

    private FragmentReportsBinding binding;
    private CategoryReportAdapter adapter;

    /** Called by the framework to inflate the fragment's layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentReportsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** Called by the framework once the fragment's views exist. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        adapter = new CategoryReportAdapter();
        binding.recyclerCategories.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerCategories.setAdapter(adapter);

        binding.buttonSearch.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), com.spendwise.ui.search.GlobalSearchActivity.class)));

        TransactionsViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(TransactionsViewModel.class);
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), this::render);
    }

    /** Draws the value on screen. */
    private void render(@Nullable List<Transaction> ledger) {
        if (binding == null) {
            return;
        }

        YearMonth period = YearMonth.now();
        List<Transaction> thisMonth = new ArrayList<>();
        if (ledger != null) {
            for (Transaction t : ledger) {
                LocalDate date = t.getDate();
                if (date != null && date.getYear() == period.getYear()
                        && date.getMonthValue() == period.getMonthValue()) {
                    thisMonth.add(t);
                }
            }
        }

        binding.textPeriod.setText(String.format(Locale.UK, "%s · %s",
                period.format(PERIOD_FORMAT),
                getString(R.string.reports_transactions_count, thisMonth.size())));

        long income = BudgetCalculator.totalIncomeMinor(thisMonth);
        long spent = BudgetCalculator.totalExpenseMinor(thisMonth);
        long net = BudgetCalculator.balanceMinor(thisMonth);

        Currency currency = CurrencyManager.getCurrency(requireContext());
        binding.textTotalIncome.setText(CurrencyFormatter.formatDisplay(income, currency));
        binding.textTotalSpent.setText(CurrencyFormatter.formatDisplay(spent, currency));
        binding.textNet.setText(CurrencyFormatter.formatDisplay(net, currency));
        binding.textNet.setTextColor(ContextCompat.getColor(requireContext(),
                net >= 0 ? R.color.status_income : R.color.status_expense));

        Map<Category, Long> byCategory = BudgetCalculator.spendByCategory(thisMonth);
        List<CategoryReportAdapter.CategoryTotal> rows =
                CategoryReportAdapter.buildRows(byCategory);
        adapter.submit(rows);

        renderSummary(byCategory, spent, countExpenses(thisMonth));

        boolean empty = rows.isEmpty();
        binding.layoutEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerCategories.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Draws the summary on screen. */
    private void renderSummary(Map<Category, Long> byCategory,
                               long spentMinor, int expenseCount) {
        Category largest = null;
        long largestMinor = 0L;
        for (Map.Entry<Category, Long> entry : byCategory.entrySet()) {
            long amount = entry.getValue() == null ? 0L : entry.getValue();
            if (largest == null || amount > largestMinor) {
                largest = entry.getKey();
                largestMinor = amount;
            }
        }

        if (largest == null || spentMinor <= 0L) {
            binding.textLargestCategory.setText(NO_VALUE);
            binding.textLargestCategoryDetail.setText(
                    getString(R.string.reports_share_of_spend, 0));
            tintKey(ContextCompat.getColor(requireContext(), R.color.outline));
        } else {
            int share = (int) ((largestMinor * 100L) / spentMinor);
            binding.textLargestCategory.setText(largest.getDisplayName());
            binding.textLargestCategoryDetail.setText(
                    getString(R.string.reports_share_of_spend, share));
            tintKey(Color.parseColor(largest.getColourHex()));
        }

        long average = expenseCount == 0 ? 0L : spentMinor / expenseCount;
        binding.textAverageTransaction.setText(expenseCount == 0 ? NO_VALUE
                : CurrencyFormatter.formatDisplay(
                        average, CurrencyManager.getCurrency(requireContext())));
        binding.textAverageDetail.setText(
                getString(R.string.reports_transactions_count, expenseCount));
    }

    /** Tint key. */
    private void tintKey(int colour) {
        ViewCompat.setBackgroundTintList(binding.viewLargestCategoryKey,
                ColorStateList.valueOf(colour));
    }

    /** Count expenses. */
    private static int countExpenses(List<Transaction> ledger) {
        int count = 0;
        for (Transaction t : ledger) {
            if (t != null && t.typeAsEnum() == TransactionType.EXPENSE) {
                count++;
            }
        }
        return count;
    }

    /** Called by the framework as the fragment's views are torn down. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
