package com.spendwise.ui.budgets;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.entity.Budget;
import com.spendwise.data.repository.BudgetRepository;
import com.spendwise.databinding.FragmentBudgetsBinding;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.transactions.TransactionsViewModel;
import com.spendwise.util.CurrencyFormatter;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Budgets tab. Lists this month's budgets, most overspent first, and handles adding,
 * editing and deleting a limit.
 */
public class BudgetsFragment extends Fragment {
    private static final DateTimeFormatter PERIOD_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.UK);

    private static final int WARNING_THRESHOLD_PERCENT = Budget.DEFAULT_ALERT_THRESHOLD_PERCENT;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private FragmentBudgetsBinding binding;
    private BudgetAdapter adapter;

    /** Called by the framework to inflate the fragment's layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBudgetsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** Called by the framework once the fragment's views exist. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.textPeriod.setText(YearMonth.now().format(PERIOD_FORMAT));

        adapter = new BudgetAdapter(this::showBudgetActions);
        binding.recyclerBudgets.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerBudgets.setAdapter(adapter);

        View.OnClickListener addBudget = v -> showAddBudgetDialog();
        binding.buttonAddBudget.setOnClickListener(addBudget);
        binding.buttonAddFirstBudget.setOnClickListener(addBudget);

        binding.buttonSearch.setOnClickListener(v -> startActivity(
                new Intent(requireContext(), com.spendwise.ui.search.GlobalSearchActivity.class)));

        TransactionsViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(TransactionsViewModel.class);
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), ignored -> reload());
    }

    /** Reload. */
    private void reload() {
        long userId = SessionManager.getInstance(requireContext()).getUserId();
        BudgetRepository repository = new BudgetRepository(requireContext());

        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            List<BudgetCalculator.BudgetStatus> evaluated;
            try {
                evaluated = repository.evaluateAll(userId, YearMonth.now());
            } catch (RuntimeException e) {
                Log.w("BudgetsFragment", "Budget evaluation failed: "
                        + e.getClass().getSimpleName());
                evaluated = new ArrayList<>();
            }
            final List<BudgetCalculator.BudgetStatus> statuses = evaluated;
            mainHandler.post(() -> {
                if (binding == null) {
                    return;
                }
                adapter.submitList(statuses);
                boolean empty = statuses.isEmpty();
                binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.recyclerBudgets.setVisibility(empty ? View.GONE : View.VISIBLE);

                binding.cardSummary.setVisibility(empty ? View.GONE : View.VISIBLE);
                if (!empty) {
                    renderSummary(statuses);
                }
            });
        });
    }

    /** Draws the summary on screen. */
    private void renderSummary(List<BudgetCalculator.BudgetStatus> statuses) {
        long budgetedMinor = 0L;
        long spentMinor = 0L;
        for (BudgetCalculator.BudgetStatus status : statuses) {
            budgetedMinor += status.getBudget().getLimitMinor();
            spentMinor += status.getSpentMinor();
        }

        Currency currency = CurrencyManager.getCurrency(requireContext());
        binding.textTotalBudgeted.setText(CurrencyFormatter.formatDisplay(budgetedMinor, currency));
        binding.textTotalSpent.setText(CurrencyFormatter.formatDisplay(spentMinor, currency));

        int percent = budgetedMinor <= 0
                ? 0
                : (int) Math.min(999L, (spentMinor * 100L) / budgetedMinor);
        binding.textOverallPercent.setText(getString(R.string.budgets_percent, percent));
        binding.progressOverall.setProgress(Math.min(100, percent));

        @ColorRes int colour;
        if (spentMinor >= budgetedMinor) {
            colour = R.color.status_exceeded;
        } else if (percent >= WARNING_THRESHOLD_PERCENT) {
            colour = R.color.status_warning;
        } else {
            colour = R.color.status_on_track;
        }
        int resolved = ContextCompat.getColor(requireContext(), colour);
        binding.textOverallPercent.setTextColor(resolved);
        binding.textTotalSpent.setTextColor(resolved);
        binding.progressOverall.setIndicatorColor(resolved);
        binding.progressOverall.setTrackColor(
                ColorStateList.valueOf(resolved).withAlpha(38).getDefaultColor());

        if (spentMinor > budgetedMinor) {
            binding.textOverallRemaining.setText(getString(R.string.budgets_over_by,
                    CurrencyFormatter.formatDisplay(spentMinor - budgetedMinor, currency)));
        } else {
            binding.textOverallRemaining.setText(getString(R.string.budgets_remaining,
                    CurrencyFormatter.formatDisplay(budgetedMinor - spentMinor, currency)));
        }
    }

    /** Shows the budget actions. */
    private void showBudgetActions(BudgetCalculator.BudgetStatus status) {
        if (status == null || !isAdded()) {
            return;
        }
        final Budget budget = status.getBudget();
        final Category category = budget.categoryAsEnum();

        Currency currency = CurrencyManager.getCurrency(requireContext());
        String body = getString(R.string.budgets_spent_of,
                CurrencyFormatter.formatDisplay(status.getSpentMinor(), currency),
                CurrencyFormatter.formatDisplay(budget.getLimitMinor(), currency));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.tx_budget_edit_title, category.getDisplayName()))
                .setMessage(body)
                .setNegativeButton(R.string.transactions_delete,
                        (dialog, which) -> confirmDeleteBudget(budget))
                .setNeutralButton(R.string.tx_action_edit,
                        (dialog, which) -> showBudgetDialog(budget))
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    /** Confirm delete budget. */
    private void confirmDeleteBudget(Budget budget) {
        Category category = budget.categoryAsEnum();
        final Context context = requireContext();
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.transactions_delete)
                .setMessage(getString(R.string.tx_budget_delete_confirm,
                        category.getDisplayName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.transactions_delete, (dialog, which) ->
                        new BudgetRepository(context).delete(budget, () -> mainHandler.post(() -> {
                            if (binding == null) {
                                return;
                            }
                            Toast.makeText(context, R.string.tx_budget_deleted,
                                    Toast.LENGTH_SHORT).show();
                            reload();
                        })))
                .show();
    }

    /** Shows the add budget dialog. */
    private void showAddBudgetDialog() {
        showBudgetDialog(null);
    }

    /** Shows the budget dialog. */
    private void showBudgetDialog(@Nullable Budget existing) {
        final Context context = requireContext();
        int gutter = getResources().getDimensionPixelSize(R.dimen.spacing_lg);
        int gap = getResources().getDimensionPixelSize(R.dimen.spacing_sm);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(gutter, gap, gutter, 0);

        TextView categoryLabel = new TextView(context);
        categoryLabel.setText(R.string.add_category);
        categoryLabel.setTextAppearance(R.style.TextAppearance_SpendWise_SectionLabel);
        content.addView(categoryLabel, wide(0));

        final List<Category> categories = Category.expenseCategories();
        final Category editingCategory = existing == null ? null : existing.categoryAsEnum();
        final Category[] chosen = {
                editingCategory == null ? categories.get(0) : editingCategory
        };

        ChipGroup chipGroup = new ChipGroup(context);
        chipGroup.setSingleSelection(true);
        chipGroup.setSelectionRequired(true);
        for (Category category : categories) {
            Chip chip = new Chip(context);
            chip.setText(category.getDisplayName());
            chip.setCheckable(true);
            chip.setTag(category);
            chip.setChecked(category == chosen[0]);

            chip.setEnabled(editingCategory == null);
            chip.setOnClickListener(v -> chosen[0] = (Category) v.getTag());
            chipGroup.addView(chip);
        }
        content.addView(chipGroup, wide(gap));

        TextView limitLabel = new TextView(context);
        limitLabel.setText(R.string.budgets_limit);
        limitLabel.setTextAppearance(R.style.TextAppearance_SpendWise_SectionLabel);
        content.addView(limitLabel, wide(gutter));

        Currency currency = CurrencyManager.getCurrency(context);

        final EditText limitField = new EditText(context);
        limitField.setHint(currency.getSymbol().trim() + " 250.00");
        limitField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        limitField.setSingleLine(true);
        limitField.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                getResources().getDimensionPixelSize(R.dimen.text_headline));
        limitField.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        limitField.setHintTextColor(ContextCompat.getColor(context, R.color.text_tertiary));
        if (existing != null) {
            long minorInCurrency = currency.minorFromLkr(existing.getLimitMinor());
            limitField.setText(String.format(Locale.UK, "%d.%02d",
                    minorInCurrency / 100, Math.abs(minorInCurrency % 100)));
            limitField.setSelection(limitField.getText().length());
        }
        content.addView(limitField, wide(gap));

        new MaterialAlertDialogBuilder(context)
                .setTitle(existing == null
                        ? getString(R.string.budgets_add)
                        : getString(R.string.budgets_edit))
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.add_save, (dialog, which) ->
                        saveBudget(chosen[0], limitField.getText().toString(), currency))
                .show();
    }

    private LinearLayout.LayoutParams wide(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        return params;
    }

    /** Writes the budget to storage. */
    private void saveBudget(Category category, String rawAmount, Currency currency) {
        final Context context = requireContext();
        try {
            long limitMinor = CurrencyFormatter.parseToMinorDisplay(rawAmount, currency);
            long userId = SessionManager.getInstance(context).getUserId();
            Budget budget = Budget.create(userId, category, limitMinor, YearMonth.now());

            new BudgetRepository(context).upsert(budget, () -> {
                mainHandler.post(() -> {
                    if (binding == null) {
                        return;
                    }
                    Toast.makeText(context, R.string.budgets_saved, Toast.LENGTH_SHORT).show();
                    reload();
                });
            });
        } catch (IllegalArgumentException e) {
            Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Called by the framework as the fragment's views are torn down. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
