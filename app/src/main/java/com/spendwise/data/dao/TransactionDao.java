package com.spendwise.data.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.spendwise.data.entity.Transaction;

import java.util.List;

/**
 * Database access for transactions, including the six clause filter query. Every
 * parameter is bound by Room rather than concatenated into the string, so a search
 * term can never change the shape of the SQL.
 */
@Dao
public interface TransactionDao {
    /** Inserts one transaction and returns its new row id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(Transaction transaction);

    /** Inserts many transactions in a single database transaction. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    List<Long> insertAll(List<Transaction> transactions);

    /** Updates one transaction in place and returns the number of rows changed. */
    @Update
    int update(Transaction transaction);

    /** Deletes one transaction and returns the number of rows removed. */
    @Delete
    int delete(Transaction transaction);

    /** Reads one transaction by its row id, or null when there is none. */
    @Nullable
    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    Transaction findById(long id);

    /** The user's whole ledger as LiveData, newest first, so the screen redraws itself. */
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date DESC, id DESC")
    LiveData<List<Transaction>> observeAll(long userId);

    /** The same ledger read once, for callers already on a worker thread. */
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date DESC, id DESC")
    List<Transaction> getAll(long userId);

    /** The newest few transactions, for the dashboard. */
    @Query("SELECT * FROM transactions WHERE user_id = :userId ORDER BY date DESC, id DESC LIMIT :limit")
    LiveData<List<Transaction>> observeRecent(long userId, int limit);

    /** How many transactions this user has. */
    @Query("SELECT COUNT(*) FROM transactions WHERE user_id = :userId")
    int countForUser(long userId);

    /** Clears the user's ledger, used when a remote copy replaces it. */
    @Query("DELETE FROM transactions WHERE user_id = :userId")
    int deleteAllForUser(long userId);

    /**
     * The six clause filter, run in SQL so a long ledger is never read into memory. Each
     * clause switches itself off when its criterion is unset, which is what makes an empty
     * filter return everything.
     */
    @Query("SELECT * FROM transactions "
            + "WHERE user_id = :userId "
            + "AND (:query = '' "
            + "     OR description LIKE '%' || :query || '%' ESCAPE '\\' "
            + "     OR IFNULL(note, '') LIKE '%' || :query || '%' ESCAPE '\\' "
            + "     OR REPLACE(category, '_', ' ') LIKE '%' || :query || '%' ESCAPE '\\' "
            + "     OR REPLACE(payment_method, '_', ' ') LIKE '%' || :query || '%' ESCAPE '\\') "
            + "AND (:categoryCount = 0 OR category IN (:categories)) "
            + "AND (:methodCount = 0 OR payment_method IN (:methods)) "
            + "AND (:type IS NULL OR type = :type) "
            + "AND (:fromDay IS NULL OR date >= :fromDay) "
            + "AND (:toDay IS NULL OR date <= :toDay) "
            + "AND amount_minor >= :minAmountMinor "
            + "AND amount_minor <= :maxAmountMinor "
            + "ORDER BY date DESC, id DESC")
    LiveData<List<Transaction>> filter(long userId,
                                       String query,
                                       List<String> categories,
                                       int categoryCount,
                                       List<String> methods,
                                       int methodCount,
                                       @Nullable String type,
                                       @Nullable Long fromDay,
                                       @Nullable Long toDay,
                                       long minAmountMinor,
                                       long maxAmountMinor);

    /**
     * The same query without LiveData, for the differential test that checks this SQL and
     * the Java predicate agree row for row.
     */
    @Query("SELECT * FROM transactions "
            + "WHERE user_id = :userId "
            + "AND (:query = '' "
            + "     OR description LIKE '%' || :query || '%' ESCAPE '\\' "
            + "     OR IFNULL(note, '') LIKE '%' || :query || '%' ESCAPE '\\' "
            + "     OR REPLACE(category, '_', ' ') LIKE '%' || :query || '%' ESCAPE '\\' "
            + "     OR REPLACE(payment_method, '_', ' ') LIKE '%' || :query || '%' ESCAPE '\\') "
            + "AND (:categoryCount = 0 OR category IN (:categories)) "
            + "AND (:methodCount = 0 OR payment_method IN (:methods)) "
            + "AND (:type IS NULL OR type = :type) "
            + "AND (:fromDay IS NULL OR date >= :fromDay) "
            + "AND (:toDay IS NULL OR date <= :toDay) "
            + "AND amount_minor >= :minAmountMinor "
            + "AND amount_minor <= :maxAmountMinor "
            + "ORDER BY date DESC, id DESC")
    List<Transaction> filterBlocking(long userId,
                                     String query,
                                     List<String> categories,
                                     int categoryCount,
                                     List<String> methods,
                                     int methodCount,
                                     @Nullable String type,
                                     @Nullable Long fromDay,
                                     @Nullable Long toDay,
                                     long minAmountMinor,
                                     long maxAmountMinor);

    /** Totals one category over a date range, which is what a budget is measured against. */
    @Query("SELECT IFNULL(SUM(amount_minor), 0) FROM transactions "
            + "WHERE user_id = :userId AND type = 'EXPENSE' AND category = :category "
            + "AND date >= :fromDay AND date <= :toDay")
    long sumExpenseForCategory(long userId, String category, long fromDay, long toDay);
}
