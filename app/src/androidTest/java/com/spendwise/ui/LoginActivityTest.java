package com.spendwise.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;

import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;

import com.spendwise.R;
import com.spendwise.ui.auth.LoginActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Espresso tests driving the sign in screen through the widgets a user touches. */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class LoginActivityTest {

    private ActivityScenario<LoginActivity> scenario;

    /** Launch. */
    @Before
    public void launch() {
        scenario = ActivityScenario.launch(LoginActivity.class);
    }

    /** All sign in controls are visible on launch. */
    @Test
    public void allSignInControlsAreVisibleOnLaunch() {
        onView(withId(R.id.editEmail)).check(matches(isDisplayed()));
        onView(withId(R.id.editPassword)).check(matches(isDisplayed()));
        onView(withId(R.id.buttonSignIn)).check(matches(isDisplayed()));
        onView(withId(R.id.buttonGoogle)).check(matches(isDisplayed()));
        onView(withId(R.id.textForgotPassword)).check(matches(isDisplayed()));
        onView(withId(R.id.textSignUpPrompt)).check(matches(isDisplayed()));
    }

    /** Third party sign in button is present and labelled. */
    @Test
    public void thirdPartySignInButtonIsPresentAndLabelled() {
        // FR-04: the third-party option must be offered on the sign-in screen.
        onView(withId(R.id.buttonGoogle)).check(matches(withText(R.string.auth_google)));
    }

    /** Empty email_is rejected without leaving the screen. */
    @Test
    public void emptyEmail_isRejectedWithoutLeavingTheScreen() {
        onView(withId(R.id.buttonSignIn)).perform(click());
        // Still on the login screen, no navigation occurred.
        onView(withId(R.id.editEmail)).check(matches(isDisplayed()));
    }

    /** Malformed email_is rejected. */
    @Test
    public void malformedEmail_isRejected() {
        onView(withId(R.id.editEmail)).perform(typeText("not-an-email"), closeSoftKeyboard());
        onView(withId(R.id.editPassword)).perform(typeText("somepass1"), closeSoftKeyboard());
        onView(withId(R.id.buttonSignIn)).perform(click());

        onView(withId(R.id.editEmail)).check(matches(isDisplayed()));
    }

    /** Missing password_is rejected. */
    @Test
    public void missingPassword_isRejected() {
        onView(withId(R.id.editEmail)).perform(typeText("user@example.com"), closeSoftKeyboard());
        onView(withId(R.id.buttonSignIn)).perform(click());

        onView(withId(R.id.editPassword)).check(matches(isDisplayed()));
    }

    /** Unknown account_shows an error rather than signing in. */
    @Test
    public void unknownAccount_showsAnErrorRatherThanSigningIn() {
        onView(withId(R.id.editEmail))
                .perform(typeText("nobody-" + System.nanoTime() + "@example.com"),
                        closeSoftKeyboard());
        onView(withId(R.id.editPassword)).perform(typeText("whatever1"), closeSoftKeyboard());
        onView(withId(R.id.buttonSignIn)).perform(click());

        // The error banner becomes visible; the user stays on the login screen.
        onView(withId(R.id.textError)).check(matches(isDisplayed()));
    }

    /** Editing a field clears a previous error. */
    @Test
    public void editingAFieldClearsAPreviousError() {
        onView(withId(R.id.editEmail))
                .perform(typeText("nobody-" + System.nanoTime() + "@example.com"),
                        closeSoftKeyboard());
        onView(withId(R.id.editPassword)).perform(typeText("whatever1"), closeSoftKeyboard());
        onView(withId(R.id.buttonSignIn)).perform(click());
        onView(withId(R.id.textError)).check(matches(isDisplayed()));

        onView(withId(R.id.editPassword)).perform(clearText(), typeText("other1pass"),
                closeSoftKeyboard());
        onView(withId(R.id.textError))
                .check(matches(ViewMatchers.withEffectiveVisibility(
                        ViewMatchers.Visibility.GONE)));
    }

    /** Sign up link opens the registration screen. */
    @Test
    public void signUpLinkOpensTheRegistrationScreen() {
        onView(withId(R.id.textSignUpPrompt)).perform(click());
        onView(withId(R.id.editConfirm)).check(matches(isDisplayed()));
    }

    /** Forgot password link opens the recovery screen. */
    @Test
    public void forgotPasswordLinkOpensTheRecoveryScreen() {
        onView(withId(R.id.textForgotPassword)).perform(click());
        onView(withId(R.id.buttonReset)).check(matches(isDisplayed()));
    }

    /** Screen survives a rotation. */
    @Test
    public void screenSurvivesARotation() {
        onView(withId(R.id.editEmail)).perform(typeText("user@example.com"), closeSoftKeyboard());
        scenario.recreate();
        onView(withId(R.id.editEmail)).check(matches(isDisplayed()));
        onView(withId(R.id.buttonSignIn)).check(matches(isDisplayed()));
    }
}
