package com.spendwise.data;

import androidx.room.TypeConverter;

import java.time.LocalDate;

/**
 * Room type converters. SQLite has no date type, so a LocalDate is stored as an
 * epoch day number and turned back into a LocalDate on the way out.
 */
public final class Converters {
    private Converters() {
    }

    /** From local date. */
    @TypeConverter
    public static Long fromLocalDate(LocalDate date) {
        return date == null ? null : date.toEpochDay();
    }

    /** To local date. */
    @TypeConverter
    public static LocalDate toLocalDate(Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay);
    }
}
