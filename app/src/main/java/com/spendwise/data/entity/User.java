package com.spendwise.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.spendwise.domain.AuthProvider;
import com.spendwise.domain.Guard;

import java.util.Locale;

/**
 * A user account. The password is stored only as a PBKDF2 hash, never in any form it
 * can be read back from.
 */
@Entity(
        tableName = "users",
        indices = {@Index(value = "email", unique = true)}
)
public class User {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @NonNull
    @ColumnInfo(name = "email")
    private String email = "";

    @NonNull
    @ColumnInfo(name = "display_name")
    private String displayName = "";

    @Nullable
    @ColumnInfo(name = "password_hash")
    private String passwordHash;

    @NonNull
    @ColumnInfo(name = "provider")
    private String provider = AuthProvider.LOCAL.name();

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public User() {
    }

    /** Builds the value. */
    public static User create(String email, String displayName,
                              @Nullable String passwordHash, AuthProvider provider) {
        Guard.notBlank(email, "email");
        Guard.notBlank(displayName, "displayName");
        Guard.notNull(provider, "provider");

        User user = new User();
        user.setEmail(normaliseEmail(email));
        user.setDisplayName(displayName.trim());
        user.setPasswordHash(passwordHash);
        user.setProvider(provider.name());
        user.setCreatedAt(System.currentTimeMillis());
        return user;
    }

    /** Normalise email. */
    public static String normaliseEmail(String email) {
        return Guard.notBlank(email, "email").trim().toLowerCase(Locale.ROOT);
    }

    /** Returns the id. */
    public long getId() {
        return id;
    }

    /** Sets the id. */
    public void setId(long id) {
        this.id = id;
    }

    /** Returns the email. */
    @NonNull
    public String getEmail() {
        return email;
    }

    /** Sets the email. */
    public void setEmail(@NonNull String email) {
        this.email = email;
    }

    /** Returns the display name. */
    @NonNull
    public String getDisplayName() {
        return displayName;
    }

    /** Sets the display name. */
    public void setDisplayName(@NonNull String displayName) {
        this.displayName = displayName;
    }

    /** Returns the password hash. */
    @Nullable
    public String getPasswordHash() {
        return passwordHash;
    }

    /** Sets the password hash. */
    public void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Returns the provider. */
    @NonNull
    public String getProvider() {
        return provider;
    }

    /** Sets the provider. */
    public void setProvider(@NonNull String provider) {
        this.provider = provider;
    }

    /** Returns the created at. */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Sets the created at. */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** Provider as enum. */
    public AuthProvider providerAsEnum() {
        AuthProvider parsed = AuthProvider.fromNameOrNull(provider);
        return parsed == null ? AuthProvider.LOCAL : parsed;
    }

    @NonNull
    @Override
    public String toString() {
        return "User{id=" + id + ", email='" + email + "', provider='" + provider + "'}";
    }
}
