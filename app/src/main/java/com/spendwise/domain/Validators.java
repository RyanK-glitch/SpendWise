package com.spendwise.domain;

import java.util.regex.Pattern;

/**
 * Field validation for email, password, name, description and amount. Every screen
 * calls these rather than writing its own checks.
 */
public final class Validators {
    public static final int MIN_PASSWORD_LENGTH = 8;
    public static final int MAX_PASSWORD_LENGTH = 128;
    public static final int MAX_DESCRIPTION_LENGTH = 120;

    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private static final Pattern HAS_DIGIT = Pattern.compile(".*\\d.*");
    private static final Pattern HAS_LETTER = Pattern.compile(".*[A-Za-z].*");

    private Validators() {
    }

    public static final class Result {
        private final boolean valid;
        private final String message;

        private Result(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }

        /** Valid. */
        public static Result valid() {
            return new Result(true, null);
        }

        /** Invalid. */
        public static Result invalid(String message) {
            return new Result(false, Guard.notBlank(message, "message"));
        }

        /** True when valid. */
        public boolean isValid() {
            return valid;
        }

        /** Returns the message. */
        public String getMessage() {
            return message;
        }
    }

    /** Checks the email and reports what is wrong. */
    public static Result validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return Result.invalid("Email is required");
        }
        String trimmed = email.trim();
        if (trimmed.length() > 254) {
            return Result.invalid("Email is too long");
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            return Result.invalid("Enter a valid email address");
        }
        return Result.valid();
    }

    /** Checks the password and reports what is wrong. */
    public static Result validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return Result.invalid("Password is required");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return Result.invalid(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            return Result.invalid(
                    "Password must be " + MAX_PASSWORD_LENGTH + " characters or fewer");
        }
        if (!HAS_LETTER.matcher(password).matches()) {
            return Result.invalid("Password must contain at least one letter");
        }
        if (!HAS_DIGIT.matcher(password).matches()) {
            return Result.invalid("Password must contain at least one number");
        }
        return Result.valid();
    }

    /** Checks the password confirmation and reports what is wrong. */
    public static Result validatePasswordConfirmation(String password, String confirmation) {
        if (confirmation == null || confirmation.isEmpty()) {
            return Result.invalid("Please confirm your password");
        }
        if (!confirmation.equals(password)) {
            return Result.invalid("Passwords do not match");
        }
        return Result.valid();
    }

    /** Checks the display name and reports what is wrong. */
    public static Result validateDisplayName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return Result.invalid("Name is required");
        }
        if (name.trim().length() < 2) {
            return Result.invalid("Name is too short");
        }
        if (name.trim().length() > 60) {
            return Result.invalid("Name is too long");
        }
        return Result.valid();
    }

    /** Checks the description and reports what is wrong. */
    public static Result validateDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return Result.invalid("Description is required");
        }
        if (description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            return Result.invalid(
                    "Description must be " + MAX_DESCRIPTION_LENGTH + " characters or fewer");
        }
        return Result.valid();
    }

    /** Checks the amount and reports what is wrong. */
    public static Result validateAmount(String amountText) {
        try {
            com.spendwise.util.CurrencyFormatter.parseToMinor(amountText);
            return Result.valid();
        } catch (IllegalArgumentException e) {
            return Result.invalid(e.getMessage());
        }
    }

    /** Checks the amount and reports what is wrong. */
    public static Result validateAmount(String amountText, Currency currency) {
        try {
            com.spendwise.util.CurrencyFormatter.parseToMinorDisplay(amountText, currency);
            return Result.valid();
        } catch (IllegalArgumentException e) {
            return Result.invalid(e.getMessage());
        }
    }
}
