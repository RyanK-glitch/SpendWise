package com.spendwise.data.dao;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.spendwise.data.entity.Budget;

import java.util.List;

/** Database access for budgets. One budget per user, category and month. */
@Dao
public interface BudgetDao {
    /** Inserts one budget and returns its new row id. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Budget budget);

    /** Inserts many budgets at once, used when restoring from the cloud copy. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<Budget> budgets);

    /** Updates one budget in place. */
    @Update
    int update(Budget budget);

    /** Deletes one budget. */
    @Delete
    int delete(Budget budget);

    /** The budgets for one month as LiveData. */
    @Query("SELECT * FROM budgets WHERE user_id = :userId AND year = :year AND month = :month "
            + "ORDER BY category ASC")
    LiveData<List<Budget>> observeForPeriod(long userId, int year, int month);

    /** The budgets for one month, read once on a worker thread. */
    @Query("SELECT * FROM budgets WHERE user_id = :userId AND year = :year AND month = :month "
            + "ORDER BY category ASC")
    List<Budget> getForPeriod(long userId, int year, int month);

    /** The single budget for a user, category and month, or null. */
    @Nullable
    @Query("SELECT * FROM budgets WHERE user_id = :userId AND category = :category "
            + "AND year = :year AND month = :month LIMIT 1")
    Budget findOne(long userId, String category, int year, int month);

    /**
     * Stamps the time an alert went out, which is what stops a repeating schedule from
     * announcing the same overspend again and again.
     */
    @Query("UPDATE budgets SET alert_sent_at = :timestamp WHERE id = :budgetId")
    int markAlertSent(long budgetId, long timestamp);

    /** How many budgets this user has set. */
    @Query("SELECT COUNT(*) FROM budgets WHERE user_id = :userId")
    int countForUser(long userId);
}
