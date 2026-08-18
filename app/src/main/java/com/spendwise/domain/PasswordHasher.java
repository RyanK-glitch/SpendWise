package com.spendwise.domain;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2 password hashing and verification. Per user salt, 120,000 iterations, a self
 * describing record so the cost can be raised later, constant time comparison, and
 * false rather than an exception when a record is malformed.
 */
public final class PasswordHasher {
    private static final String ALGORITHM_PREFERRED = "PBKDF2WithHmacSHA256";
    private static final String ALGORITHM_FALLBACK = "PBKDF2WithHmacSHA1";

    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final String FIELD_SEPARATOR = "\\$";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    /**
     * Derives a PBKDF2 hash with a fresh random salt, and records the parameters alongside
     * it so the cost can be raised later without invalidating existing accounts.
     */
    public static String hash(String plainPassword) {
        Guard.notNull(plainPassword, "plainPassword");

        byte[] salt = new byte[SALT_LENGTH_BYTES];
        SECURE_RANDOM.nextBytes(salt);

        String algorithm = resolveAlgorithm();
        byte[] derived = derive(plainPassword, salt, ITERATIONS, algorithm);

        return algorithm + "$" + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(derived);
    }

    /**
     * Fails closed. A record that is malformed in any way returns false rather than
     * throwing, because a corrupted row must never be treated as a match.
     */
    public static boolean verify(String plainPassword, String storedRecord) {
        if (plainPassword == null || storedRecord == null) {
            return false;
        }

        String[] parts = storedRecord.split(FIELD_SEPARATOR);
        if (parts.length != 4) {
            return false;
        }

        try {
            String algorithm = parts[0];
            int iterations = Integer.parseInt(parts[1]);
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);

            if (iterations <= 0 || salt.length == 0 || expected.length == 0) {
                return false;
            }

            byte[] actual = derive(plainPassword, salt, iterations, algorithm);
            return constantTimeEquals(expected, actual);
        } catch (IllegalArgumentException | IllegalStateException e) {
            return false;
        }
    }

    /**
     * Compares every byte whatever happens, so the time taken does not reveal how many
     * leading bytes were correct.
     */
    static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.length != b.length) {
            return false;
        }
        int difference = 0;
        for (int i = 0; i < a.length; i++) {
            difference |= a[i] ^ b[i];
        }
        return difference == 0;
    }

    /** Resolve algorithm. */
    private static String resolveAlgorithm() {
        try {
            SecretKeyFactory.getInstance(ALGORITHM_PREFERRED);
            return ALGORITHM_PREFERRED;
        } catch (NoSuchAlgorithmException e) {
            return ALGORITHM_FALLBACK;
        }
    }

    /** Derive. */
    private static byte[] derive(String password, byte[] salt, int iterations, String algorithm) {
        PBEKeySpec spec = null;
        try {
            spec = new PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS);
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unsupported hash algorithm: " + algorithm, e);
        } catch (InvalidKeySpecException e) {
            throw new IllegalStateException("Unable to derive password hash", e);
        } finally {
            if (spec != null) {
                spec.clearPassword();
            }
        }
    }

    /** Derive for test. */
    static byte[] deriveForTest(String password, byte[] salt, int iterations) {
        return derive(password, salt, iterations, resolveAlgorithm());
    }

    /** Wipe. */
    public static void wipe(char[] buffer) {
        if (buffer != null) {
            java.util.Arrays.fill(buffer, '\0');
        }
    }

    /** Utf8. */
    static byte[] utf8(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
