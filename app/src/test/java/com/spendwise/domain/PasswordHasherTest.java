package com.spendwise.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Tests salt uniqueness, that the plaintext never appears in the stored record, constant
 * time comparison, and that seven shapes of malformed record all fail closed.
 */
public class PasswordHasherTest {

    private static final String PASSWORD = "correct horse battery staple 1";

    /** Verify_accepts the correct password. */
    @Test
    public void verify_acceptsTheCorrectPassword() {
        assertTrue(PasswordHasher.verify(PASSWORD, PasswordHasher.hash(PASSWORD)));
    }

    /** Verify_rejects the wrong password. */
    @Test
    public void verify_rejectsTheWrongPassword() {
        assertFalse(PasswordHasher.verify("wrong password 1", PasswordHasher.hash(PASSWORD)));
    }

    /** Verify_is case sensitive. */
    @Test
    public void verify_isCaseSensitive() {
        assertFalse(PasswordHasher.verify(PASSWORD.toUpperCase(), PasswordHasher.hash(PASSWORD)));
    }

    /** Verify_rejects a password differing by one character. */
    @Test
    public void verify_rejectsAPasswordDifferingByOneCharacter() {
        assertFalse(PasswordHasher.verify(PASSWORD + "x", PasswordHasher.hash(PASSWORD)));
    }

    /** True when there is h_never contains the plaintext. */
    @Test
    public void hash_neverContainsThePlaintext() {
        String record = PasswordHasher.hash(PASSWORD);
        assertFalse("the stored record leaks the plaintext password",
                record.contains(PASSWORD));
    }

    /** True when there is h_produces a different record each time because the salt is random. */
    @Test
    public void hash_producesADifferentRecordEachTimeBecauseTheSaltIsRandom() {
        assertNotEquals(PasswordHasher.hash(PASSWORD), PasswordHasher.hash(PASSWORD));
    }

    /** True when there is h_both records still verify despite differing salts. */
    @Test
    public void hash_bothRecordsStillVerifyDespiteDifferingSalts() {
        assertTrue(PasswordHasher.verify(PASSWORD, PasswordHasher.hash(PASSWORD)));
        assertTrue(PasswordHasher.verify(PASSWORD, PasswordHasher.hash(PASSWORD)));
    }

    /** True when there is h_uses the self describing four field format. */
    @Test
    public void hash_usesTheSelfDescribingFourFieldFormat() {
        String[] parts = PasswordHasher.hash(PASSWORD).split("\\$");
        assertEquals("expected algorithm$iterations$salt$hash", 4, parts.length);
        assertTrue("algorithm field should name PBKDF2", parts[0].startsWith("PBKDF2"));
        assertTrue("iteration count should be a large number",
                Integer.parseInt(parts[1]) >= 100_000);
    }

    /** True when there is h_rejects null input. */
    @Test
    public void hash_rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> PasswordHasher.hash(null));
    }

    // ---- Fail-closed behaviour on bad input ------------------------------

    /** Verify_returns false for null arguments rather than throwing. */
    @Test
    public void verify_returnsFalseForNullArgumentsRatherThanThrowing() {
        assertFalse(PasswordHasher.verify(null, PasswordHasher.hash(PASSWORD)));
        assertFalse(PasswordHasher.verify(PASSWORD, null));
        assertFalse(PasswordHasher.verify(null, null));
    }

    /** Verify_returns false for a malformed record. */
    @Test
    public void verify_returnsFalseForAMalformedRecord() {
        assertFalse(PasswordHasher.verify(PASSWORD, ""));
        assertFalse(PasswordHasher.verify(PASSWORD, "garbage"));
        assertFalse(PasswordHasher.verify(PASSWORD, "only$three$fields"));
        assertFalse(PasswordHasher.verify(PASSWORD, "a$b$c$d$e"));
        assertFalse(PasswordHasher.verify(PASSWORD, "PBKDF2WithHmacSHA256$notanumber$c2FsdA==$aGFzaA=="));
        assertFalse(PasswordHasher.verify(PASSWORD, "PBKDF2WithHmacSHA256$1000$!!!notbase64!!!$aGFzaA=="));
        assertFalse(PasswordHasher.verify(PASSWORD, "PBKDF2WithHmacSHA256$0$c2FsdA==$aGFzaA=="));
    }

    /** Verify_returns false when the stored hash is empty. */
    @Test
    public void verify_returnsFalseWhenTheStoredHashIsEmpty() {
        assertFalse(PasswordHasher.verify(PASSWORD, "PBKDF2WithHmacSHA256$120000$c2FsdA==$"));
    }

    // ---- Constant-time comparison ---------------------------------------

    /** Constant time equals_matches identical arrays. */
    @Test
    public void constantTimeEquals_matchesIdenticalArrays() {
        assertTrue(PasswordHasher.constantTimeEquals(
                new byte[]{1, 2, 3}, new byte[]{1, 2, 3}));
    }

    /** Constant time equals_rejects differing content. */
    @Test
    public void constantTimeEquals_rejectsDifferingContent() {
        assertFalse(PasswordHasher.constantTimeEquals(
                new byte[]{1, 2, 3}, new byte[]{1, 2, 4}));
    }

    /** Constant time equals_rejects differing lengths. */
    @Test
    public void constantTimeEquals_rejectsDifferingLengths() {
        assertFalse(PasswordHasher.constantTimeEquals(
                new byte[]{1, 2, 3}, new byte[]{1, 2}));
    }

    /** Constant time equals_handles nulls and empty arrays. */
    @Test
    public void constantTimeEquals_handlesNullsAndEmptyArrays() {
        assertFalse(PasswordHasher.constantTimeEquals(null, new byte[]{1}));
        assertFalse(PasswordHasher.constantTimeEquals(new byte[]{1}, null));
        assertFalse(PasswordHasher.constantTimeEquals(null, null));
        assertTrue(PasswordHasher.constantTimeEquals(new byte[0], new byte[0]));
    }

    /** Constant time equals_detects a mismatch in the final byte. */
    @Test
    public void constantTimeEquals_detectsAMismatchInTheFinalByte() {
        byte[] a = new byte[64];
        byte[] b = new byte[64];
        b[63] = 1;
        assertFalse(PasswordHasher.constantTimeEquals(a, b));
    }

    // ---- Determinism and edge-case inputs --------------------------------

    /** Derive_is deterministic for a fixed salt. */
    @Test
    public void derive_isDeterministicForAFixedSalt() {
        byte[] salt = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] first = PasswordHasher.deriveForTest(PASSWORD, salt, 1_000);
        byte[] second = PasswordHasher.deriveForTest(PASSWORD, salt, 1_000);
        assertTrue(PasswordHasher.constantTimeEquals(first, second));
    }

    /** Derive_different iteration counts produce different keys. */
    @Test
    public void derive_differentIterationCountsProduceDifferentKeys() {
        byte[] salt = "0123456789abcdef".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertFalse(PasswordHasher.constantTimeEquals(
                PasswordHasher.deriveForTest(PASSWORD, salt, 1_000),
                PasswordHasher.deriveForTest(PASSWORD, salt, 2_000)));
    }

    /** True when there is h_handles unicode and very long passwords. */
    @Test
    public void hash_handlesUnicodeAndVeryLongPasswords() {
        String unicode = "påsswörd123日本語";
        assertTrue(PasswordHasher.verify(unicode, PasswordHasher.hash(unicode)));

        StringBuilder longPassword = new StringBuilder();
        for (int i = 0; i < 128; i++) {
            longPassword.append('a');
        }
        assertTrue(PasswordHasher.verify(longPassword.toString(),
                PasswordHasher.hash(longPassword.toString())));
    }

    /** Wipe_clears the buffer. */
    @Test
    public void wipe_clearsTheBuffer() {
        char[] buffer = {'s', 'e', 'c', 'r', 'e', 't'};
        PasswordHasher.wipe(buffer);
        for (char c : buffer) {
            assertEquals('\0', c);
        }
    }

    /** Wipe_tolerates null. */
    @Test
    public void wipe_toleratesNull() {
        PasswordHasher.wipe(null);   // must not throw
    }
}
