package com.spendwise.ui.auth;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.repository.AuthRepository;
import com.spendwise.data.repository.BudgetRepository;
import com.spendwise.data.repository.TransactionRepository;
import com.spendwise.databinding.ActivityLoginBinding;
import com.spendwise.domain.AuthResult;
import com.spendwise.domain.Validators;
import com.spendwise.notification.NotificationHelper;
import com.spendwise.ui.MainActivity;
import com.spendwise.ui.SimpleTextWatcher;

/**
 * The sign in screen, and the activity Android launches. Validation errors appear beside
 * the field that caused them, and a failure gives the same message for a wrong password
 * and for an unknown account.
 */
public class LoginActivity extends AppCompatActivity {
    private static final String TAG = "LoginActivity";

    private ActivityLoginBinding binding;
    private AuthRepository authRepository;
    private GoogleSignInHelper googleSignInHelper;
    private ActivityResultLauncher<Intent> googleSignInLauncher;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Nullable
    private Runnable pendingAfterPermission;

    /** Called by the framework when the screen is first created. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityLoginBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);
        googleSignInHelper = new GoogleSignInHelper(this);

        registerNotificationPermissionLauncher();

        if (SessionManager.getInstance(this).isSignedIn()) {
            goToMain();
            return;
        }

        registerGoogleLauncher();
        wireUpValidation();

        binding.buttonSignIn.setOnClickListener(v -> attemptSignIn());
        binding.buttonGoogle.setOnClickListener(v -> startGoogleSignIn());
        binding.textSignUpPrompt.setOnClickListener(v ->
                startActivity(new Intent(this, SignUpActivity.class)));
        binding.textForgotPassword.setOnClickListener(v ->
                startActivity(new Intent(this, ForgotPasswordActivity.class)));
    }

    /** Register google launcher. */
    private void registerGoogleLauncher() {
        googleSignInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        GoogleSignInAccount account =
                                GoogleSignInHelper.extractAccount(result.getData());
                        if (account == null || account.getEmail() == null) {
                            showError(getString(R.string.auth_google_failed));
                            return;
                        }
                        completeFederatedSignIn(account);
                    } catch (ApiException e) {
                        showError(GoogleSignInHelper.describeFailure(this, e));
                    }
                });
    }

    /** Register notification permission launcher. */
    private void registerNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    Runnable pending = pendingAfterPermission;
                    pendingAfterPermission = null;
                    if (pending != null) {
                        pending.run();
                    } else if (SessionManager.getInstance(this).isSignedIn()) {
                        restoreFromCloud();
                        goToMain();
                    }
                });
    }

    /** Wire up validation. */
    private void wireUpValidation() {
        binding.editEmail.addTextChangedListener(new SimpleTextWatcher(() -> {
            binding.inputLayoutEmail.setError(null);
            hideError();
        }));
        binding.editPassword.addTextChangedListener(new SimpleTextWatcher(() -> {
            binding.inputLayoutPassword.setError(null);
            hideError();
        }));
    }

    /** Attempt sign in. */
    private void attemptSignIn() {
        hideError();
        String email = text(binding.editEmail);
        String password = text(binding.editPassword);

        Validators.Result emailCheck = Validators.validateEmail(email);
        if (!emailCheck.isValid()) {
            binding.inputLayoutEmail.setError(emailCheck.getMessage());
            binding.editEmail.requestFocus();
            return;
        }
        if (password.isEmpty()) {
            binding.inputLayoutPassword.setError(getString(R.string.auth_password));
            binding.editPassword.requestFocus();
            return;
        }

        setBusy(true);
        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            AuthResult result = authRepository.signIn(email, password);
            runOnUiThread(() -> {
                setBusy(false);
                if (result.isSuccess()) {
                    completeSignIn(result.getUser().getDisplayName(),
                            NotificationHelper.SignInMethod.EMAIL);
                } else {
                    showError(result.getMessage());
                }
            });
        });
    }

    /** Start google sign in. */
    private void startGoogleSignIn() {
        if (!GoogleSignInHelper.isConfigured(this)) {
            Log.i(TAG, "Google Sign-In has no web client id; refusing to start the picker");
            showError(getString(R.string.auth_google_not_configured));
            return;
        }
        try {
            googleSignInLauncher.launch(googleSignInHelper.getSignInIntent());
        } catch (RuntimeException e) {
            showError(getString(R.string.auth_google_not_configured));
        }
    }

    /** Complete federated sign in. */
    private void completeFederatedSignIn(GoogleSignInAccount account) {
        setBusy(true);
        String idToken = account.getIdToken();
        String email = account.getEmail();
        String displayName = account.getDisplayName();
        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            AuthResult result = authRepository.signInWithGoogle(idToken, email, displayName);
            runOnUiThread(() -> {
                setBusy(false);
                if (result.isSuccess()) {
                    completeSignIn(result.getUser().getDisplayName(),
                            NotificationHelper.SignInMethod.GOOGLE);
                } else {
                    showError(result.getMessage());
                }
            });
        });
    }

    /** Complete sign in. */
    private void completeSignIn(@Nullable String displayName,
                                NotificationHelper.SignInMethod method) {
        Runnable proceed = () -> {
            NotificationHelper.postSignedIn(this, displayName, method);
            restoreFromCloud();
            goToMain();
        };

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || NotificationHelper.canPostNotifications(this)) {
            proceed.run();
            return;
        }

        pendingAfterPermission = proceed;
        try {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        } catch (RuntimeException e) {
            pendingAfterPermission = null;
            proceed.run();
        }
    }

    /** Restore from cloud. */
    private void restoreFromCloud() {
        long userId = SessionManager.getInstance(this).getUserId();
        if (userId == SessionManager.NO_USER) {
            return;
        }
        new TransactionRepository(getApplicationContext()).pullAndReconcile(userId);
        new BudgetRepository(getApplicationContext()).pullAndReconcile(userId);
    }

    /** Sets the busy. */
    private void setBusy(boolean busy) {
        binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.buttonSignIn.setEnabled(!busy);
        binding.buttonGoogle.setEnabled(!busy);
        binding.buttonSignIn.setText(busy ? "" : getString(R.string.auth_sign_in));
    }

    /** Shows the error. */
    private void showError(String message) {
        binding.textError.setText(message);
        binding.textError.setVisibility(View.VISIBLE);
    }

    /** Hide error. */
    private void hideError() {
        binding.textError.setVisibility(View.GONE);
    }

    /** Go to main. */
    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** The trimmed contents of a field, so validation never sees stray spaces. */
    static String text(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
