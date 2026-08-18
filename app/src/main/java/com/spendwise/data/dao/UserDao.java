package com.spendwise.data.dao;

import androidx.annotation.Nullable;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.spendwise.data.entity.User;

/**
 * Database access for user accounts. Email is unique, enforced by an index rather
 * than by a check in application code.
 */
@Dao
public interface UserDao {
    /** Creates an account row and returns its new id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(User user);

    /** Updates an account row. */
    @Update
    int update(User user);

    /** Deletes an account. The foreign key cascade removes their ledger with it. */
    @Delete
    int delete(User user);

    /** Looks an account up by email address, or null when there is none. */
    @Nullable
    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    User findByEmail(String email);

    /** Looks an account up by row id. */
    @Nullable
    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    User findById(long id);

    /**
     * Used to reject a duplicate sign up. The unique index is the real guarantee, this is
     * only the friendly check that happens first.
     */
    @Query("SELECT COUNT(*) FROM users WHERE email = :email")
    int countByEmail(String email);

    /** How many accounts exist on this device. */
    @Query("SELECT COUNT(*) FROM users")
    int count();

    /**
     * Replaces the stored hash, which is how a password reset made through Firebase becomes
     * usable offline on the next sign in.
     */
    @Query("UPDATE users SET password_hash = :passwordHash WHERE id = :userId")
    int updatePasswordHash(long userId, String passwordHash);
}
