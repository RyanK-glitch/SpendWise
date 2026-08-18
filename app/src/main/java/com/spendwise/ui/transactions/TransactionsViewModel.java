package com.spendwise.ui.transactions;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.spendwise.data.SessionManager;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.repository.TransactionRepository;
import com.spendwise.domain.Category;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionFilter;
import com.spendwise.domain.TransactionType;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Holds the current filter and the list it produces. switchMap turns each new filter
 * into a new query while the screen keeps watching one LiveData, so two queries can
 * never both be feeding the list at once.
 */
public class TransactionsViewModel extends AndroidViewModel {
    private final TransactionRepository repository;
    private final long userId;

    private final MutableLiveData<TransactionFilter> filter =
            new MutableLiveData<>(TransactionFilter.empty());

    private final LiveData<List<Transaction>> filteredTransactions;
    private final LiveData<List<Transaction>> allTransactions;

    public TransactionsViewModel(@NonNull Application application) {
        super(application);
        this.repository = new TransactionRepository(application);
        this.userId = SessionManager.getInstance(application).getUserId();
        this.allTransactions = repository.observeAll(userId);
        // Each new filter becomes a new query and the old one is dropped. The screen
        // subscribes once, so two queries can never both feed the list.
        this.filteredTransactions = Transformations.switchMap(filter,
                f -> repository.observeFiltered(userId, f));
    }

    /** Returns the filtered transactions. */
    public LiveData<List<Transaction>> getFilteredTransactions() {
        return filteredTransactions;
    }

    /** Returns the all transactions. */
    public LiveData<List<Transaction>> getAllTransactions() {
        return allTransactions;
    }

    /** Returns the filter. */
    public LiveData<TransactionFilter> getFilter() {
        return filter;
    }

    /** Current filter. */
    public TransactionFilter currentFilter() {
        TransactionFilter current = filter.getValue();
        return current == null ? TransactionFilter.empty() : current;
    }

    /** Sets the query. */
    public void setQuery(String query) {
        TransactionFilter current = currentFilter();
        String normalised = query == null ? "" : query.trim();
        if (normalised.equals(current.getQuery())) {
            return;
        }
        filter.setValue(current.toBuilder().query(normalised).build());
    }

    /** Applies the filters. */
    public void applyFilters(Set<Category> categories,
                             Set<PaymentMethod> methods,
                             TransactionType type,
                             LocalDate from,
                             LocalDate to,
                             long minAmountMinor,
                             long maxAmountMinor) {
        TransactionFilter updated = TransactionFilter.builder()
                .query(currentFilter().getQuery())
                .categories(categories)
                .paymentMethods(methods)
                .type(type)
                .dateRange(from, to)
                .amountRange(minAmountMinor, maxAmountMinor)
                .build();
        filter.setValue(updated);
    }

    /** Clear filters. */
    public void clearFilters() {
        filter.setValue(TransactionFilter.empty());
    }

    /** Removes the value. */
    public void delete(Transaction transaction, Runnable onComplete) {
        repository.delete(transaction, onComplete);
    }

    /** Writes the value to storage. */
    public void insert(Transaction transaction, Runnable onComplete) {
        repository.insert(transaction, onComplete);
    }
}
