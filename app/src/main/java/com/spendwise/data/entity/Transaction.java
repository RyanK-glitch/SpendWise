package com.spendwise.data.entity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.Guard;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionType;
import com.spendwise.util.CurrencyFormatter;

import java.time.LocalDate;

/**
 * One row of the ledger. The amount is a positive whole number of minor units and the
 * income or expense direction is held separately in the type column, which is what
 * makes the balance arithmetic exact and provable.
 */
@Entity(
        tableName = "transactions",
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index("user_id"),
                @Index("date"),
                @Index("category")
        }
)
public class Transaction {
    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "user_id")
    private long userId;

    @NonNull
    @ColumnInfo(name = "description")
    private String description = "";

    @Nullable
    @ColumnInfo(name = "note")
    private String note;

    @ColumnInfo(name = "amount_minor")
    private long amountMinor;

    @NonNull
    @ColumnInfo(name = "type")
    private String type = TransactionType.EXPENSE.name();

    @NonNull
    @ColumnInfo(name = "category")
    private String category = Category.OTHER_INCOME.name();

    @NonNull
    @ColumnInfo(name = "payment_method")
    private String paymentMethod = PaymentMethod.CARD.name();

    @NonNull
    @ColumnInfo(name = "date")
    private LocalDate date = LocalDate.now();

    @ColumnInfo(name = "created_at")
    private long createdAt;

    public Transaction() {
    }

    /**
     * The only way to build one. Every rule is checked before the object exists, so a
     * Transaction that is in memory is always valid.
     */
    public static Transaction create(long userId,
                                     String description,
                                     @Nullable String note,
                                     long amountMinor,
                                     TransactionType type,
                                     Category category,
                                     PaymentMethod paymentMethod,
                                     LocalDate date) {
        Guard.require(userId > 0, "userId must be positive");
        Guard.notBlank(description, "description");
        Guard.require(amountMinor > 0, "amountMinor must be greater than zero");
        Guard.inRange(amountMinor, 1, CurrencyFormatter.MAX_AMOUNT_MINOR, "amountMinor");
        Guard.notNull(type, "type");
        Guard.notNull(category, "category");
        Guard.notNull(paymentMethod, "paymentMethod");
        Guard.notNull(date, "date");

        Transaction t = new Transaction();
        t.setUserId(userId);
        t.setDescription(description.trim());
        t.setNote(note == null || note.trim().isEmpty() ? null : note.trim());
        t.setAmountMinor(amountMinor);
        t.setType(type.name());
        t.setCategory(category.name());
        t.setPaymentMethod(paymentMethod.name());
        t.setDate(date);
        t.setCreatedAt(System.currentTimeMillis());

        Guard.ensure(t.getAmountMinor() > 0, "constructed transaction must have a positive amount");
        return t;
    }

    /** Type as enum. */
    public TransactionType typeAsEnum() {
        TransactionType parsed = TransactionType.fromNameOrNull(type);
        return parsed == null ? TransactionType.EXPENSE : parsed;
    }

    /** Category as enum. */
    public Category categoryAsEnum() {
        Category parsed = Category.fromNameOrNull(category);
        return parsed == null ? Category.OTHER_INCOME : parsed;
    }

    /** Payment method as enum. */
    public PaymentMethod paymentMethodAsEnum() {
        PaymentMethod parsed = PaymentMethod.fromNameOrNull(paymentMethod);
        return parsed == null ? PaymentMethod.CARD : parsed;
    }

    /** True when income. */
    public boolean isIncome() {
        return typeAsEnum() == TransactionType.INCOME;
    }

    /** Signed amount minor. */
    public long signedAmountMinor() {
        return amountMinor * typeAsEnum().getSign();
    }

    /** Formats the ted amount for display. */
    public String formattedAmount() {
        return CurrencyFormatter.formatSigned(amountMinor, isIncome());
    }

    /** Formats the ted amount for display. */
    public String formattedAmount(Currency currency) {
        return CurrencyFormatter.formatSignedDisplay(amountMinor, isIncome(), currency);
    }

    /** Returns the id. */
    public long getId() {
        return id;
    }

    /** Sets the id. */
    public void setId(long id) {
        this.id = id;
    }

    /** Returns the user id. */
    public long getUserId() {
        return userId;
    }

    /** Sets the user id. */
    public void setUserId(long userId) {
        this.userId = userId;
    }

    /** Returns the description. */
    @NonNull
    public String getDescription() {
        return description;
    }

    /** Sets the description. */
    public void setDescription(@NonNull String description) {
        this.description = description;
    }

    /** Returns the note. */
    @Nullable
    public String getNote() {
        return note;
    }

    /** Sets the note. */
    public void setNote(@Nullable String note) {
        this.note = note;
    }

    /** Returns the amount minor. */
    public long getAmountMinor() {
        return amountMinor;
    }

    /** Sets the amount minor. */
    public void setAmountMinor(long amountMinor) {
        this.amountMinor = amountMinor;
    }

    /** Returns the type. */
    @NonNull
    public String getType() {
        return type;
    }

    /** Sets the type. */
    public void setType(@NonNull String type) {
        this.type = type;
    }

    /** Returns the category. */
    @NonNull
    public String getCategory() {
        return category;
    }

    /** Sets the category. */
    public void setCategory(@NonNull String category) {
        this.category = category;
    }

    /** Returns the payment method. */
    @NonNull
    public String getPaymentMethod() {
        return paymentMethod;
    }

    /** Sets the payment method. */
    public void setPaymentMethod(@NonNull String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    /** Returns the date. */
    @NonNull
    public LocalDate getDate() {
        return date;
    }

    /** Sets the date. */
    public void setDate(@NonNull LocalDate date) {
        this.date = date;
    }

    /** Returns the created at. */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Sets the created at. */
    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }
}
