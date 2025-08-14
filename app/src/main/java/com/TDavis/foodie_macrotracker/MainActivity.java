package com.TDavis.foodie_macrotracker;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.content.SharedPreferences;

import com.google.firebase.crashlytics.buildtools.reloc.com.google.common.reflect.TypeToken; // Used for generic type handling with Gson
import com.google.gson.Gson; // Library for converting objects <-> JSON

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;

/**
 * MainActivity is the main screen of the Foodie-MacroTracker app.
 * It allows the user to add food entries with calories and macros,
 * view daily totals, save them locally, reset them, and auto-reset each day.
 */
public class MainActivity extends AppCompatActivity {

    // UI elements for entering and displaying data
    EditText etFood, etCalories, etProtein, etCarbs, etFat;
    Button btnAdd, btnClear;
    TextView tvTotals, tvDate;
    RecyclerView rvEntries;

    // Stores all food entries for the current day
    ArrayList<FoodEntry> entries = new ArrayList<>();
    FoodEntryAdapter adapter;

    // Running totals for calories, protein, carbs, and fat
    int totalCalories = 0, totalProtein = 0, totalCarbs = 0, totalFat = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Link Java variables to the XML views
        etFood = findViewById(R.id.etFood);
        etCalories = findViewById(R.id.etCalories);
        etProtein = findViewById(R.id.etProtein);
        etCarbs = findViewById(R.id.etCarbs);
        etFat = findViewById(R.id.etFat);
        btnAdd = findViewById(R.id.btnAdd);
        btnClear = findViewById(R.id.btnClear);
        tvTotals = findViewById(R.id.tvTotals);
        tvDate = findViewById(R.id.tvDate);
        rvEntries = findViewById(R.id.rvEntries);

        // Handle "Clear All Data" button click
        btnClear.setOnClickListener(v -> clearAllData());

        // Setup RecyclerView for displaying entries
        adapter = new FoodEntryAdapter(entries);
        rvEntries.setLayoutManager(new LinearLayoutManager(this));
        rvEntries.setAdapter(adapter);

        // Load saved data from previous sessions (only if it's the same day)
        loadData();

        // Show today's date at the top
        String today;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            today = java.time.LocalDate.now().toString();
        } else {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
            today = sdf.format(new java.util.Date());
        }
        tvDate.setText("Entries for " + today);

        // Update totals text on screen
        updateTotalsText();

        // Handle "Add Entry" button click
        btnAdd.setOnClickListener(v -> addEntry());
    }

    /**
     * Adds a new food entry to the list and updates totals.
     */
    private void addEntry() {
        // Read values from input fields
        String name = etFood.getText().toString();
        int cal = parseInt(etCalories.getText().toString());
        int pro = parseInt(etProtein.getText().toString());
        int car = parseInt(etCarbs.getText().toString());
        int fat = parseInt(etFat.getText().toString());

        // Get today's date (only for API 26+ here — could be extended for older devices)
        String today = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            today = LocalDate.now().toString();
        }

        // Create and add the new entry
        FoodEntry entry = new FoodEntry(name, cal, pro, car, fat, today);
        entries.add(entry);
        adapter.notifyDataSetChanged();

        // Update totals
        totalCalories += cal;
        totalProtein += pro;
        totalCarbs += car;
        totalFat += fat;
        tvTotals.setText("Totals: " + totalCalories + " kcal • P" + totalProtein + "/C" + totalCarbs + "/F" + totalFat + " g");

        // Clear input fields for the next entry
        etFood.setText("");
        etCalories.setText("");
        etProtein.setText("");
        etCarbs.setText("");
        etFat.setText("");

        // Save updated data
        saveData();
    }

    /**
     * Safely parse a string into an int, returning 0 if invalid.
     */
    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Saves the current list of entries and totals to SharedPreferences.
     */
    private void saveData() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        Gson gson = new Gson();

        // Convert entries list to JSON
        String json = gson.toJson(entries);
        editor.putString("entries", json);

        // Save totals
        editor.putInt("totalCalories", totalCalories);
        editor.putInt("totalProtein", totalProtein);
        editor.putInt("totalCarbs", totalCarbs);
        editor.putInt("totalFat", totalFat);

        // Save the date
        String today = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            today = LocalDate.now().toString();
        }
        editor.putString("lastSavedDate", today);

        editor.apply(); // Commit changes
    }

    /**
     * Loads saved data from SharedPreferences if it's still the same day.
     * If it's a new day, it clears everything and starts fresh.
     */
    private void loadData() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        Gson gson = new Gson();

        String lastSavedDate = prefs.getString("lastSavedDate", "");
        String today = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            today = LocalDate.now().toString();
        }

        // If new day, reset everything
        if (!today.equals(lastSavedDate)) {
            entries.clear();
            totalCalories = totalProtein = totalCarbs = totalFat = 0;
            saveData(); // Save with new date
        } else {
            // Load existing entries for the current day
            String json = prefs.getString("entries", null);
            Type type = new TypeToken<ArrayList<FoodEntry>>() {}.getType();
            ArrayList<FoodEntry> savedEntries = gson.fromJson(json, type);

            if (savedEntries != null) {
                entries.clear();
                entries.addAll(savedEntries);

                // Load totals
                totalCalories = prefs.getInt("totalCalories", 0);
                totalProtein = prefs.getInt("totalProtein", 0);
                totalCarbs = prefs.getInt("totalCarbs", 0);
                totalFat = prefs.getInt("totalFat", 0);

                adapter.notifyDataSetChanged();
            }
        }
    }

    /**
     * Updates the totals display TextView with the latest values.
     */
    private void updateTotalsText() {
        tvTotals.setText("Totals: " + totalCalories + " kcal • P" + totalProtein + "/C" + totalCarbs + "/F" + totalFat + " g");
    }

    /**
     * Clears all data (entries + totals) and wipes from SharedPreferences.
     */
    private void clearAllData() {
        entries.clear();
        adapter.notifyDataSetChanged();

        totalCalories = totalProtein = totalCarbs = totalFat = 0;
        updateTotalsText();

        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
