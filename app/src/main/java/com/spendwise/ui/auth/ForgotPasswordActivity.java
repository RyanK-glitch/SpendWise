package com.spendwise.ui.auth;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.spendwise.R;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.repository.AuthRepository;
import com.spendwise.databinding.ActivityForgotPasswordBinding;
import com.spendwise.domain.AuthResult;
import com.spendwise.domain.Validators;
import com.spendwise.ui.SimpleTextWatcher;
import com.spendwise.ui.SystemBars;

/**
 * Password recovery. The confirmation message is the same whether or not the address is
 * registered, so the screen cannot be used to discover which accounts exist.
 */
public class ForgotPasswordActivity extends AppCompatActivity {
    private ActivityForgotPasswordBinding binding;
    private AuthRepository authRepository;

    /** Called by the framework when the screen is first created. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityForgotPasswordBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        authRepository = new AuthRepository(this);

        SystemBars.addStatusBarTopMargin(binding.buttonBack);
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.textBackToSignIn.setOnClickListener(v -> finish());
        binding.buttonReset.setOnClickListener(v -> requestReset());

        binding.editEmail.addTextChangedListener(new SimpleTextWatcher(() -> {
            binding.inputLayoutEmail.setError(null);
            binding.textStatus.setVisibility(View.GONE);
        }));
    }

    /** Request reset. */
    private void requestReset() {
        String email = LoginActivity.text(binding.editEmail);

        Validators.Result emailCheck = Validators.validateEmail(email);
        if (!emailCheck.isValid()) {
            binding.inputLayoutEmail.setError(emailCheck.getMessage());
            return;
        }

        binding.buttonReset.setEnabled(false);
        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            AuthResult result = authRepository.requestPasswordReset(email);
            runOnUiThread(() -> {
                binding.buttonReset.setEnabled(true);
                binding.textStatus.setText(result.getMessage());
                binding.textStatus.setTextColor(
                        ContextCompat.getColor(this, R.color.status_income));
                binding.textStatus.setCompoundDrawablesRelativeWithIntrinsicBounds(
                        R.drawable.ic_check, 0, 0, 0);
                binding.textStatus.setVisibility(View.VISIBLE);
            });
        });
    }
}
