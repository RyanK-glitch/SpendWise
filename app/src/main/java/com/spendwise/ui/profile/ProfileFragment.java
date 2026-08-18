package com.spendwise.ui.profile;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.spendwise.BuildConfig;
import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.repository.AuthRepository;
import com.spendwise.data.sync.FirestoreSync;
import com.spendwise.databinding.FragmentProfileBinding;
import com.spendwise.domain.AuthProvider;
import com.spendwise.domain.Currency;
import com.spendwise.notification.BudgetAlertWorker;
import com.spendwise.notification.NotificationHelper;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.ThemeManager;
import com.spendwise.ui.auth.GoogleSignInHelper;
import com.spendwise.ui.auth.LoginActivity;
import com.spendwise.ui.transactions.TransactionsViewModel;
import com.spendwise.util.CurrencyFormatter;

import java.util.Locale;

import androidx.lifecycle.ViewModelProvider;

/**
 * The Profile tab. Theme, display currency, where the data is stored, and sign out. The
 * storage line is derived from whether sync is actually on, not hard coded.
 */
public class ProfileFragment extends Fragment {
    private static final String TAG = "ProfileFragment";

    private FragmentProfileBinding binding;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private boolean signingOut;

    /** Called by the framework to inflate the fragment's layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** Called by the framework once the fragment's views exist. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SessionManager session = SessionManager.getInstance(requireContext());

        String name = session.getDisplayName();
        String email = session.getEmail();
        binding.textName.setText(name == null ? "" : name);
        binding.textEmail.setText(email == null ? "" : email);
        binding.textAvatar.setText(initialOf(name, email));

        AuthProvider provider = AuthProvider.fromNameOrNull(session.getProvider());
        binding.textProvider.setText(describeProvider(provider));

        binding.textVersion.setText(getString(R.string.profile_version, BuildConfig.VERSION_NAME));

        binding.textSessionWarning.setVisibility(
                session.isPersistent() ? View.GONE : View.VISIBLE);

        TransactionsViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(TransactionsViewModel.class);
        viewModel.getAllTransactions().observe(getViewLifecycleOwner(), ledger -> {
            if (binding != null) {
                binding.textLedgerSize.setText(getString(R.string.profile_ledger_size,
                        ledger == null ? 0 : ledger.size()));
            }
        });

        updateNotificationStatus();
        setUpThemeToggle();
        setUpCurrencyRow();

        applyStorageDisclosure();

        binding.rowNotifications.setOnClickListener(v -> openNotificationSettings());
        binding.rowPrivacy.setOnClickListener(v ->
                showInfo(R.string.rp_profile_privacy, storageDisclosure()));
        binding.rowAbout.setOnClickListener(v -> showAbout());
        binding.buttonSignOut.setOnClickListener(v -> confirmSignOut());
    }

    /** Sets the up theme toggle. */
    private void setUpThemeToggle() {
        ThemeManager.Mode current = ThemeManager.getMode(requireContext());
        binding.toggleTheme.check(buttonIdFor(current));
        binding.textAppearanceSummary.setText(summaryFor(current));

        binding.toggleTheme.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked || binding == null) {
                return;
            }
            ThemeManager.Mode mode = modeFor(checkedId);
            binding.textAppearanceSummary.setText(summaryFor(mode));
            if (mode != ThemeManager.getMode(requireContext())) {
                ThemeManager.setMode(requireContext(), mode);
            }
        });
    }

    /** Button id for. */
    private int buttonIdFor(ThemeManager.Mode mode) {
        if (mode == ThemeManager.Mode.LIGHT) {
            return R.id.buttonThemeLight;
        }
        if (mode == ThemeManager.Mode.DARK) {
            return R.id.buttonThemeDark;
        }
        return R.id.buttonThemeSystem;
    }

    private ThemeManager.Mode modeFor(int buttonId) {
        if (buttonId == R.id.buttonThemeLight) {
            return ThemeManager.Mode.LIGHT;
        }
        if (buttonId == R.id.buttonThemeDark) {
            return ThemeManager.Mode.DARK;
        }
        return ThemeManager.Mode.FOLLOW_SYSTEM;
    }

    /** Summary for. */
    @StringRes
    private static int summaryFor(ThemeManager.Mode mode) {
        if (mode == ThemeManager.Mode.LIGHT) {
            return R.string.theme_summary_light;
        }
        if (mode == ThemeManager.Mode.DARK) {
            return R.string.theme_summary_dark;
        }
        return R.string.theme_summary_system;
    }

    /** Sets the up currency row. */
    private void setUpCurrencyRow() {
        renderCurrencySummary(CurrencyManager.getCurrency(requireContext()));
        binding.rowCurrency.setOnClickListener(v -> showCurrencyPicker());
    }

    /** Draws the currency summary on screen. */
    private void renderCurrencySummary(Currency currency) {
        binding.textCurrencySummary.setText(currency.isBase()
                ? getString(R.string.profile_currency_base, currency.getDisplayName())
                : getString(R.string.profile_currency_rate,
                        currency.getDisplayName(),
                        currency.getCode(),
                        CurrencyFormatter.format(currency.unitInLkrMinor())));
    }

    /** Shows the currency picker. */
    private void showCurrencyPicker() {
        Currency[] options = Currency.values();
        CharSequence[] labels = new CharSequence[options.length];
        for (int i = 0; i < options.length; i++) {
            labels[i] = getString(R.string.profile_currency_item,
                    options[i].getCode(), options[i].getDisplayName());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.profile_currency)
                .setSingleChoiceItems(labels,
                        CurrencyManager.getCurrency(requireContext()).ordinal(),
                        (dialog, which) -> {
                            dialog.dismiss();
                            applyCurrency(options[which]);
                        })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    /** Applies the currency. */
    private void applyCurrency(Currency currency) {
        if (binding == null || currency == CurrencyManager.getCurrency(requireContext())) {
            return;
        }
        CurrencyManager.setCurrency(requireContext(), currency);
        renderCurrencySummary(currency);
        Toast.makeText(requireContext(),
                getString(R.string.profile_currency_changed, currency.getCode()),
                Toast.LENGTH_SHORT).show();
    }

    /** Called by the framework each time the screen comes back to the front. */
    @Override
    public void onResume() {
        super.onResume();
        if (binding != null) {
            updateNotificationStatus();
        }
    }

    /** Updates the notification status. */
    private void updateNotificationStatus() {
        boolean allowed = NotificationHelper.canPostNotifications(requireContext());
        binding.textNotificationStatus.setText(allowed
                ? getString(R.string.profile_enable_notifications)
                : getString(R.string.profile_notification_permission_needed));
        binding.switchNotifications.setChecked(allowed);
    }

    /** Open notification settings. */
    private void openNotificationSettings() {
        Intent intent;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, requireContext().getPackageName());
        } else {
            intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", requireContext().getPackageName(), null));
        }
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Shows the info. */
    private void showInfo(@StringRes int title, @StringRes int message) {
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    /** True when mirrored to the cloud. */
    private boolean isMirroredToTheCloud() {
        return FirestoreSync.create(requireContext().getApplicationContext()).isEnabled();
    }

    /** Storage disclosure. */
    @StringRes
    private int storageDisclosure() {
        return isMirroredToTheCloud()
                ? R.string.profile_data_synced
                : R.string.profile_data_local;
    }

    /** Applies the storage disclosure. */
    private void applyStorageDisclosure() {
        binding.textPrivacySubtitle.setText(isMirroredToTheCloud()
                ? R.string.rpp_privacy_subtitle_synced
                : R.string.rpp_privacy_subtitle);
    }

    /** Shows the about. */
    private void showAbout() {
        String body = getString(R.string.app_tagline)
                + "\n\n" + getString(R.string.profile_version, BuildConfig.VERSION_NAME)
                + "\n" + getString(storageDisclosure());
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.app_name)
                .setMessage(body)
                .setPositiveButton(R.string.action_close, null)
                .show();
    }

    /** Confirm sign out. */
    private void confirmSignOut() {
        new AlertDialog.Builder(requireContext())
                .setMessage(R.string.profile_sign_out_confirm)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.profile_sign_out, (dialog, which) -> signOut())
                .show();
    }

    /** Signs the user out and sends them back to the sign in screen. */
    private void signOut() {
        if (signingOut) {
            return;
        }
        signingOut = true;
        if (binding != null) {
            binding.buttonSignOut.setEnabled(false);
        }

        Context appContext = requireContext().getApplicationContext();

        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            try {
                BudgetAlertWorker.cancel(appContext);
                new AuthRepository(appContext).signOut();
                new GoogleSignInHelper(appContext).signOut();
            } catch (RuntimeException e) {
                Log.w(TAG, "Sign-out cleanup failed: " + e.getClass().getSimpleName());
            }
            mainHandler.post(() -> returnToLogin(appContext));
        });
    }

    /** Return to login. */
    private void returnToLogin(Context appContext) {
        Intent intent = new Intent(appContext, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        appContext.startActivity(intent);

        Activity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    /** Describe provider. */
    private static String describeProvider(@Nullable AuthProvider provider) {
        if (provider == AuthProvider.GOOGLE) {
            return "Signed in with Google";
        }
        if (provider == AuthProvider.FIREBASE) {
            return "Signed in with Firebase";
        }
        return "Signed in with email and password";
    }

    /** Initial of. */
    private static String initialOf(@Nullable String name, @Nullable String email) {
        String source = (name != null && !name.trim().isEmpty()) ? name.trim()
                : (email == null ? "?" : email);
        return source.isEmpty() ? "?" : source.substring(0, 1).toUpperCase(Locale.UK);
    }

    /** Called by the framework as the fragment's views are torn down. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
