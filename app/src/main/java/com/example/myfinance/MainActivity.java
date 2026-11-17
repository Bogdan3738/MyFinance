package com.example.myfinance;

import android.app.DatePickerDialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final Locale RU = Locale.forLanguageTag("ru-RU");
    private static final int GOLD = 0xFFF1CD86;
    private static final int MUTED = 0xFFBDB6AB;
    private static final int DARK = 0xFF251807;
    private static final String[] INCOME = {"Зарплата", "Подработка", "Инвестиции", "Подарок", "Другое"};
    private static final String[] EXPENSE = {"Продукты", "Транспорт", "Кафе и рестораны", "Развлечения", "Другое"};

    private final Calendar operationDate = Calendar.getInstance();
    private OperationDatabase database;
    private boolean editing;
    private boolean income;
    private int categoryIndex = -1;
    private EditText amountInput;
    private OnBackPressedCallback closeForm;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        database = new OperationDatabase(this);
        WindowCompat.enableEdgeToEdge(getWindow());
        closeForm = new OnBackPressedCallback(false) {
            @Override
            public void handleOnBackPressed() {
                showHome();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, closeForm);

        if (state != null && state.getBoolean("editing")) {
            income = state.getBoolean("income");
            categoryIndex = state.getInt("category", -1);
            operationDate.setTimeInMillis(state.getLong("date"));
            showForm(state.getString("amount", ""));
        } else {
            showHome();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putBoolean("editing", editing);
        if (editing) {
            state.putBoolean("income", income);
            state.putInt("category", categoryIndex);
            state.putLong("date", operationDate.getTimeInMillis());
            state.putString("amount", amountInput.getText().toString());
        }
    }

    @Override
    protected void onDestroy() {
        database.close();
        super.onDestroy();
    }

    private void display(int layout, int rootId) {
        setContentView(layout);
        View root = findViewById(rootId);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets padding = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            view.setPadding(padding.left, padding.top, padding.right, padding.bottom);
            return insets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void showHome() {
        if (editing && amountInput != null) {
            getSystemService(InputMethodManager.class)
                    .hideSoftInputFromWindow(amountInput.getWindowToken(), 0);
        }
        editing = false;
        closeForm.setEnabled(false);
        display(R.layout.activity_main, R.id.main);
        findViewById(R.id.add_operation).setOnClickListener(v -> startOperation(true));
        findViewById(R.id.income_total_action).setOnClickListener(v -> startOperation(true));
        findViewById(R.id.expense_total_action).setOnClickListener(v -> startOperation(false));

        BigDecimal incomeTotal = BigDecimal.ZERO;
        BigDecimal expenseTotal = BigDecimal.ZERO;
        LinearLayout history = findViewById(R.id.operation_history);
        SimpleDateFormat dateFormat = new SimpleDateFormat("d MMM yyyy, HH:mm", RU);

        try (Cursor rows = database.history()) {
            while (rows.moveToNext()) {
                boolean isIncome = rows.getInt(0) == 1;
                BigDecimal amount = BigDecimal.valueOf(rows.getLong(2), 2);
                if (isIncome) {
                    incomeTotal = incomeTotal.add(amount);
                } else {
                    expenseTotal = expenseTotal.add(amount);
                }
                View row = getLayoutInflater().inflate(R.layout.item_operation, history, false);
                setText(row, R.id.operation_category, rows.getString(1));
                setText(row, R.id.operation_date, dateFormat.format(rows.getLong(3)));
                TextView amountView = row.findViewById(R.id.operation_amount);
                amountView.setText((isIncome ? "+" : "−") + money(amount));
                amountView.setTextColor(isIncome ? 0xFFA0D387 : 0xFFF3A193);
                history.addView(row);
            }
        }
        if (history.getChildCount() == 0) {
            TextView empty = new TextView(this);
            empty.setText("Записей пока нет");
            empty.setTextColor(MUTED);
            empty.setTextSize(15);
            history.addView(empty);
        }
        ((TextView) findViewById(R.id.income_total)).setText("Доходы  +" + money(incomeTotal));
        ((TextView) findViewById(R.id.expense_total)).setText("Расходы  −" + money(expenseTotal));
        ((TextView) findViewById(R.id.balance_total)).setText(money(incomeTotal.subtract(expenseTotal)));
    }

    private void startOperation(boolean isIncome) {
        income = isIncome;
        categoryIndex = -1;
        operationDate.setTimeInMillis(System.currentTimeMillis());
        showForm("");
    }

    private void showForm(String draftAmount) {
        editing = true;
        closeForm.setEnabled(true);
        display(R.layout.screen_add_operation, R.id.operation_form);
        amountInput = findViewById(R.id.amount_input);
        amountInput.setText(draftAmount);
        findViewById(R.id.income_type).setOnClickListener(v -> selectType(true));
        findViewById(R.id.expense_type).setOnClickListener(v -> selectType(false));
        findViewById(R.id.pick_operation_date).setOnClickListener(v -> showDatePicker());
        findViewById(R.id.save_operation).setOnClickListener(v -> saveOperation());
        updateDate();
        renderCategories();
    }

    private void selectType(boolean isIncome) {
        if (income != isIncome) {
            income = isIncome;
            categoryIndex = -1;
            renderCategories();
        }
    }

    private void renderCategories() {
        setTab(R.id.income_type, R.id.income_type_label, income);
        setTab(R.id.expense_type, R.id.expense_type_label, !income);
        ((TextView) findViewById(R.id.category_heading)).setText(
                income ? "Тип дохода" : "Тип расхода");
        LinearLayout categories = findViewById(R.id.category_list);
        categories.removeAllViews();
        String[] names = income ? INCOME : EXPENSE;

        for (int i = 0; i < names.length; i++) {
            int index = i;
            View row = getLayoutInflater().inflate(R.layout.item_operation_category, categories, false);
            setText(row, R.id.category_name, names[i]);
            setText(row, R.id.category_check, i == categoryIndex ? "✓" : "");
            row.setSelected(i == categoryIndex);
            row.setFocusable(true);
            row.setOnClickListener(v -> {
                categoryIndex = index;
                for (int position = 0; position < categories.getChildCount(); position++) {
                    View option = categories.getChildAt(position);
                    option.setSelected(position == index);
                    setText(option, R.id.category_check, position == index ? "✓" : "");
                }
            });
            categories.addView(row);
        }
    }

    private void setTab(int tabId, int textId, boolean selected) {
        View tab = findViewById(tabId);
        tab.setSelected(selected);
        if (selected) {
            tab.setBackgroundResource(R.drawable.mf_gold);
        } else {
            tab.setBackgroundColor(Color.TRANSPARENT);
        }
        ((TextView) findViewById(textId)).setTextColor(selected ? DARK : MUTED);
    }

    private void showDatePicker() {
        DatePickerDialog picker = new DatePickerDialog(this, (view, year, month, day) -> {
            operationDate.set(Calendar.DAY_OF_MONTH, 1);
            operationDate.set(year, month, day);
            updateDate();
        }, operationDate.get(Calendar.YEAR), operationDate.get(Calendar.MONTH),
                operationDate.get(Calendar.DAY_OF_MONTH));
        picker.show();
    }

    private void updateDate() {
        ((TextView) findViewById(R.id.operation_date_value)).setText(
                new SimpleDateFormat("d MMM yyyy", RU).format(operationDate.getTime()) + "  ›");
    }

    private void saveOperation() {
        String input = amountInput.getText().toString().trim().replace(',', '.');
        long kopecks;
        try {
            if (!input.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
                throw new NumberFormatException();
            }
            kopecks = new BigDecimal(input).movePointRight(2).longValueExact();
            if (kopecks <= 0) {
                throw new NumberFormatException();
            }
        } catch (ArithmeticException | NumberFormatException error) {
            amountInput.setError("Введите сумму больше нуля, до двух знаков после запятой");
            amountInput.requestFocus();
            return;
        }
        if (categoryIndex < 0) {
            Toast.makeText(this, "Выберите категорию", Toast.LENGTH_SHORT).show();
            return;
        }

        View save = findViewById(R.id.save_operation);
        save.setEnabled(false);
        try {
            database.add(income, (income ? INCOME : EXPENSE)[categoryIndex], kopecks,
                    operationDate.getTimeInMillis());
        } catch (android.database.SQLException error) {
            save.setEnabled(true);
            Toast.makeText(this, "Не удалось сохранить запись", Toast.LENGTH_SHORT).show();
            return;
        }
        showHome();
    }

    private static void setText(View parent, int id, String value) {
        ((TextView) parent.findViewById(id)).setText(value);
    }

    private static String money(BigDecimal amount) {
        NumberFormat format = NumberFormat.getNumberInstance(RU);
        format.setMinimumFractionDigits(amount.stripTrailingZeros().scale() > 0 ? 2 : 0);
        format.setMaximumFractionDigits(2);
        return format.format(amount) + " ₽";
    }

    private static final class OperationDatabase extends SQLiteOpenHelper {
        OperationDatabase(Context context) {
            super(context, "finance_operations.db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE operations (id INTEGER PRIMARY KEY, income INTEGER NOT NULL, "
                    + "category TEXT NOT NULL, kopecks INTEGER NOT NULL CHECK (kopecks > 0), "
                    + "operation_date INTEGER NOT NULL)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            throw new IllegalStateException("Missing migration for operations database");
        }

        void add(boolean income, String category, long kopecks, long date) {
            ContentValues values = new ContentValues();
            values.put("income", income ? 1 : 0);
            values.put("category", category);
            values.put("kopecks", kopecks);
            values.put("operation_date", date);
            getWritableDatabase().insertOrThrow("operations", null, values);
        }

        Cursor history() {
            return getReadableDatabase().query("operations",
                    new String[]{"income", "category", "kopecks", "operation_date"},
                    null, null, null, null, "operation_date DESC, id DESC");
        }
    }
}

