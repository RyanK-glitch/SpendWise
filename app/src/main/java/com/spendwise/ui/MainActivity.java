package com.spendwise.ui;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.NavigationUI;

import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.databinding.ActivityMainBinding;
import com.spendwise.domain.Category;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionFilter;
import com.spendwise.notification.BudgetAlertWorker;
import com.spendwise.notification.NotificationHelper;
import com.spendwise.ui.auth.LoginActivity;
import com.spendwise.ui.search.GlobalSearchActivity;
import com.spendwise.ui.transactions.AddTransactionActivity;
import com.spendwise.ui.transactions.TransactionsViewModel;

import java.util.EnumSet;

/**
 * The shell that hosts the five tabs. It checks there is a session, connects the
 * bottom navigation bar to the navigation graph and wires the + button to the entry
 * screen. There is no business logic in here on purpose.
 */
public class MainActivity extends AppCompatActivity {
    private ActivityMainBinding binding;
    private NavController navController;
    private ActivityResultLauncher<String> notificationPermissionLauncher;

    /** Called by the framework when the screen is first created. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Nobody reaches the ledger without a session.
        if (!SessionManager.getInstance(this).isSignedIn()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        NavHostFragment navHostFragment = (NavHostFragment)
                getSupportFragmentManager().findFragmentById(R.id.navHostFragment);
        if (navHostFragment == null) {
            throw new IllegalStateException("NavHostFragment missing from activity_main.xml");
        }
        navController = navHostFragment.getNavController();
        NavigationUI.setupWithNavController(binding.bottomNavigation, navController);

        binding.fabAdd.setOnClickListener(v ->
                startActivity(new Intent(this, AddTransactionActivity.class)));

        navController.addOnDestinationChangedListener((controller, destination, args) -> {
            if (destination.getId() == R.id.profileFragment) {
                binding.fabAdd.hide();
            } else {
                binding.fabAdd.show();
            }
        });

        registerNotificationPermissionLauncher();
        if (savedInstanceState == null) {
            requestNotificationPermissionIfNeeded();
        }

        BudgetAlertWorker.schedule(getApplicationContext());
        handleDeepLink(getIntent());
    }

    /** Called by the framework at the matching point in the lifecycle. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleDeepLink(intent);
    }

    /** Handle deep link. */
    private void handleDeepLink(@Nullable Intent intent) {
        if (intent == null || navController == null) {
            return;
        }
        String destination = intent.getStringExtra(NotificationHelper.EXTRA_DESTINATION);
        if (destination == null) {
            return;
        }
        switch (destination) {
            case NotificationHelper.DESTINATION_BUDGETS:
                navController.navigate(R.id.budgetsFragment);
                break;
            case NotificationHelper.DESTINATION_REPORTS:
                navController.navigate(R.id.reportsFragment);
                break;
            case NotificationHelper.DESTINATION_TRANSACTIONS:
                navController.navigate(R.id.transactionsFragment);
                applySearchExtras(intent);
                break;
            default:
                break;
        }

        intent.removeExtra(NotificationHelper.EXTRA_DESTINATION);
    }

    /** Applies the search extras. */
    private void applySearchExtras(Intent intent) {
        String query = intent.getStringExtra(GlobalSearchActivity.EXTRA_QUERY);
        String categoryName = intent.getStringExtra(GlobalSearchActivity.EXTRA_CATEGORY);
        if (query == null && categoryName == null) {
            return;
        }

        TransactionsViewModel viewModel =
                new ViewModelProvider(this).get(TransactionsViewModel.class);
        Category category = Category.fromNameOrNull(categoryName);

        viewModel.applyFilters(
                category == null ? EnumSet.noneOf(Category.class) : EnumSet.of(category),
                EnumSet.noneOf(PaymentMethod.class),
                null, null, null,
                TransactionFilter.NO_MIN_AMOUNT, TransactionFilter.NO_MAX_AMOUNT);
        viewModel.setQuery(query == null ? "" : query);

        intent.removeExtra(GlobalSearchActivity.EXTRA_QUERY);
        intent.removeExtra(GlobalSearchActivity.EXTRA_CATEGORY);
    }

    /** Register notification permission launcher. */
    private void registerNotificationPermissionLauncher() {
        notificationPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                });
    }

    /** Request notification permission if needed. */
    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (!NotificationHelper.canPostNotifications(this)) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }
}
