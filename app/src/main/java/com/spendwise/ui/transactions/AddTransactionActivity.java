package com.spendwise.ui.transactions;

import android.app.DatePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.spendwise.R;
import com.spendwise.data.SessionManager;
import com.spendwise.data.SpendWiseDatabase;
import com.spendwise.data.entity.Transaction;
import com.spendwise.data.repository.TransactionRepository;
import com.spendwise.databinding.ActivityAddTransactionBinding;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionType;
import com.spendwise.domain.Validators;
import com.spendwise.notification.NotificationHelper;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.SystemBars;
import com.spendwise.util.CurrencyFormatter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * The three step entry screen, used for both adding and editing. Three short steps fit
 * above the keyboard where one long form does not. When a foreign display currency is
 * selected it writes the stored amount back unchanged unless the user actually retyped
 * it, so re-saving a row cannot drift its value.
 */
public class AddTransactionActivity extends AppCompatActivity {
    private static final int TOTAL_STEPS = 3;
    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private static final String STATE_STEP = "step";
    private static final String STATE_TYPE = "type";
    private static final String STATE_CATEGORY = "category";
    private static final String STATE_METHOD = "method";
    private static final String STATE_DATE_EPOCH_DAY = "dateEpochDay";
    private static final String STATE_SAVED = "saved";

    private ActivityAddTransactionBinding binding;
    private TransactionRepository repository;

    private Currency currency = Currency.LKR;

    private int currentStep = 1;
    private TransactionType selectedType = TransactionType.EXPENSE;
    private Category selectedCategory = Category.GROCERIES;
    private PaymentMethod selectedMethod = PaymentMethod.CARD;
    private LocalDate selectedDate = LocalDate.now();
    private boolean saved;

    private long editingId;

    private long editingCreatedAt;

    // The amount this edit started from, so an untouched field saves the stored figure
    // back unchanged instead of the rounded value a foreign display currency shows.
    private long editingAmountMinor;
    private String editingAmountText;

    public static final String EXTRA_TRANSACTION_ID = "com.spendwise.extra.TRANSACTION_ID";

    private static final String STATE_EDIT_ID = "editingId";
    private static final String STATE_EDIT_CREATED_AT = "editingCreatedAt";
    private static final String STATE_EDIT_AMOUNT = "editingAmountMinor";
    private static final String STATE_EDIT_AMOUNT_TEXT = "editingAmountText";

    /** Edit intent. */
    public static Intent editIntent(Context context, long transactionId) {
        Intent intent = new Intent(context, AddTransactionActivity.class);
        intent.putExtra(EXTRA_TRANSACTION_ID, transactionId);
        return intent;
    }

    /** Called by the framework when the screen is first created. */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddTransactionBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        repository = new TransactionRepository(this);
        currency = CurrencyManager.getCurrency(this);
        binding.inputLayoutAmount.setPrefixText(currency.getSymbol());

        SystemBars.addStatusBarTopMargin(binding.buttonBack);
        binding.buttonBack.setOnClickListener(v -> finish());
        binding.buttonExpense.setOnClickListener(v -> selectType(TransactionType.EXPENSE));
        binding.buttonIncome.setOnClickListener(v -> selectType(TransactionType.INCOME));
        binding.editDate.setOnClickListener(v -> pickDate());

        binding.buttonNext.setOnClickListener(v -> onNext());
        binding.buttonPrevious.setOnClickListener(v -> onPrevious());

        if (savedInstanceState != null) {
            readState(savedInstanceState);
        }
        if (saved) {
            finish();
            return;
        }
        buildPaymentChips(selectedMethod);
        selectType(selectedType, selectedCategory);
        binding.editDate.setText(selectedDate.format(DATE_DISPLAY));
        showStep(currentStep);

        long requestedId = getIntent().getLongExtra(EXTRA_TRANSACTION_ID, 0L);
        if (savedInstanceState == null && requestedId > 0) {
            loadForEditing(requestedId);
        } else if (editingId > 0) {
            applyEditingTitles();
        }
    }

    /** Loads the for editing. */
    private void loadForEditing(long transactionId) {
        SpendWiseDatabase.IO_EXECUTOR.execute(() -> {
            Transaction existing = SpendWiseDatabase.getInstance(this)
                    .transactionDao().findById(transactionId);
            runOnUiThread(() -> {
                if (binding == null || isFinishing()) {
                    return;
                }
                if (existing == null) {
                    Toast.makeText(this, R.string.tx_edit_missing, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                bindExisting(existing);
            });
        });
    }

    /** Draws the existing on screen. */
    private void bindExisting(Transaction existing) {
        editingId = existing.getId();
        editingCreatedAt = existing.getCreatedAt();

        selectedType = existing.typeAsEnum();
        selectedCategory = existing.categoryAsEnum();
        selectedMethod = existing.paymentMethodAsEnum();
        selectedDate = existing.getDate();

        binding.editDescription.setText(existing.getDescription());
        binding.editNote.setText(existing.getNote() == null ? "" : existing.getNote());
        editingAmountMinor = existing.getAmountMinor();
        editingAmountText = plainAmount(currency.minorFromLkr(editingAmountMinor));
        binding.editAmount.setText(editingAmountText);
        binding.editDate.setText(selectedDate.format(DATE_DISPLAY));

        buildPaymentChips(selectedMethod);
        selectType(selectedType, selectedCategory);
        applyEditingTitles();
        showStep(1);
    }

    /** Applies the editing titles. */
    private void applyEditingTitles() {
        binding.textTitle.setText(R.string.edit_transaction_title);
        showStep(currentStep);
    }

    /** LKR minor units for the amount field, keeping the stored figure if it was not edited. */
    private long enteredAmountMinor() {
        String typed = text(binding.editAmount);
        if (editingAmountMinor > 0 && typed.equals(editingAmountText)) {
            return editingAmountMinor;
        }
        return CurrencyFormatter.parseToMinorDisplay(typed, currency);
    }

    /** Plain amount. */
    private static String plainAmount(long minor) {
        return String.format(Locale.UK, "%d.%02d", minor / 100, Math.abs(minor % 100));
    }

    /** Called by the framework so state survives the screen being recreated. */
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_STEP, currentStep);
        outState.putString(STATE_TYPE, selectedType.name());
        outState.putString(STATE_CATEGORY, selectedCategory.name());
        outState.putString(STATE_METHOD, selectedMethod.name());
        outState.putLong(STATE_DATE_EPOCH_DAY, selectedDate.toEpochDay());
        outState.putBoolean(STATE_SAVED, saved);
        outState.putLong(STATE_EDIT_ID, editingId);
        outState.putLong(STATE_EDIT_CREATED_AT, editingCreatedAt);
        outState.putLong(STATE_EDIT_AMOUNT, editingAmountMinor);
        outState.putString(STATE_EDIT_AMOUNT_TEXT, editingAmountText);
    }

    /** Called by the framework at the matching point in the lifecycle. */
    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        binding.editDate.setText(selectedDate.format(DATE_DISPLAY));
        checkChipWithTag(binding.chipGroupCategory, selectedCategory);
        checkChipWithTag(binding.chipGroupPayment, selectedMethod);
        if (currentStep == TOTAL_STEPS) {
            if (Validators.validateAmount(text(binding.editAmount), currency).isValid()) {
                renderSummary();
            } else {
                showStep(2);
            }
        }
    }

    /** Read state. */
    private void readState(Bundle state) {
        saved = state.getBoolean(STATE_SAVED, false);
        editingId = state.getLong(STATE_EDIT_ID, 0L);
        editingCreatedAt = state.getLong(STATE_EDIT_CREATED_AT, 0L);
        editingAmountMinor = state.getLong(STATE_EDIT_AMOUNT, 0L);
        editingAmountText = state.getString(STATE_EDIT_AMOUNT_TEXT);
        currentStep = Math.max(1, Math.min(TOTAL_STEPS, state.getInt(STATE_STEP, currentStep)));
        selectedType = enumOrDefault(TransactionType.class,
                state.getString(STATE_TYPE), selectedType);
        selectedCategory = enumOrDefault(Category.class,
                state.getString(STATE_CATEGORY), selectedCategory);
        selectedMethod = enumOrDefault(PaymentMethod.class,
                state.getString(STATE_METHOD), selectedMethod);
        try {
            selectedDate = LocalDate.ofEpochDay(
                    state.getLong(STATE_DATE_EPOCH_DAY, selectedDate.toEpochDay()));
        } catch (RuntimeException e) {
            selectedDate = LocalDate.now();
        }
    }

    /** Enum or default. */
    private static <E extends Enum<E>> E enumOrDefault(Class<E> type, @Nullable String name,
                                                       E fallback) {
        if (name == null) {
            return fallback;
        }
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }

    /** Checks the chip with tag and reports what is wrong. */
    private static void checkChipWithTag(ChipGroup group, Object tag) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && child.getTag() == tag) {
                ((Chip) child).setChecked(true);
                return;
            }
        }
    }

    /**
     * Switches between income and expense, and re-offers only the categories that belong
     * to the direction chosen.
     */
    private void selectType(TransactionType type) {
        selectType(type, null);
    }

    /**
     * Switches between income and expense, and re-offers only the categories that belong
     * to the direction chosen.
     */
    private void selectType(TransactionType type, @Nullable Category preferred) {
        selectedType = type;
        binding.buttonExpense.setChecked(type == TransactionType.EXPENSE);
        binding.buttonIncome.setChecked(type == TransactionType.INCOME);
        paintTypeCard(binding.buttonExpense, type == TransactionType.EXPENSE,
                R.color.status_expense, R.color.status_expense_container);
        paintTypeCard(binding.buttonIncome, type == TransactionType.INCOME,
                R.color.status_income, R.color.status_income_container);

        binding.chipGroupCategory.removeAllViews();
        Category firstOffered = null;
        boolean preferredOffered = false;
        for (Category category : Category.values()) {
            if (category.getNaturalType() != type) {
                continue;
            }
            Chip chip = new Chip(this);
            chip.setText(category.getDisplayName());
            chip.setCheckable(true);
            chip.setTag(category);
            chip.setOnClickListener(v -> selectedCategory = (Category) v.getTag());
            binding.chipGroupCategory.addView(chip);
            if (firstOffered == null) {
                firstOffered = category;
            }
            preferredOffered |= category == preferred;
        }

        Category resolved = preferredOffered ? preferred : firstOffered;
        if (resolved != null) {
            selectedCategory = resolved;
            checkChipWithTag(binding.chipGroupCategory, resolved);
        }
    }

    /** Paint type card. */
    private void paintTypeCard(MaterialButton card, boolean selected,
                               @ColorRes int accentRes, @ColorRes int containerRes) {
        int accent = ContextCompat.getColor(this, accentRes);
        int container = ContextCompat.getColor(this, containerRes);
        int restBackground = ContextCompat.getColor(this, R.color.surface_container_high);
        int restForeground = ContextCompat.getColor(this, R.color.text_secondary);
        int restStroke = ContextCompat.getColor(this, R.color.outline_variant);

        card.setBackgroundTintList(
                ColorStateList.valueOf(selected ? container : restBackground));
        card.setTextColor(selected ? accent : restForeground);
        card.setIconTint(ColorStateList.valueOf(selected ? accent : restForeground));
        card.setStrokeColor(ColorStateList.valueOf(selected ? accent : restStroke));
        card.setStrokeWidth(dp(selected ? 2 : 1));
    }

    /** Dp. */
    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    /** Builds the payment chips. */
    private void buildPaymentChips(PaymentMethod preferred) {
        binding.chipGroupPayment.removeAllViews();
        for (PaymentMethod method : PaymentMethod.values()) {
            Chip chip = new Chip(this);
            chip.setText(method.getDisplayName());
            chip.setCheckable(true);
            chip.setTag(method);
            chip.setOnClickListener(v -> selectedMethod = (PaymentMethod) v.getTag());
            binding.chipGroupPayment.addView(chip);
        }
        selectedMethod = preferred;
        checkChipWithTag(binding.chipGroupPayment, preferred);
    }

    /** Called by the framework at the matching point in the lifecycle. */
    private void onNext() {
        if (currentStep == 1) {
            showStep(2);
            return;
        }
        if (currentStep == 2) {
            if (!validateStep2()) {
                return;
            }
            renderSummary();
            showStep(3);
            return;
        }
        save();
    }

    /** Called by the framework at the matching point in the lifecycle. */
    private void onPrevious() {
        if (currentStep > 1) {
            showStep(currentStep - 1);
        }
    }

    /** Checks the step2 and reports what is wrong. */
    private boolean validateStep2() {
        String description = text(binding.editDescription);
        Validators.Result descriptionCheck = Validators.validateDescription(description);
        if (!descriptionCheck.isValid()) {
            binding.inputLayoutDescription.setError(descriptionCheck.getMessage());
            binding.editDescription.requestFocus();
            return false;
        }
        binding.inputLayoutDescription.setError(null);

        Validators.Result amountCheck = Validators.validateAmount(text(binding.editAmount), currency);
        if (!amountCheck.isValid()) {
            binding.inputLayoutAmount.setError(amountCheck.getMessage());
            binding.editAmount.requestFocus();
            return false;
        }
        binding.inputLayoutAmount.setError(null);
        return true;
    }

    /** Draws the summary on screen. */
    private void renderSummary() {
        long amountMinor = enteredAmountMinor();
        boolean income = selectedType == TransactionType.INCOME;
        String note = text(binding.editNote);

        binding.textSummaryAmount.setText(
                CurrencyFormatter.formatSignedDisplay(amountMinor, income, currency));
        binding.textSummaryAmount.setTextColor(ContextCompat.getColor(this,
                income ? R.color.status_income : R.color.status_expense));
        binding.textSummaryTitle.setText(text(binding.editDescription));

        StringBuilder details = new StringBuilder()
                .append(selectedCategory.getDisplayName()).append(" · ")
                .append(selectedMethod.getDisplayName()).append('\n')
                .append(selectedDate.format(DATE_DISPLAY));
        if (!note.isEmpty()) {
            details.append('\n').append(note);
        }
        binding.textSummary.setText(details.toString());
    }

    /** Shows the step. */
    private void showStep(int step) {
        currentStep = step;
        binding.layoutStep1.setVisibility(step == 1 ? View.VISIBLE : View.GONE);
        binding.layoutStep2.setVisibility(step == 2 ? View.VISIBLE : View.GONE);
        binding.layoutStep3.setVisibility(step == 3 ? View.VISIBLE : View.GONE);

        binding.progressSteps.setProgressCompat(step, true);
        binding.textStepLabel.setText(getString(R.string.add_step_of, step, TOTAL_STEPS));
        binding.scrollSteps.scrollTo(0, 0);
        binding.buttonPrevious.setVisibility(step == 1 ? View.INVISIBLE : View.VISIBLE);
        binding.buttonNext.setText(step == TOTAL_STEPS
                ? getString(editingId > 0 ? R.string.tx_edit_save : R.string.add_save)
                : getString(R.string.add_next));
    }

    /** Pick date. */
    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> {
                    selectedDate = LocalDate.of(year, month + 1, dayOfMonth);
                    binding.editDate.setText(selectedDate.format(DATE_DISPLAY));
                },
                selectedDate.getYear(),
                selectedDate.getMonthValue() - 1,
                selectedDate.getDayOfMonth());

        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    /** Writes the value to storage. */
    private void save() {
        if (saved) {
            return;
        }
        long userId = SessionManager.getInstance(this).getUserId();
        String note = text(binding.editNote);

        try {
            Transaction transaction = Transaction.create(
                    userId,
                    text(binding.editDescription),
                    note.isEmpty() ? null : note,
                    enteredAmountMinor(),
                    selectedType,
                    selectedCategory,
                    selectedMethod,
                    selectedDate);

            binding.buttonNext.setEnabled(false);
            saved = true;

            if (editingId > 0) {
                transaction.setId(editingId);
                transaction.setCreatedAt(editingCreatedAt);
                repository.update(transaction, () -> runOnUiThread(() -> {
                    Toast.makeText(this, R.string.tx_edit_saved, Toast.LENGTH_SHORT).show();
                    finish();
                }));
                return;
            }

            repository.insert(transaction, () -> runOnUiThread(() -> {
                NotificationHelper.postTransactionRecorded(this,
                        transaction.typeAsEnum(),
                        transaction.getAmountMinor(),
                        transaction.categoryAsEnum());
                Toast.makeText(this, R.string.add_saved, Toast.LENGTH_SHORT).show();
                finish();
            }));
        } catch (IllegalArgumentException e) {
            saved = false;
            binding.buttonNext.setEnabled(true);
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /** Text. */
    private static String text(com.google.android.material.textfield.TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString().trim();
    }
}
