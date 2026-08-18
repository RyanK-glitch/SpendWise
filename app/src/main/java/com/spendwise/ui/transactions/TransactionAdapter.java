package com.spendwise.ui.transactions;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.data.entity.Transaction;
import com.spendwise.databinding.ItemTransactionBinding;
import com.spendwise.domain.Category;
import com.spendwise.ui.CategoryIcons;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Draws one ledger row. It uses ListAdapter, which diffs on a background thread, so
 * typing in the search box does not rebind every visible row.
 */
public class TransactionAdapter
        extends ListAdapter<Transaction, TransactionAdapter.TransactionViewHolder> {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM", Locale.UK);

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    private final OnTransactionClickListener clickListener;

    public TransactionAdapter(OnTransactionClickListener clickListener) {
        super(DIFF_CALLBACK);
        this.clickListener = clickListener;
    }

    private static final DiffUtil.ItemCallback<Transaction> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<Transaction>() {
                @Override
                public boolean areItemsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                    return a.getId() == b.getId();
                }

                @Override
                public boolean areContentsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                    return a.getAmountMinor() == b.getAmountMinor()
                            && a.getDescription().equals(b.getDescription())
                            && a.getCategory().equals(b.getCategory())
                            && a.getPaymentMethod().equals(b.getPaymentMethod())
                            && a.getType().equals(b.getType())
                            && a.getDate().equals(b.getDate())
                            && (a.getNote() == null
                                    ? b.getNote() == null
                                    : a.getNote().equals(b.getNote()));
                }
            };

    /** Item at. */
    @Nullable
    public Transaction itemAt(int position) {
        if (position < 0 || position >= getItemCount()) {
            return null;
        }
        return getItem(position);
    }

    /** Called by the framework to inflate one row's layout. */
    @NonNull
    @Override
    public TransactionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemTransactionBinding binding = ItemTransactionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new TransactionViewHolder(binding, clickListener);
    }

    /** Called by the framework to fill one row with data. */
    @Override
    public void onBindViewHolder(@NonNull TransactionViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static class TransactionViewHolder extends RecyclerView.ViewHolder {
        private final ItemTransactionBinding binding;
        private final OnTransactionClickListener clickListener;

        TransactionViewHolder(ItemTransactionBinding binding,
                              OnTransactionClickListener clickListener) {
            super(binding.getRoot());
            this.binding = binding;
            this.clickListener = clickListener;
        }

        void bind(Transaction transaction) {
            Category category = transaction.categoryAsEnum();

            binding.textDescription.setText(transaction.getDescription());

            binding.textCategoryInitial.setText(
                    category.getDisplayName().substring(0, 1).toUpperCase(Locale.UK));

            binding.imageCategoryIcon.setImageResource(CategoryIcons.iconFor(category));

            GradientDrawable circle = (GradientDrawable) ContextCompat.getDrawable(
                    binding.getRoot().getContext(), R.drawable.bg_category_circle);
            if (circle != null) {
                circle = (GradientDrawable) circle.mutate();
                circle.setColor(Color.parseColor(category.getColourHex()));
                binding.imageCategoryIcon.setBackground(circle);
            }

            binding.textMeta.setText(String.format(Locale.UK, "%s · %s · %s",
                    category.getDisplayName(),
                    transaction.paymentMethodAsEnum().getDisplayName(),
                    transaction.getDate().format(DATE_FORMAT)));

            String note = transaction.getNote();
            if (note == null || note.isEmpty()) {
                binding.textNote.setVisibility(View.GONE);
            } else {
                binding.textNote.setText(note);
                binding.textNote.setVisibility(View.VISIBLE);
            }

            binding.textAmount.setText(transaction.formattedAmount(
                    com.spendwise.ui.CurrencyManager.getCurrency(binding.getRoot().getContext())));
            binding.textAmount.setTextColor(ContextCompat.getColor(
                    binding.getRoot().getContext(),
                    transaction.isIncome() ? R.color.status_income : R.color.status_expense));

            binding.getRoot().setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onTransactionClick(transaction);
                }
            });
        }
    }
}
