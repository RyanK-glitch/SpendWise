package com.spendwise.ui.auth;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.repository.AuthRepository;
import com.spendwise.databinding.ActivitySignupBinding;
import com.spendwise.domain.AuthResult;
import com.spendwise.domain.Validators;
import com.spendwise.notification.BudgetAlertWorker;
import com.spendwise.notification.NotificationHelper;
import com.spendwise.ui.MainActivity;
import com.spendwise.ui.SimpleTextWatcher;
import com.spendwise.ui.SystemBars;

/**
 * Account creation. The password rules are shown before the user submits rather than
 * after the app rejects the form.
 */
public class SignUpActivity extends AppCompatActivity {
    private ActivitySignupBinding binding;
    private AuthRepository authRepository;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    @Nullable
    private Runnable pendingAfterPermission;

    /** Called by the framework when the screen is first created. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivitySignupBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);
        registerNotificationPermissionLauncher();

        SystemBars.addStatusBarTopMargin(binding.buttonBack);
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.textSignInPrompt.setOnClickListener(v -> finish());
        binding.buttonSignUp.setOnClickListener(v -> attemptSignUp());

        clearErrorOnEdit();
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
                        goToMain();
                    }
                });
    }

    /** Clear error on edit. */
    private void clearErrorOnEdit() {
        binding.editName.addTextChangedListener(new SimpleTextWatcher(
                () -> binding.inputLayoutName.setError(null)));
        binding.editEmail.addTextChangedListener(new SimpleTextWatcher(
                () -> binding.inputLayoutEmail.setError(null)));
        binding.editPassword.addTextChangedListener(new SimpleTextWatcher(
                () -> binding.inputLayoutPassword.setError(null)));
        binding.editConfirm.addTextChangedListener(new SimpleTextWatcher(
                () -> binding.inputLayoutConfirm.setError(null)));
    }

    /** Attempt sign up. */
    private void attemptSignUp() {
        binding.textError.setVisibility(View.GONE);

        String name = LoginActivity.text(binding.editName);
        String email = LoginActivity.text(binding.editEmail);
        String password = binding.editPassword.getText() == null
                ? "" : binding.editPassword.getText().toString();
        String confirm = binding.editConfirm.getText() == null
                ? "" : binding.editConfirm.getText().toString();

        if (!check(Validators.validateDisplayName(name), binding.inputLayoutName)) return;
        if (!check(Validators.validateEmail(email), binding.inputLayoutEmail)) return;
        if (!check(Validators.validatePassword(password), binding.inputLayoutPassword)) return;
        if (!check(Validators.validatePasswordConfirmation(password, confirm),
                binding.inputLayoutConfirm)) return;

        setBusy(true, getString(R.string.auth_creating_account));

        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            AuthResult result = authRepository.signUp(email, name, password);
            if (!result.isSuccess()) {
                runOnUiThread(() -> {
                    setBusy(false, null);
                    binding.textError.setText(result.getMessage());
                    binding.textError.setVisibility(View.VISIBLE);
                });
                return;
            }

            BudgetAlertWorker.schedule(getApplicationContext());

            runOnUiThread(() -> {
                setBusy(false, null);
                completeSignUp(result.getUser().getDisplayName());
            });
        });
    }

    /** Complete sign up. */
    private void completeSignUp(@Nullable String displayName) {
        Runnable proceed = () -> {
            NotificationHelper.postSignedIn(this, displayName,
                    NotificationHelper.SignInMethod.EMAIL);
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

    /** Go to main. */
    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /** Checks the value and reports what is wrong. */
    private boolean check(Validators.Result result,
                          com.google.android.material.textfield.TextInputLayout layout) {
        if (result.isValid()) {
            layout.setError(null);
            return true;
        }
        layout.setError(result.getMessage());
        layout.requestFocus();
        return false;
    }

    /** Sets the busy. */
    private void setBusy(boolean busy, @Nullable String label) {
        binding.progressBar.setVisibility(busy ? View.VISIBLE : View.GONE);
        binding.buttonSignUp.setEnabled(!busy);
        binding.buttonSignUp.setText(busy
                ? (label == null ? "" : label)
                : getString(R.string.auth_sign_up));
    }
}
