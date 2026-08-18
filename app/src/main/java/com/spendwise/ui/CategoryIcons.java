package com.spendwise.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;

import com.spendwise.R;
import com.spendwise.domain.Category;

/**
 * Maps a category to its icon and its colour, so every screen draws a category the
 * same way.
 */
public final class CategoryIcons {
    private CategoryIcons() {
    }

    /** Icon for. */
    @DrawableRes
    public static int iconFor(@Nullable Category category) {
        if (category == null) {
            return R.drawable.ic_wallet;
        }
        switch (category) {
            case GROCERIES:
                return R.drawable.ic_cat_groceries;
            case RENT:
                return R.drawable.ic_cat_rent;
            case UTILITIES:
                return R.drawable.ic_cat_utilities;
            case TRANSPORT:
                return R.drawable.ic_cat_transport;
            case DINING:
                return R.drawable.ic_cat_dining;
            case ENTERTAINMENT:
                return R.drawable.ic_cat_entertainment;
            case HEALTH:
                return R.drawable.ic_cat_health;
            case SHOPPING:
                return R.drawable.ic_cat_shopping;
            case EDUCATION:
                return R.drawable.ic_cat_education;
            case TRAVEL:
                return R.drawable.ic_cat_travel;
            case SALARY:
                return R.drawable.ic_cat_salary;
            case OTHER_INCOME:
                return R.drawable.ic_cat_other_income;
            default:
                return R.drawable.ic_wallet;
        }
    }

    /** Colour for. */
    @ColorRes
    public static int colourFor(@Nullable Category category) {
        if (category == null) {
            return R.color.brand_primary;
        }
        switch (category) {
            case GROCERIES:
                return R.color.category_groceries;
            case RENT:
                return R.color.category_rent;
            case UTILITIES:
                return R.color.category_utilities;
            case TRANSPORT:
                return R.color.category_transport;
            case DINING:
                return R.color.category_dining;
            case ENTERTAINMENT:
                return R.color.category_entertainment;
            case HEALTH:
                return R.color.category_health;
            case SHOPPING:
                return R.color.category_shopping;
            case EDUCATION:
                return R.color.category_education;
            case TRAVEL:
                return R.color.category_travel;
            case SALARY:
                return R.color.category_salary;
            case OTHER_INCOME:
                return R.color.category_other_income;
            default:
                return R.color.brand_primary;
        }
    }
}
