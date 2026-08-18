package com.spendwise.domain;

/** How an account signs in: with a password held locally, or through Google. */
public enum AuthProvider {
    LOCAL,

    GOOGLE,

    FIREBASE;

    /** From name or null. */
    public static AuthProvider fromNameOrNull(String name) {
        if (name == null) {
            return null;
        }
        for (AuthProvider p : values()) {
            if (p.name().equalsIgnoreCase(name.trim())) {
                return p;
            }
        }
        return null;
    }
}
