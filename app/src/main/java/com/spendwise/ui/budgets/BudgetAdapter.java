package com.spendwise.ui.budgets;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.databinding.ItemBudgetBinding;
import com.spendwise.domain.BudgetCalculator;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.ui.CategoryIcons;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.util.CurrencyFormatter;

/**
 * Draws one budget card. The state is written in words as well as shown in colour, so
 * it does not depend on colour vision.
 */
public class BudgetAdapter
        extends ListAdapter<BudgetCalculator.BudgetStatus, BudgetAdapter.BudgetViewHolder> {
    public interface OnBudgetClickListener {
        void onBudgetClick(BudgetCalculator.BudgetStatus status);
    }

    @Nullable
    private final OnBudgetClickListener clickListener;

    public BudgetAdapter() {
        this(null);
    }

    public BudgetAdapter(@Nullable OnBudgetClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<BudgetCalculator.BudgetStatus> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BudgetCalculator.BudgetStatus>() {
                @Override
                public boolean areItemsTheSame(@NonNull BudgetCalculator.BudgetStatus a,
                                               @NonNull BudgetCalculator.BudgetStatus b) {
                    return a.getBudget().getId() == b.getBudget().getId();
                }

                @SuppressLint("DiffUtilEquals")
                @Override
                public boolean areContentsTheSame(@NonNull BudgetCalculator.BudgetStatus a,
                                                  @NonNull BudgetCalculator.BudgetStatus b) {
                    return a.getSpentMinor() == b.getSpentMinor()
                            && a.getBudget().getLimitMinor() == b.getBudget().getLimitMinor()
                            && a.getState() == b.getState();
                }
            };

    /** Called by the framework to inflate one row's layout. */
    @NonNull
    @Override
    public BudgetViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new BudgetViewHolder(ItemBudgetBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    /** Called by the framework to fill one row with data. */
    @Override
    public void onBindViewHolder(@NonNull BudgetViewHolder holder, int position) {
        BudgetCalculator.BudgetStatus status = getItem(position);
        holder.bind(status);
        holder.itemView.setOnClickListener(
                clickListener == null ? null : v -> clickListener.onBudgetClick(status));
        holder.itemView.setClickable(clickListener != null);
    }

    static class BudgetViewHolder extends RecyclerView.ViewHolder {
        private final ItemBudgetBinding binding;

        BudgetViewHolder(ItemBudgetBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(BudgetCalculator.BudgetStatus status) {
            Context context = binding.getRoot().getContext();
            Currency currency = CurrencyManager.getCurrency(context);
            Category category = status.getBudget().categoryAsEnum();
            String categoryName = category.getDisplayName();

            binding.textCategory.setText(categoryName);
            binding.textPercent.setText(
                    context.getString(R.string.budgets_percent, status.getRawPercentUsed()));
            binding.progressBudget.setProgress(status.getPercentUsed());
            binding.textSpent.setText(context.getString(
                    R.string.budgets_spent_of,
                    CurrencyFormatter.formatDisplay(status.getSpentMinor(), currency),
                    CurrencyFormatter.formatDisplay(status.getBudget().getLimitMinor(), currency)));

            binding.progressBudget.setContentDescription(context.getString(
                    R.string.cd_budget_progress, status.getRawPercentUsed(), categoryName));

            binding.imageCategoryIcon.setImageResource(CategoryIcons.iconFor(category));
            GradientDrawable circle = (GradientDrawable) ContextCompat.getDrawable(
                    context, R.drawable.bg_category_circle);
            if (circle != null) {
                circle = (GradientDrawable) circle.mutate();
                circle.setColor(Color.parseColor(category.getColourHex()));
                binding.imageCategoryIcon.setBackground(circle);
            }

            @ColorRes int colour;
            @ColorRes int containerColour;
            @StringRes int label;
            switch (status.getState()) {
                case EXCEEDED:
                    colour = R.color.status_exceeded;
                    containerColour = R.color.status_expense_container;
                    label = R.string.budgets_state_exceeded;
                    break;
                case WARNING:
                    colour = R.color.status_warning;
                    containerColour = R.color.status_warning_container;
                    label = R.string.budgets_state_warning;
                    break;
                default:
                    colour = R.color.status_on_track;
                    containerColour = R.color.status_income_container;
                    label = R.string.budgets_state_on_track;
                    break;
            }

            int resolved = ContextCompat.getColor(context, colour);
            binding.textPercent.setTextColor(resolved);
            binding.textState.setTextColor(resolved);
            binding.textState.setText(label);

            binding.textState.setBackgroundTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(context, containerColour)));

            binding.textRemaining.setText(
                    status.getState() == BudgetCalculator.BudgetState.EXCEEDED
                            ? context.getString(R.string.budgets_over_by,
                                    CurrencyFormatter.formatDisplay(
                                            status.getOverspendMinor(), currency))
                            : context.getString(R.string.budgets_remaining,
                                    CurrencyFormatter.formatDisplay(
                                            status.getRemainingMinor(), currency)));

            binding.progressBudget.setIndicatorColor(resolved);
            binding.progressBudget.setTrackColor(ColorStateList.valueOf(resolved)
                    .withAlpha(40).getDefaultColor());
        }
    }
}
