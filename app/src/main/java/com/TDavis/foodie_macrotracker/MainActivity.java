package com.TDavis.foodie_macrotracker;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.Collections;
import android.widget.ArrayAdapter;
import android.widget.Spinner;


public class MainActivity extends AppCompatActivity {

    // Inputs + UI
    EditText etFood, etCalories, etProtein, etCarbs, etFat;
    Button btnAdd, btnClear, btnSetGoals;
    TextView tvTotals, tvDate;

    // List
    RecyclerView rvEntries;
    ArrayList<FoodEntry> entries = new ArrayList<>();
    FoodEntryAdapter adapter;

    // Totals
    int totalCalories = 0, totalProtein = 0, totalCarbs = 0, totalFat = 0;

    // Goals (user-editable)
    int goalCal = 2000, goalPro = 150, goalCar = 250, goalFat = 70;

    // Progress UI
    ProgressBar pbCalories, pbProtein, pbCarbs, pbFat;
    TextView tvCalorieProgress, tvProteinProgress, tvCarbProgress, tvFatProgress;
    Spinner spMealType;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // -- Bind views --
        etFood     = findViewById(R.id.etFood);
        etCalories = findViewById(R.id.etCalories);
        etProtein  = findViewById(R.id.etProtein);
        etCarbs    = findViewById(R.id.etCarbs);
        etFat      = findViewById(R.id.etFat);
        btnAdd     = findViewById(R.id.btnAdd);
        btnClear   = findViewById(R.id.btnClear);
        btnSetGoals = findViewById(R.id.btnSetGoals);
        tvTotals   = findViewById(R.id.tvTotals);
        tvDate     = findViewById(R.id.tvDate);
        rvEntries  = findViewById(R.id.rvEntries);

        tvCalorieProgress = findViewById(R.id.tvCalorieProgress);
        tvProteinProgress = findViewById(R.id.tvProteinProgress);
        tvCarbProgress    = findViewById(R.id.tvCarbProgress);
        tvFatProgress     = findViewById(R.id.tvFatProgress);

        pbCalories = findViewById(R.id.pbCalories);
        pbProtein  = findViewById(R.id.pbProtein);
        pbCarbs    = findViewById(R.id.pbCarbs);
        pbFat      = findViewById(R.id.pbFat);

        spMealType = findViewById(R.id.spMealType);
        ArrayAdapter<CharSequence> mealAdapter = ArrayAdapter.createFromResource(
                this, R.array.meal_types, android.R.layout.simple_spinner_item);
        mealAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMealType.setAdapter(mealAdapter);

        // -- RecyclerView setup --
        adapter = new FoodEntryAdapter(entries);
        rvEntries.setLayoutManager(new LinearLayoutManager(this));
        rvEntries.setAdapter(adapter);

        // Tap to edit
        adapter.setOnItemClickListener(position -> showEditDialog(position));
        // Long-press to delete
        adapter.setOnItemLongClickListener(position -> confirmDelete(position));

        // -- Goals + progress bars --
        loadGoals();
        pbCalories.setMax(goalCal);
        pbProtein.setMax(goalPro);
        pbCarbs.setMax(goalCar);
        pbFat.setMax(goalFat);

        btnSetGoals.setOnClickListener(v -> showSetGoalsDialog());

        // -- Buttons/IME --
        btnClear.setOnClickListener(v -> clearAllData());
        btnAdd.setOnClickListener(v -> addEntry());
        etFat.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addEntry();
                return true;
            }
            return false;
        });

        // -- Load data + set date header --
        loadData();
        tvDate.setText("Entries for " + getTodayString());

        updateTotalsText();
        updateProgressUI();
    }

    // Add entry
    private void addEntry() {
        String name = etFood.getText().toString();
        int cal = parseInt(etCalories.getText().toString());
        int pro = parseInt(etProtein.getText().toString());
        int car = parseInt(etCarbs.getText().toString());
        int fat = parseInt(etFat.getText().toString());

        if (!validateInputs(name, cal, pro, car, fat)) return;

        String mealType = (String) spMealType.getSelectedItem();
        String today = getTodayString();
        FoodEntry entry = new FoodEntry(name, cal, pro, car, fat, today, mealType);
        entries.add(0, entry);              // newest first
        adapter.notifyItemInserted(0);
        rvEntries.scrollToPosition(0);


        totalCalories += cal;
        totalProtein  += pro;
        totalCarbs    += car;
        totalFat      += fat;

        updateTotalsText();
        saveData();
        updateProgressUI();

        // Reset inputs + focus
        etFood.setText("");
        etCalories.setText("");
        etProtein.setText("");
        etCarbs.setText("");
        etFat.setText("");
        hideKeyboard(etFat);
        etFood.requestFocus();
    }

    // Delete (with confirm)
    private void confirmDelete(int position) {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete entry?")
                .setMessage("Remove this food from today?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    FoodEntry e = entries.get(position);
                    totalCalories -= e.calories;
                    totalProtein  -= e.protein;
                    totalCarbs    -= e.carbs;
                    totalFat      -= e.fat;
                    entries.remove(position);
                    adapter.notifyItemRemoved(position);
                    updateTotalsText();
                    saveData();
                    updateProgressUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Edit dialog
    private void showEditDialog(int position) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_entry, null);

        EditText etEditName     = dialogView.findViewById(R.id.etEditName);
        EditText etEditCalories = dialogView.findViewById(R.id.etEditCalories);
        EditText etEditProtein  = dialogView.findViewById(R.id.etEditProtein);
        EditText etEditCarbs    = dialogView.findViewById(R.id.etEditCarbs);
        EditText etEditFat      = dialogView.findViewById(R.id.etEditFat);

        FoodEntry e = entries.get(position);

        etEditName.setText(e.name);
        etEditCalories.setText(String.valueOf(e.calories));
        etEditProtein.setText(String.valueOf(e.protein));
        etEditCarbs.setText(String.valueOf(e.carbs));
        etEditFat.setText(String.valueOf(e.fat));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Edit entry")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    int oldCal = e.calories, oldPro = e.protein, oldCar = e.carbs, oldFat = e.fat;

                    String newName = etEditName.getText().toString().trim();
                    int newCal = parseInt(etEditCalories.getText().toString());
                    int newPro = parseInt(etEditProtein.getText().toString());
                    int newCar = parseInt(etEditCarbs.getText().toString());
                    int newFat = parseInt(etEditFat.getText().toString());

                    // Basic validation for dialog fields
                    if (newName.isEmpty()) { toast("Please enter a food name."); return; }
                    if (newCal < 0 || newPro < 0 || newCar < 0 || newFat < 0) { toast("Values cannot be negative."); return; }
                    if (newCal == 0 && newPro == 0 && newCar == 0 && newFat == 0) { toast("Enter at least one non-zero value."); return; }
                    if (newCal > 5000 || newPro > 1000 || newCar > 1000 || newFat > 1000) { toast("One or more values look too large."); return; }

                    e.name = newName.isEmpty() ? e.name : newName;
                    e.calories = newCal;
                    e.protein  = newPro;
                    e.carbs    = newCar;
                    e.fat      = newFat;

                    totalCalories += (newCal - oldCal);
                    totalProtein  += (newPro - oldPro);
                    totalCarbs    += (newCar - oldCar);
                    totalFat      += (newFat - oldFat);

                    adapter.notifyItemChanged(position);
                    updateTotalsText();
                    saveData();
                    updateProgressUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Persistence
    private void saveData() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();

        String json = gson.toJson(entries);
        editor.putString("entries", json);
        editor.putInt("totalCalories", totalCalories);
        editor.putInt("totalProtein", totalProtein);
        editor.putInt("totalCarbs", totalCarbs);
        editor.putInt("totalFat", totalFat);
        editor.putString("lastSavedDate", getTodayString());
        editor.apply();
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        Gson gson = new Gson();

        String lastSavedDate = prefs.getString("lastSavedDate", "");
        String today = getTodayString();

        if (!today.equals(lastSavedDate)) {
            entries.clear();
            totalCalories = totalProtein = totalCarbs = totalFat = 0;
            saveData();
        } else {
            String json = prefs.getString("entries", null);
            Type type = new TypeToken<ArrayList<FoodEntry>>(){}.getType();
            ArrayList<FoodEntry> savedEntries = gson.fromJson(json, type);

            if (savedEntries != null) {
                entries.clear();
                entries.addAll(savedEntries);
                // Sort saved entries: newest first (entries with 0 timestamp treated as oldest)
                Collections.sort(entries, (a, b) -> Long.compare(
                        b.createdAt, a.createdAt
                ));

                totalCalories = prefs.getInt("totalCalories", 0);
                totalProtein  = prefs.getInt("totalProtein", 0);
                totalCarbs    = prefs.getInt("totalCarbs", 0);
                totalFat      = prefs.getInt("totalFat", 0);
                adapter.notifyDataSetChanged();
            }
        }
    }

    // Goals (save/load + dialog)
    private void saveGoals() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        prefs.edit()
                .putInt("goalCal", goalCal)
                .putInt("goalPro", goalPro)
                .putInt("goalCar", goalCar)
                .putInt("goalFat", goalFat)
                .apply();
    }

    private void loadGoals() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        goalCal = prefs.getInt("goalCal", goalCal);
        goalPro = prefs.getInt("goalPro", goalPro);
        goalCar = prefs.getInt("goalCar", goalCar);
        goalFat = prefs.getInt("goalFat", goalFat);
    }

    private void showSetGoalsDialog() {
        View dlg = getLayoutInflater().inflate(R.layout.dialog_set_goals, null);

        EditText etGoalCal = dlg.findViewById(R.id.etGoalCal);
        EditText etGoalPro = dlg.findViewById(R.id.etGoalPro);
        EditText etGoalCar = dlg.findViewById(R.id.etGoalCar);
        EditText etGoalFat = dlg.findViewById(R.id.etGoalFat);

        etGoalCal.setText(String.valueOf(goalCal));
        etGoalPro.setText(String.valueOf(goalPro));
        etGoalCar.setText(String.valueOf(goalCar));
        etGoalFat.setText(String.valueOf(goalFat));

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Set Daily Goals")
                .setView(dlg)
                .setPositiveButton("Save", (d, w) -> {
                    int newCal = Math.max(1, parseInt(etGoalCal.getText().toString()));
                    int newPro = Math.max(1, parseInt(etGoalPro.getText().toString()));
                    int newCar = Math.max(1, parseInt(etGoalCar.getText().toString()));
                    int newFat = Math.max(1, parseInt(etGoalFat.getText().toString()));

                    goalCal = newCal; goalPro = newPro; goalCar = newCar; goalFat = newFat;

                    pbCalories.setMax(goalCal);
                    pbProtein.setMax(goalPro);
                    pbCarbs.setMax(goalCar);
                    pbFat.setMax(goalFat);

                    saveGoals();
                    updateProgressUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // UI helpers
    private void updateTotalsText() {
        tvTotals.setText("Totals: " + totalCalories + " kcal • P" + totalProtein + "/C" + totalCarbs + "/F" + totalFat + " g");
    }

    private void updateProgressUI() {
        int cal = Math.min(totalCalories, goalCal);
        int pro = Math.min(totalProtein,  goalPro);
        int car = Math.min(totalCarbs,    goalCar);
        int fat = Math.min(totalFat,      goalFat);

        pbCalories.setProgress(cal);
        pbProtein.setProgress(pro);
        pbCarbs.setProgress(car);
        pbFat.setProgress(fat);

        int pCal = (int)Math.round(100.0 * totalCalories / Math.max(goalCal, 1));
        int pPro = (int)Math.round(100.0 * totalProtein  / Math.max(goalPro, 1));
        int pCar = (int)Math.round(100.0 * totalCarbs    / Math.max(goalCar, 1));
        int pFat = (int)Math.round(100.0 * totalFat      / Math.max(goalFat, 1));

        tvCalorieProgress.setText("Calories: " + totalCalories + " / " + goalCal + " (" + pCal + "%)");
        tvProteinProgress.setText("Protein: "  + totalProtein  + " / " + goalPro + " g (" + pPro + "%)");
        tvCarbProgress.setText("Carbs: "      + totalCarbs    + " / " + goalCar + " g (" + pCar + "%)");
        tvFatProgress.setText("Fat: "         + totalFat      + " / " + goalFat + " g (" + pFat + "%)");
    }

    private void clearAllData() {
        entries.clear();
        adapter.notifyDataSetChanged();
        totalCalories = totalProtein = totalCarbs = totalFat = 0;
        updateTotalsText();

        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();

        updateProgressUI();
    }

    // Utils
    private int parseInt(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; }
    }

    private void toast(String msg) {
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private String getTodayString() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return java.time.LocalDate.now().toString();
        } else {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        }
    }

    // Validates name and macro inputs before adding/editing an entry
    private boolean validateInputs(String name, int cal, int pro, int car, int fat) {
        // Require a non-empty name
        if (name == null || name.trim().isEmpty()) {
            etFood.setError("Enter a food name");
            toast("Please enter a food name.");
            return false;
        }
        // No negative numbers
        if (cal < 0 || pro < 0 || car < 0 || fat < 0) {
            toast("Values cannot be negative.");
            return false;
        }
        // Avoid all zeros
        if (cal == 0 && pro == 0 && car == 0 && fat == 0) {
            toast("Enter at least one non-zero value.");
            return false;
        }
        // Simple sanity caps
        if (cal > 5000 || pro > 1000 || car > 1000 || fat > 1000) {
            toast("One or more values look too large.");
            return false;
        }
        return true;
    }

}
