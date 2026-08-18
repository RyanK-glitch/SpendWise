package com.spendwise.ui.transactions;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipDrawable;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.spendwise.R;
import com.spendwise.databinding.SheetFilterBinding;
import com.spendwise.domain.Category;
import com.spendwise.domain.Currency;
import com.spendwise.domain.PaymentMethod;
import com.spendwise.domain.TransactionFilter;
import com.spendwise.domain.TransactionType;
import com.spendwise.ui.CurrencyManager;
import com.spendwise.ui.SimpleTextWatcher;
import com.spendwise.util.CurrencyFormatter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * The filter sheet. Six criteria are collected here and nothing is applied to the list
 * until Apply is pressed.
 */
public class FilterBottomSheet extends BottomSheetDialogFragment {
    public static final String TAG = "FilterBottomSheet";

    private static final String STATE_FROM = "filter_from_epoch_day";
    private static final String STATE_TO = "filter_to_epoch_day";
    private static final String STATE_CATEGORIES = "filter_categories";
    private static final String STATE_METHODS = "filter_methods";

    private static final long INVALID_AMOUNT = -1L;

    private static final DateTimeFormatter DATE_DISPLAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.UK);

    private SheetFilterBinding binding;
    private TransactionsViewModel viewModel;

    private LocalDate selectedFrom;
    private LocalDate selectedTo;

    private Currency currency = Currency.LKR;

    /** New instance. */
    public static FilterBottomSheet newInstance() {
        return new FilterBottomSheet();
    }

    /** Called by the framework to inflate the fragment's layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetFilterBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    /** Called by the framework once the fragment's views exist. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(TransactionsViewModel.class);
        TransactionFilter current = viewModel.currentFilter();

        currency = CurrencyManager.getCurrency(requireContext());
        binding.layoutAmountMin.setPrefixText(currency.getSymbol());
        binding.layoutAmountMax.setPrefixText(currency.getSymbol());

        Set<Category> tickedCategories = current.getCategories();
        Set<PaymentMethod> tickedMethods = current.getPaymentMethods();
        if (savedInstanceState != null) {
            tickedCategories = readEnums(savedInstanceState, STATE_CATEGORIES,
                    Category.class, Category::fromNameOrNull);
            tickedMethods = readEnums(savedInstanceState, STATE_METHODS,
                    PaymentMethod.class, PaymentMethod::fromNameOrNull);
        }

        buildCategoryChips(tickedCategories);
        buildPaymentChips(tickedMethods);
        restoreTypeSelection(current.getType());
        restoreDateRange(current);
        restoreAmountRange(current);

        if (savedInstanceState != null) {
            selectedFrom = epochDay(savedInstanceState, STATE_FROM);
            selectedTo = epochDay(savedInstanceState, STATE_TO);
        }

        binding.editDateFrom.setOnClickListener(v ->
                pickDate(selectedFrom, date -> {
                    selectedFrom = date;
                    binding.editDateFrom.setText(date.format(DATE_DISPLAY));
                }));

        binding.editDateTo.setOnClickListener(v ->
                pickDate(selectedTo, date -> {
                    selectedTo = date;
                    binding.editDateTo.setText(date.format(DATE_DISPLAY));
                }));

        binding.editAmountMin.addTextChangedListener(
                new SimpleTextWatcher(() -> binding.layoutAmountMin.setError(null)));
        binding.editAmountMax.addTextChangedListener(
                new SimpleTextWatcher(() -> binding.layoutAmountMax.setError(null)));

        binding.buttonApply.setOnClickListener(v -> applyAndDismiss());
        binding.buttonClear.setOnClickListener(v -> {
            viewModel.clearFilters();
            dismiss();
        });

        binding.buttonClose.setOnClickListener(v -> dismiss());
    }

    /** Called by the framework so state survives the screen being recreated. */
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (selectedFrom != null) {
            outState.putLong(STATE_FROM, selectedFrom.toEpochDay());
        }
        if (selectedTo != null) {
            outState.putLong(STATE_TO, selectedTo.toEpochDay());
        }

        if (binding != null) {
            outState.putStringArrayList(STATE_CATEGORIES,
                    checkedNames(binding.chipGroupCategory));
            outState.putStringArrayList(STATE_METHODS,
                    checkedNames(binding.chipGroupPayment));
        }
    }

    /** Checks the ed names and reports what is wrong. */
    private static ArrayList<String> checkedNames(ChipGroup group) {
        ArrayList<String> names = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()
                    && child.getTag() instanceof Enum) {
                names.add(((Enum<?>) child.getTag()).name());
            }
        }
        return names;
    }

    /** Read enums. */
    private static <E extends Enum<E>> Set<E> readEnums(Bundle state, String key,
                                                        Class<E> type,
                                                        Lookup<E> lookup) {
        Set<E> result = EnumSet.noneOf(type);
        ArrayList<String> names = state.getStringArrayList(key);
        if (names == null) {
            return result;
        }
        for (String name : names) {
            E value = lookup.byName(name);
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private interface Lookup<E> {
        @Nullable
        E byName(String name);
    }

    /** Epoch day. */
    @Nullable
    private static LocalDate epochDay(Bundle state, String key) {
        return state.containsKey(key) ? LocalDate.ofEpochDay(state.getLong(key)) : null;
    }

    /** Called by the framework as the screen becomes visible. */
    @Override
    public void onStart() {
        super.onStart();
        if (getDialog() == null) {
            return;
        }
        View sheet = getDialog().findViewById(
                com.google.android.material.R.id.design_bottom_sheet);
        if (sheet != null) {
            BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(sheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    /** Builds the category chips. */
    private void buildCategoryChips(Set<Category> selected) {
        for (Category category : Category.values()) {
            Chip chip = filterChip(category.getDisplayName(), selected.contains(category));
            chip.setTag(category);
            binding.chipGroupCategory.addView(chip);
        }
    }

    /** Builds the payment chips. */
    private void buildPaymentChips(Set<PaymentMethod> selected) {
        for (PaymentMethod method : PaymentMethod.values()) {
            Chip chip = filterChip(method.getDisplayName(), selected.contains(method));
            chip.setTag(method);
            binding.chipGroupPayment.addView(chip);
        }
    }

    /** Filter chip. */
    private Chip filterChip(CharSequence label, boolean checked) {
        Chip chip = new Chip(requireContext());
        chip.setChipDrawable(ChipDrawable.createFromAttributes(
                requireContext(), null, 0, R.style.Widget_SpendWise_Chip));
        chip.setEnsureMinTouchTargetSize(true);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(true);
        chip.setChecked(checked);
        return chip;
    }

    /** Restore type selection. */
    private void restoreTypeSelection(@Nullable TransactionType type) {
        if (type == TransactionType.INCOME) {
            binding.chipTypeIncome.setChecked(true);
        } else if (type == TransactionType.EXPENSE) {
            binding.chipTypeExpense.setChecked(true);
        } else {
            binding.chipTypeAll.setChecked(true);
        }
    }

    /** Restore date range. */
    private void restoreDateRange(TransactionFilter filter) {
        selectedFrom = filter.getFrom();
        selectedTo = filter.getTo();
        if (selectedFrom != null) {
            binding.editDateFrom.setText(selectedFrom.format(DATE_DISPLAY));
        }
        if (selectedTo != null) {
            binding.editDateTo.setText(selectedTo.format(DATE_DISPLAY));
        }
    }

    /** Restore amount range. */
    private void restoreAmountRange(TransactionFilter filter) {
        if (filter.getMinAmountMinor() != TransactionFilter.NO_MIN_AMOUNT) {
            binding.editAmountMin.setText(
                    plainAmount(currency.minorFromLkr(filter.getMinAmountMinor())));
        }
        if (filter.getMaxAmountMinor() != TransactionFilter.NO_MAX_AMOUNT) {
            binding.editAmountMax.setText(
                    plainAmount(currency.minorFromLkr(filter.getMaxAmountMinor())));
        }
    }

    /** Applies the and dismiss. */
    private void applyAndDismiss() {
        Set<Category> categories = EnumSet.noneOf(Category.class);
        for (int i = 0; i < binding.chipGroupCategory.getChildCount(); i++) {
            Chip chip = (Chip) binding.chipGroupCategory.getChildAt(i);
            if (chip.isChecked()) {
                categories.add((Category) chip.getTag());
            }
        }

        Set<PaymentMethod> methods = EnumSet.noneOf(PaymentMethod.class);
        for (int i = 0; i < binding.chipGroupPayment.getChildCount(); i++) {
            Chip chip = (Chip) binding.chipGroupPayment.getChildAt(i);
            if (chip.isChecked()) {
                methods.add((PaymentMethod) chip.getTag());
            }
        }

        TransactionType type = null;
        if (binding.chipTypeIncome.isChecked()) {
            type = TransactionType.INCOME;
        } else if (binding.chipTypeExpense.isChecked()) {
            type = TransactionType.EXPENSE;
        }

        long min = parseBound(textOf(binding.editAmountMin), TransactionFilter.NO_MIN_AMOUNT, currency);
        long max = parseBound(textOf(binding.editAmountMax), TransactionFilter.NO_MAX_AMOUNT, currency);

        binding.layoutAmountMin.setError(min == INVALID_AMOUNT
                ? getString(R.string.tx_amount_invalid) : null);
        binding.layoutAmountMax.setError(max == INVALID_AMOUNT
                ? getString(R.string.tx_amount_invalid) : null);
        if (min == INVALID_AMOUNT || max == INVALID_AMOUNT) {
            return;
        }

        viewModel.applyFilters(categories, methods, type, selectedFrom, selectedTo, min, max);
        dismiss();
    }

    /** Reads the bound back out of text. */
    private static long parseBound(String raw, long blankFallback, Currency currency) {
        String cleaned = CurrencyFormatter.stripSymbol(raw, currency);
        if (cleaned.isEmpty()) {
            return blankFallback;
        }
        if (!cleaned.matches("\\d+(\\.\\d{1,2})?")) {
            return INVALID_AMOUNT;
        }
        try {
            java.math.BigDecimal major = new java.math.BigDecimal(cleaned);
            java.math.BigDecimal minorInCurrency = major.movePointRight(2)
                    .setScale(0, java.math.RoundingMode.HALF_UP);
            long lkrMinor = currency.minorToLkr(minorInCurrency.longValueExact());
            if (lkrMinor > CurrencyFormatter.MAX_AMOUNT_MINOR) {
                return INVALID_AMOUNT;
            }
            return lkrMinor;
        } catch (ArithmeticException | NumberFormatException e) {
            return INVALID_AMOUNT;
        }
    }

    /** Pick date. */
    private void pickDate(@Nullable LocalDate initial, DateChosen callback) {
        LocalDate start = initial == null ? LocalDate.now() : initial;
        DatePickerDialog dialog = new DatePickerDialog(
                requireContext(),
                (view, year, month, dayOfMonth) ->

                        callback.onDateChosen(LocalDate.of(year, month + 1, dayOfMonth)),
                start.getYear(),
                start.getMonthValue() - 1,
                start.getDayOfMonth());
        dialog.show();
    }

    /** Text of. */
    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    /** Plain amount. */
    private static String plainAmount(long minor) {
        return String.format(Locale.UK, "%d.%02d", minor / 100, Math.abs(minor % 100));
    }

    private interface DateChosen {
        void onDateChosen(LocalDate date);
    }

    /** Called by the framework as the fragment's views are torn down. */
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
