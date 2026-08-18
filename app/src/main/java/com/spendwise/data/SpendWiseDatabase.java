package com.spendwise.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import com.spendwise.data.dao.BudgetDao;
import com.spendwise.data.dao.TransactionDao;
import com.spendwise.data.dao.UserDao;
import com.spendwise.data.entity.Budget;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.entity.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The Room database, and the thread pool every write goes through. Schema version 1,
 * three tables, and a volatile double-checked singleton so two threads cannot each
 * build a database over the same file.
 */
@Database(
        entities = {User.class, Transaction.class, Budget.class},
        version = 1,
        exportSchema = true
)
@TypeConverters({Converters.class})
public abstract class SpendWiseDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "spendwise.db";

    private static final int WRITE_THREADS = 4;
    // One shared pool for every database write in the app, so writes queue behind
    // each other instead of racing.
    public static final ExecutorService IO_EXECUTOR =
            Executors.newFixedThreadPool(WRITE_THREADS);

    private static volatile SpendWiseDatabase instance;

    /** User dao. */
    public abstract UserDao userDao();

    /** Transaction dao. */
    public abstract TransactionDao transactionDao();

    /** Budget dao. */
    public abstract BudgetDao budgetDao();

    /**
     * Double-checked locking. Two threads reaching this at once must not each build a
     * database over the same file, and the volatile field is what makes the second check
     * safe.
     */
    public static SpendWiseDatabase getInstance(Context context) {
        SpendWiseDatabase local = instance;
        if (local == null) {
            synchronized (SpendWiseDatabase.class) {
                local = instance;
                if (local == null) {
                    local = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    SpendWiseDatabase.class,
                                    DATABASE_NAME)
                            .build();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Sets the instance for testing. */
    public static void setInstanceForTesting(SpendWiseDatabase testInstance) {
        synchronized (SpendWiseDatabase.class) {
            instance = testInstance;
        }
    }
}
