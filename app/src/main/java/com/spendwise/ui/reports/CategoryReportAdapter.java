package com.spendwise.ui.reports;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.data.entity.Transaction;
import com.spendwise.databinding.ItemCategoryReportBinding;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Category;
import com.spendwise.domain.TransactionType;
import com.spendwise.ui.CategoryIcons;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.util.CurrencyFormatter;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draws one row of the report. The bar is scaled against the largest category of the
 * month so the relative sizes stay readable.
 */
public class CategoryReportAdapter
        extends RecyclerView.Adapter<CategoryReportAdapter.CategoryViewHolder> {
    public static final class CategoryTotal {
        final Category category;
        final long amountMinor;
        final int percentOfTotal;
        final int percentOfLargest;

        final int transactionCount;

        CategoryTotal(Category category, long amountMinor,
                      int percentOfTotal, int percentOfLargest, int transactionCount) {
            this.category = category;
            this.amountMinor = amountMinor;
            this.percentOfTotal = percentOfTotal;
            this.percentOfLargest = percentOfLargest;
            this.transactionCount = transactionCount;
        }
    }

    private final List<CategoryTotal> items = new ArrayList<>();

    /** Hands the adapter a new list to diff against the one on screen. */
    public void submit(List<CategoryTotal> totals) {
        items.clear();
        if (totals != null) {
            items.addAll(totals);
        }
        notifyDataSetChanged();
    }

    /** Builds the rows. */
    public static List<CategoryTotal> buildRows(Map<Category, Long> spendByCategory) {
        return buildRows(spendByCategory, null);
    }

    /** Builds the rows. */
    public static List<CategoryTotal> buildRows(List<Transaction> ledger) {
        Map<Category, Integer> counts = new EnumMap<>(Category.class);
        if (ledger != null) {
            for (Transaction t : ledger) {
                if (t == null || t.typeAsEnum() != TransactionType.EXPENSE) {
                    continue;
                }
                Category category = t.categoryAsEnum();
                Integer current = counts.get(category);
                counts.put(category, current == null ? 1 : current + 1);
            }
        }
        return buildRows(BudgetCalculator.spendByCategory(ledger), counts);
    }

    /** Builds the rows. */
    private static List<CategoryTotal> buildRows(Map<Category, Long> spendByCategory,
                                                 Map<Category, Integer> counts) {
        List<CategoryTotal> rows = new ArrayList<>();
        if (spendByCategory == null || spendByCategory.isEmpty()) {
            return rows;
        }

        long total = 0L;
        long largest = 0L;
        for (Long value : spendByCategory.values()) {
            total += value;
            largest = Math.max(largest, value);
        }
        if (total == 0L) {
            return rows;
        }

        List<Map.Entry<Category, Long>> entries =
                new ArrayList<>(spendByCategory.entrySet());
        entries.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        for (Map.Entry<Category, Long> entry : entries) {
            long amount = entry.getValue();

            int share = (int) ((amount * 100L) / total);
            int relative = largest == 0 ? 0 : (int) ((amount * 100L) / largest);
            Integer count = counts == null ? null : counts.get(entry.getKey());
            rows.add(new CategoryTotal(entry.getKey(), amount, share, relative,
                    count == null ? 0 : count));
        }
        return rows;
    }

    /** Called by the framework to inflate one row's layout. */
    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new CategoryViewHolder(ItemCategoryReportBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    /** Called by the framework to fill one row with data. */
    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    /** Returns the item count. */
    @Override
    public int getItemCount() {
        return items.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        private final ItemCategoryReportBinding binding;

        CategoryViewHolder(ItemCategoryReportBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(CategoryTotal row) {
            Context context = binding.getRoot().getContext();

            binding.textCategory.setText(row.category.getDisplayName());
            binding.textAmount.setText(CurrencyFormatter.formatDisplay(
                    row.amountMinor, CurrencyManager.getCurrency(context)));

            if (row.transactionCount > 0) {
                binding.textCount.setText(context.getString(
                        R.string.reports_transactions_count, row.transactionCount));
                binding.textShare.setText(
                        String.format(Locale.UK, "%d%%", row.percentOfTotal));
                binding.textShare.setVisibility(View.VISIBLE);
            } else {
                binding.textCount.setText(context.getString(
                        R.string.reports_share_of_spend, row.percentOfTotal));
                binding.textShare.setVisibility(View.GONE);
            }

            binding.progressShare.setProgress(row.percentOfLargest);
            binding.progressShare.setIndicatorColor(
                    ContextCompat.getColor(context, CategoryIcons.colourFor(row.category)));
            binding.progressShare.setContentDescription(context.getString(
                    R.string.reports_share_of_spend, row.percentOfTotal));

            binding.viewColourKey.setImageResource(CategoryIcons.iconFor(row.category));

            GradientDrawable key = (GradientDrawable) ContextCompat.getDrawable(
                    context, R.drawable.bg_category_circle);
            if (key != null) {
                key = (GradientDrawable) key.mutate();
                key.setColor(Color.parseColor(row.category.getColourHex()));
                binding.viewColourKey.setBackground(key);
            }
        }
    }
}
