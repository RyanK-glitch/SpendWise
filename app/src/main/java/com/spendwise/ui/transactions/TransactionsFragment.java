package com.spendwise.ui.transactions;

import android.graphics.Canvas;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.spendwise.R;
import com.spendwise.data.entity.Transaction;
import com.spendwise.databinding.FragmentTransactionsBinding;
import com.spendwise.domain.TransactionFilter;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.SimpleTextWatcher;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The Activity tab. Search box, filter sheet and the ledger list, with a count of how
 * many entries matched out of the total.
 */
public class TransactionsFragment extends Fragment
        implements TransactionAdapter.OnTransactionClickListener {
    private static final long SEARCH_DEBOUNCE_MILLIS = 300L;

    private static final DateTimeFormatter DETAIL_DATE =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private FragmentTransactionsBinding binding;
    private TransactionsViewModel viewModel;
    private TransactionAdapter adapter;

    private final Handler debounceHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean syncingSearchField;

    private int totalCount;
    private int shownCount;
    private boolean filterActive;
    private boolean listLoaded;

    /** Called by the framework to inflate the fragment's layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTransactionsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** Called by the framework once the fragment's views exist. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(TransactionsViewModel.class);

        adapter = new TransactionAdapter(this);
        binding.recyclerTransactions.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.recyclerTransactions.setAdapter(adapter);
        binding.recyclerTransactions.setHasFixedSize(true);
        attachSwipeToDelete();

        wireSearch();
        observeData();

        binding.buttonFilter.setOnClickListener(v -> openFilterSheet());

        View.OnClickListener clearEverything = v -> {
            cancelPendingSearch();
            viewModel.clearFilters();
        };
        binding.textClearFilters.setOnClickListener(clearEverything);
        binding.buttonClearAllEmpty.setOnClickListener(clearEverything);

        binding.buttonClearSearch.setOnClickListener(v -> {
            cancelPendingSearch();
            viewModel.setQuery("");

            syncSearchField("");
            binding.editSearch.requestFocus();
        });
    }

    /** Returns the data as LiveData, so the screen updates itself. */
    private void observeData() {
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), all -> {
            totalCount = all == null ? 0 : all.size();
            renderCounts();
        });

        viewModel.getFilteredTransactions().observe(getViewLifecycleOwner(), transactions -> {
            shownCount = transactions == null ? 0 : transactions.size();
            listLoaded = true;
            adapter.submitList(transactions);
            renderCounts();
        });

        viewModel.getFilter().observe(getViewLifecycleOwner(), this::renderFilterState);
    }

    /** Draws the filter state on screen. */
    private void renderFilterState(@Nullable TransactionFilter filter) {
        TransactionFilter active = filter == null ? TransactionFilter.empty() : filter;

        int badge = active.activeCriteriaCount() - (active.getQuery().isEmpty() ? 0 : 1);
        binding.textFilterCount.setText(String.valueOf(badge));
        binding.textFilterCount.setVisibility(badge > 0 ? View.VISIBLE : View.GONE);

        boolean anythingToClear = !active.isEmpty();
        binding.textClearFilters.setVisibility(anythingToClear ? View.VISIBLE : View.GONE);
        binding.buttonClearAllEmpty.setVisibility(anythingToClear ? View.VISIBLE : View.GONE);

        filterActive = anythingToClear;
        syncSearchField(active.getQuery());
        renderCounts();
    }

    /** Draws the counts on screen. */
    private void renderCounts() {
        if (!listLoaded) {
            binding.layoutEmpty.setVisibility(View.GONE);
            return;
        }

        int total = Math.max(shownCount, totalCount);
        boolean ledgerEmpty = totalCount == 0 && !filterActive;

        binding.textResultCount.setText(
                getString(R.string.transactions_result_count, shownCount, total));
        binding.textResultCount.setVisibility(ledgerEmpty ? View.GONE : View.VISIBLE);

        binding.textEmptyTitle.setText(
                ledgerEmpty ? R.string.tx_empty_ledger : R.string.transactions_empty);
        binding.textEmptyHint.setText(
                ledgerEmpty ? R.string.tx_empty_ledger_hint : R.string.transactions_empty_hint);

        boolean empty = shownCount == 0;
        binding.layoutEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
        binding.recyclerTransactions.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    /** Wire search. */
    private void wireSearch() {
        binding.editSearch.addTextChangedListener(new SimpleTextWatcher(() -> {
            if (binding == null) {
                return;
            }
            final String query = binding.editSearch.getText() == null
                    ? "" : binding.editSearch.getText().toString();

            binding.buttonClearSearch.setVisibility(
                    query.isEmpty() ? View.GONE : View.VISIBLE);

            if (syncingSearchField) {
                return;
            }
            cancelPendingSearch();
            pendingSearch = () -> {
                pendingSearch = null;
                viewModel.setQuery(query);
            };
            debounceHandler.postDelayed(pendingSearch, SEARCH_DEBOUNCE_MILLIS);
        }));
    }

    /** Sync search field. */
    private void syncSearchField(String query) {
        CharSequence current = binding.editSearch.getText();
        String shown = current == null ? "" : current.toString();
        if (shown.trim().equals(query) || pendingSearch != null) {
            return;
        }
        syncingSearchField = true;
        binding.editSearch.setText(query);
        binding.editSearch.setSelection(query.length());
        syncingSearchField = false;
    }

    /** Cancel pending search. */
    private void cancelPendingSearch() {
        if (pendingSearch != null) {
            debounceHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
    }

    /** Open filter sheet. */
    private void openFilterSheet() {
        FilterBottomSheet sheet = FilterBottomSheet.newInstance();
        sheet.show(getChildFragmentManager(), FilterBottomSheet.TAG);
    }

    /** Called by the framework at the matching point in the lifecycle. */
    @Override
    public void onTransactionClick(Transaction transaction) {
        if (transaction == null || !isAdded()) {
            return;
        }
        StringBuilder details = new StringBuilder()
                .append(getString(R.string.tx_detail_amount, transaction.formattedAmount(
                        CurrencyManager.getCurrency(requireContext()))))
                .append('\n')
                .append(getString(R.string.tx_detail_type, getString(transaction.isIncome()
                        ? R.string.transactions_type_income
                        : R.string.transactions_type_expense)))
                .append('\n')
                .append(getString(R.string.tx_detail_category,
                        transaction.categoryAsEnum().getDisplayName()))
                .append('\n')
                .append(getString(R.string.tx_detail_payment,
                        transaction.paymentMethodAsEnum().getDisplayName()))
                .append('\n')
                .append(getString(R.string.tx_detail_date,
                        transaction.getDate().format(DETAIL_DATE)));

        String note = transaction.getNote();
        if (note != null && !note.trim().isEmpty()) {
            details.append('\n').append(getString(R.string.tx_detail_note, note.trim()));
        }
        details.append("\n\n").append(getString(R.string.tx_detail_swipe_hint));

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(transaction.getDescription())
                .setMessage(details.toString())
                .setNegativeButton(R.string.transactions_delete,
                        (dialog, which) -> deleteWithUndo(transaction))
                .setNeutralButton(R.string.tx_action_edit,
                        (dialog, which) -> startActivity(AddTransactionActivity.editIntent(
                                requireContext(), transaction.getId())))
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    /** Attach swipe to delete. */
    private void attachSwipeToDelete() {
        final ColorDrawable background = new ColorDrawable(
                ContextCompat.getColor(requireContext(), R.color.status_expense));
        Drawable raw = ContextCompat.getDrawable(requireContext(), R.drawable.ic_trash);
        final Drawable icon = raw == null ? null : raw.mutate();
        if (icon != null) {
            icon.setTint(ContextCompat.getColor(requireContext(), R.color.white));
        }
        final int iconMargin =
                getResources().getDimensionPixelSize(R.dimen.screen_horizontal_padding);

        ItemTouchHelper helper = new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(
                0, ItemTouchHelper.START | ItemTouchHelper.END) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder holder,
                                  @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction) {
                int position = holder.getBindingAdapterPosition();
                Transaction swiped = adapter.itemAt(position);
                if (swiped == null) {
                    return;
                }

                adapter.notifyItemChanged(position);
                deleteWithUndo(swiped);
            }

            @Override
            public void onChildDraw(@NonNull Canvas canvas, @NonNull RecyclerView recyclerView,
                                    @NonNull RecyclerView.ViewHolder holder, float dX, float dY,
                                    int actionState, boolean isCurrentlyActive) {
                View row = holder.itemView;
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                    background.setBounds(row.getLeft(), row.getTop(), row.getRight(),
                            row.getBottom());
                    background.draw(canvas);
                    if (icon != null) {
                        int size = icon.getIntrinsicHeight();
                        int top = row.getTop() + (row.getHeight() - size) / 2;
                        int left = dX > 0f
                                ? row.getLeft() + iconMargin
                                : row.getRight() - iconMargin - size;
                        icon.setBounds(left, top, left + size, top + size);
                        icon.draw(canvas);
                    }
                }
                super.onChildDraw(canvas, recyclerView, holder, dX, dY, actionState,
                        isCurrentlyActive);
            }
        });
        helper.attachToRecyclerView(binding.recyclerTransactions);
    }

    /** Removes the with undo. */
    private void deleteWithUndo(Transaction transaction) {
        viewModel.delete(transaction, () -> mainHandler.post(() -> {
            if (binding == null) {
                return;
            }
            Snackbar.make(binding.getRoot(), R.string.transactions_deleted, Snackbar.LENGTH_LONG)
                    .setAction(R.string.transactions_undo, v -> restore(transaction))
                    .show();
        }));
    }

    /** Restore. */
    private void restore(Transaction snapshot) {
        snapshot.setId(0L);
        viewModel.insert(snapshot, null);
    }

    /** Called by the framework as the fragment's views are torn down. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        cancelPendingSearch();
        binding = null;
    }
}
