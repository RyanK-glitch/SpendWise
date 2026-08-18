package com.spendwise.domain;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Tests every validator across its equivalence classes and at both length boundaries. */
public class ValidatorsTest {

    // ---- Email ----------------------------------------------------------

    /** Email_accepts ordinary addresses. */
    @Test
    public void email_acceptsOrdinaryAddresses() {
        assertTrue(Validators.validateEmail("user@example.com").isValid());
        assertTrue(Validators.validateEmail("first.last@sub.domain.co.uk").isValid());
        assertTrue(Validators.validateEmail("user+tag@example.com").isValid());
        assertTrue(Validators.validateEmail("user_name-1@example.org").isValid());
    }

    /** Email_tolerates surrounding whitespace. */
    @Test
    public void email_toleratesSurroundingWhitespace() {
        assertTrue(Validators.validateEmail("  user@example.com  ").isValid());
    }

    /** Email_rejects null and blank. */
    @Test
    public void email_rejectsNullAndBlank() {
        assertFalse(Validators.validateEmail(null).isValid());
        assertFalse(Validators.validateEmail("").isValid());
        assertFalse(Validators.validateEmail("   ").isValid());
    }

    /** Email_rejects malformed addresses. */
    @Test
    public void email_rejectsMalformedAddresses() {
        assertFalse(Validators.validateEmail("no-at-sign.com").isValid());
        assertFalse(Validators.validateEmail("@example.com").isValid());
        assertFalse(Validators.validateEmail("user@").isValid());
        assertFalse(Validators.validateEmail("user@example").isValid());
        assertFalse(Validators.validateEmail("user @example.com").isValid());
        assertFalse(Validators.validateEmail("user@exam ple.com").isValid());
    }

    /** Email_rejects single character top level domain. */
    @Test
    public void email_rejectsSingleCharacterTopLevelDomain() {
        assertFalse(Validators.validateEmail("user@example.c").isValid());
    }

    /** Email_rejects excessive length. */
    @Test
    public void email_rejectsExcessiveLength() {
        StringBuilder local = new StringBuilder();
        for (int i = 0; i < 250; i++) {
            local.append('a');
        }
        assertFalse(Validators.validateEmail(local + "@example.com").isValid());
    }

    /** Email_failure carries a displayable message. */
    @Test
    public void email_failureCarriesADisplayableMessage() {
        Validators.Result result = Validators.validateEmail("nonsense");
        assertFalse(result.isValid());
        assertNotNull(result.getMessage());
        assertFalse(result.getMessage().isEmpty());
    }

    /** Valid result_carries no message. */
    @Test
    public void validResult_carriesNoMessage() {
        assertNull(Validators.validateEmail("user@example.com").getMessage());
    }

    // ---- Password -------------------------------------------------------

    /** Password_accepts a letter and digit mix at minimum length. */
    @Test
    public void password_acceptsALetterAndDigitMixAtMinimumLength() {
        assertTrue(Validators.validatePassword("passw0rd").isValid());
    }

    /** Password_rejects null and empty. */
    @Test
    public void password_rejectsNullAndEmpty() {
        assertFalse(Validators.validatePassword(null).isValid());
        assertFalse(Validators.validatePassword("").isValid());
    }

    /** Password_rejects one character below minimum but accepts minimum. */
    @Test
    public void password_rejectsOneCharacterBelowMinimumButAcceptsMinimum() {
        // Boundary pair: 7 rejected, 8 accepted.
        assertFalse(Validators.validatePassword("pass123").isValid());
        assertTrue(Validators.validatePassword("pass1234").isValid());
    }

    /** Password_rejects one character above maximum but accepts maximum. */
    @Test
    public void password_rejectsOneCharacterAboveMaximumButAcceptsMaximum() {
        StringBuilder atLimit = new StringBuilder("a1");
        while (atLimit.length() < Validators.MAX_PASSWORD_LENGTH) {
            atLimit.append('x');
        }
        assertTrue(Validators.validatePassword(atLimit.toString()).isValid());
        assertFalse(Validators.validatePassword(atLimit + "x").isValid());
    }

    /** Password_requires at least one digit. */
    @Test
    public void password_requiresAtLeastOneDigit() {
        assertFalse(Validators.validatePassword("onlyletters").isValid());
    }

    /** Password_requires at least one letter. */
    @Test
    public void password_requiresAtLeastOneLetter() {
        assertFalse(Validators.validatePassword("12345678").isValid());
    }

    /** Password_allows symbols and spaces. */
    @Test
    public void password_allowsSymbolsAndSpaces() {
        assertTrue(Validators.validatePassword("my pass 99!").isValid());
    }

    /** Password_is not trimmed. */
    @Test
    public void password_isNotTrimmed() {
        assertTrue(Validators.validatePassword(" pass123 ").isValid());
    }

    // ---- Password confirmation ------------------------------------------

    /** Confirmation_matches identical input. */
    @Test
    public void confirmation_matchesIdenticalInput() {
        assertTrue(Validators.validatePasswordConfirmation("passw0rd", "passw0rd").isValid());
    }

    /** Confirmation_rejects mismatch and empty input. */
    @Test
    public void confirmation_rejectsMismatchAndEmptyInput() {
        assertFalse(Validators.validatePasswordConfirmation("passw0rd", "different").isValid());
        assertFalse(Validators.validatePasswordConfirmation("passw0rd", "").isValid());
        assertFalse(Validators.validatePasswordConfirmation("passw0rd", null).isValid());
    }

    /** Confirmation_is case sensitive. */
    @Test
    public void confirmation_isCaseSensitive() {
        assertFalse(Validators.validatePasswordConfirmation("Passw0rd", "passw0rd").isValid());
    }

    // ---- Display name ---------------------------------------------------

    /** Display name_accepts ordinary names. */
    @Test
    public void displayName_acceptsOrdinaryNames() {
        assertTrue(Validators.validateDisplayName("Jo").isValid());
        assertTrue(Validators.validateDisplayName("Alex Morgan").isValid());
    }

    /** Display name_rejects blank and too short. */
    @Test
    public void displayName_rejectsBlankAndTooShort() {
        assertFalse(Validators.validateDisplayName(null).isValid());
        assertFalse(Validators.validateDisplayName("   ").isValid());
        assertFalse(Validators.validateDisplayName("A").isValid());
    }

    /** Display name_measures length after trimming. */
    @Test
    public void displayName_measuresLengthAfterTrimming() {
        // " A " trims to one character and must therefore be rejected.
        assertFalse(Validators.validateDisplayName(" A ").isValid());
    }

    // ---- Description ----------------------------------------------------

    /** Description_accepts normal merchant names. */
    @Test
    public void description_acceptsNormalMerchantNames() {
        assertTrue(Validators.validateDescription("Tesco Metro").isValid());
    }

    /** Description_rejects blank. */
    @Test
    public void description_rejectsBlank() {
        assertFalse(Validators.validateDescription(null).isValid());
        assertFalse(Validators.validateDescription("").isValid());
        assertFalse(Validators.validateDescription("    ").isValid());
    }

    /** Description_enforces the length ceiling at its boundary. */
    @Test
    public void description_enforcesTheLengthCeilingAtItsBoundary() {
        StringBuilder atLimit = new StringBuilder();
        for (int i = 0; i < Validators.MAX_DESCRIPTION_LENGTH; i++) {
            atLimit.append('x');
        }
        assertTrue(Validators.validateDescription(atLimit.toString()).isValid());
        assertFalse(Validators.validateDescription(atLimit + "x").isValid());
    }

    // ---- Amount (delegates to the currency parser) ----------------------

    /** Amount_accepts valid currency text. */
    @Test
    public void amount_acceptsValidCurrencyText() {
        assertTrue(Validators.validateAmount("12.50").isValid());
        assertTrue(Validators.validateAmount("LKR 1,234.56").isValid());
    }

    /** Amount_rejects invalid text and surfaces the parser message. */
    @Test
    public void amount_rejectsInvalidTextAndSurfacesTheParserMessage() {
        Validators.Result result = Validators.validateAmount("abc");
        assertFalse(result.isValid());
        assertEquals("Enter a valid amount, e.g. 12.50", result.getMessage());
    }

    /** Amount_rejects zero and negative. */
    @Test
    public void amount_rejectsZeroAndNegative() {
        assertFalse(Validators.validateAmount("0").isValid());
        assertFalse(Validators.validateAmount("-1").isValid());
    }
}
