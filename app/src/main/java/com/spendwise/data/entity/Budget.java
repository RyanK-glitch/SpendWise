package com.spendwise.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.Guard;
import com.spendwise.util.CurrencyFormatter;

import java.time.LocalDate;
import java.time.YearMonth;

/**
 * A monthly spending limit for one category. The limit is stored in minor units, the
 * same as a transaction amount, so the two can be compared without conversion.
 */
@Entity(
        tableName = "budgets",
        foreignKeys = @ForeignKey(
                entity = User.class,
                parentColumns = "id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"user_id", "category", "year", "month"}, unique = true)
        }
)
public class Budget {
    public static final int DEFAULT_ALERT_THRESHOLD_PERCENT = 80;

    @PrimaryKey(autoGenerate = true)
    private long id;

    @ColumnInfo(name = "user_id")
    private long userId;

    @NonNull
    @ColumnInfo(name = "category")
    private String category = Category.GROCERIES.name();

    @ColumnInfo(name = "limit_minor")
    private long limitMinor;

    @ColumnInfo(name = "year")
    private int year;

    @ColumnInfo(name = "month")
    private int month;

    @ColumnInfo(name = "alert_threshold_percent")
    private int alertThresholdPercent = DEFAULT_ALERT_THRESHOLD_PERCENT;

    @ColumnInfo(name = "alert_sent_at")
    private long alertSentAt;

    public Budget() {
    }

    /** Builds the value. */
    public static Budget create(long userId, Category category, long limitMinor, YearMonth period) {
        Guard.require(userId > 0, "userId must be positive");
        Guard.notNull(category, "category");
        Guard.notNull(period, "period");
        Guard.require(limitMinor > 0, "limitMinor must be greater than zero");
        Guard.inRange(limitMinor, 1, CurrencyFormatter.MAX_AMOUNT_MINOR, "limitMinor");

        Budget budget = new Budget();
        budget.setUserId(userId);
        budget.setCategory(category.name());
        budget.setLimitMinor(limitMinor);
        budget.setYear(period.getYear());
        budget.setMonth(period.getMonthValue());
        return budget;
    }

    /** Category as enum. */
    public Category categoryAsEnum() {
        Category parsed = Category.fromNameOrNull(category);
        return parsed == null ? Category.GROCERIES : parsed;
    }

    /** Period. */
    public YearMonth period() {
        return YearMonth.of(year, month);
    }

    /** Covers. */
    public boolean covers(LocalDate date) {
        return date != null && date.getYear() == year && date.getMonthValue() == month;
    }

    /** Formats the ted limit for display. */
    public String formattedLimit() {
        return CurrencyFormatter.format(limitMinor);
    }

    /** Formats the ted limit for display. */
    public String formattedLimit(Currency currency) {
        return CurrencyFormatter.formatDisplay(limitMinor, currency);
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

    /** Returns the category. */
    @NonNull
    public String getCategory() {
        return category;
    }

    /** Sets the category. */
    public void setCategory(@NonNull String category) {
        this.category = category;
    }

    /** Returns the limit minor. */
    public long getLimitMinor() {
        return limitMinor;
    }

    /** Sets the limit minor. */
    public void setLimitMinor(long limitMinor) {
        this.limitMinor = limitMinor;
    }

    /** Returns the year. */
    public int getYear() {
        return year;
    }

    /** Sets the year. */
    public void setYear(int year) {
        this.year = year;
    }

    /** Returns the month. */
    public int getMonth() {
        return month;
    }

    /** Sets the month. */
    public void setMonth(int month) {
        this.month = month;
    }

    /** Returns the alert threshold percent. */
    public int getAlertThresholdPercent() {
        return alertThresholdPercent;
    }

    /** Sets the alert threshold percent. */
    public void setAlertThresholdPercent(int alertThresholdPercent) {
        this.alertThresholdPercent = alertThresholdPercent;
    }

    /** Returns the alert sent at. */
    public long getAlertSentAt() {
        return alertSentAt;
    }

    /** Sets the alert sent at. */
    public void setAlertSentAt(long alertSentAt) {
        this.alertSentAt = alertSentAt;
    }
}
