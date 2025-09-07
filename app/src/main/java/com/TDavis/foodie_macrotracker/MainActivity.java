package com.TDavis.foodie_macrotracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import com.google.mlkit.vision.codescanner.GmsBarcodeScanner;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;

import com.TDavis.foodie_macrotracker.net.RetroFitProvider;
import com.TDavis.foodie_macrotracker.net.UsdaProxyService;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    // Inputs + UI
    EditText etFood, etCalories, etProtein, etCarbs, etFat;
    Button btnAdd, btnClear, btnSettings;
    TextView tvTotals, tvDate;

    // Meal type spinner
    Spinner spMealType;

    // List + sectioned adapter
    RecyclerView rvEntries;
    ArrayList<FoodEntry> entries = new ArrayList<>();
    SectionedEntryAdapter adapter;

    // Totals
    int totalCalories = 0, totalProtein = 0, totalCarbs = 0, totalFat = 0;

    // Goals (user-editable)
    int goalCal = 2000, goalPro = 150, goalCar = 250, goalFat = 70;

    // Progress UI
    ProgressBar pbCalories, pbProtein, pbCarbs, pbFat;
    TextView tvCalorieProgress, tvProteinProgress, tvCarbProgress, tvFatProgress;
    Button btnPrevDay, btnNextDay;
    String currentDate; // yyyy-MM-dd we’re viewing
    ArrayList<FoodEntry> displayEntries = new ArrayList<>(); // what the adapter shows (today or history)
    boolean searchMode = false;

    // Scanner
    GmsBarcodeScanner barcodeScanner;

    // Collapsible nutrition card
    private View nutritionCard, nutritionHeader, nutritionContent;
    private ImageView ivChevron;

    // Serving scaling UI
    private MaterialAutoCompleteTextView actvUnit;
    private TextInputEditText etQuantity;

    // === Scaling state (per normalized proxy) ===
    private ArrayList<Unit> unitList = new ArrayList<>();
    private Unit selectedUnit;
    private Per100g basePer100g = new Per100g();

    // === Models matching normalized proxy (keep here for simplicity) ===
    public static class Unit { public String label; public double gramsPerUnit; }
    public static class Per100g { public Integer calories; public Double protein, carbs, fat; }
    public static class PerServing { public Integer calories; public Double protein, carbs, fat; public Double grams; }
    public static class Servings { public Per100g per100g; public PerServing perServing; }
    public static class NormalizedFoodItem {
        public long fdcId;
        public String description;
        public String brandName;
        public Servings servings;
        public ArrayList<Unit> units;
    }
    public static class FoodSearchResponseV2 {
        public int totalHits;
        public int pageNumber;
        public ArrayList<NormalizedFoodItem> items;
    }
    public static class BarcodeLookupResponse {
        public NormalizedFoodItem item;
    }

    // ---- small utils ----
    private static double parseD(CharSequence s, double fb){ try { return Double.parseDouble(String.valueOf(s).trim()); } catch(Exception e){ return fb; } }
    private static int r0(double v){ return (int)Math.round(v); }
    private static double r1(double v){ return Math.round(v*10.0)/10.0; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme BEFORE setContentView
        applySavedTheme();
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
        btnSettings= findViewById(R.id.btnSettings);
        tvTotals   = findViewById(R.id.tvTotals);
        tvDate     = findViewById(R.id.tvDate);
        spMealType = findViewById(R.id.spMealType);

        tvCalorieProgress = findViewById(R.id.tvCalorieProgress);
        tvProteinProgress = findViewById(R.id.tvProteinProgress);
        tvCarbProgress    = findViewById(R.id.tvCarbProgress);
        tvFatProgress     = findViewById(R.id.tvFatProgress);

        pbCalories = findViewById(R.id.pbCalories);
        pbProtein  = findViewById(R.id.pbProtein);
        pbCarbs    = findViewById(R.id.pbCarbs);
        pbFat      = findViewById(R.id.pbFat);

        btnPrevDay = findViewById(R.id.btnPrevDay);
        btnNextDay = findViewById(R.id.btnNextDay);

        Button btnScan = findViewById(R.id.btnScan);

        nutritionCard    = findViewById(R.id.nutritionCard);
        nutritionHeader  = findViewById(R.id.nutritionHeader);
        nutritionContent = findViewById(R.id.nutritionContent);
        ivChevron        = findViewById(R.id.ivChevron);

        etQuantity = findViewById(R.id.etQuantity);
        actvUnit   = findViewById(R.id.actvUnit);

        // Collapse/expand
        nutritionHeader.setOnClickListener(v -> {
            boolean expand = nutritionContent.getVisibility() != View.VISIBLE;
            nutritionContent.setVisibility(expand ? View.VISIBLE : View.GONE);
            ivChevron.setRotation(expand ? 180f : 0f);
        });

        // Scanner setup (UPC/EAN)
        GmsBarcodeScannerOptions opts = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                        Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E,
                        Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8)
                .enableAutoZoom()
                .build();
        barcodeScanner = GmsBarcodeScanning.getClient(this, opts);

        // Click -> scan -> barcode lookup
        btnScan.setOnClickListener(v -> {
            if (!currentDate.equals(getTodayString())) { toast("Switch to Today to scan."); return; }
            barcodeScanner.startScan()
                    .addOnSuccessListener(b -> {
                        String code = b.getRawValue();
                        if (code == null || code.trim().isEmpty()) { toast("No code read."); return; }
                        etFood.setText(code); // temporary
                        etCalories.setText(""); etProtein.setText(""); etCarbs.setText(""); etFat.setText("");
                        searchByBarcode(code.trim());
                    })
                    .addOnFailureListener(e -> toast("Scan cancelled"));
        });

        // Inputs watcher to toggle Search/Add label
        TextWatcher watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateAddButtonLabel(); }
            @Override public void afterTextChanged(Editable s) {}
        };
        etFood.addTextChangedListener(watcher);
        etCalories.addTextChangedListener(watcher);
        etProtein.addTextChangedListener(watcher);
        etCarbs.addTextChangedListener(watcher);
        etFat.addTextChangedListener(watcher);
        updateAddButtonLabel();

        // Meal type spinner data
        ArrayAdapter<CharSequence> mealAdapter = ArrayAdapter.createFromResource(
                this, R.array.meal_types, android.R.layout.simple_spinner_item);
        mealAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spMealType.setAdapter(mealAdapter);

        // RecyclerView
        rvEntries  = findViewById(R.id.rvEntries);
        adapter    = new SectionedEntryAdapter();
        rvEntries.setLayoutManager(new LinearLayoutManager(this));
        rvEntries.setAdapter(adapter);

        adapter.setOnItemClickListener(this::showEditDialog);
        adapter.setOnItemLongClickListener(this::confirmDelete);

        // Buttons/IME
        btnClear.setOnClickListener(v -> clearAllData());
        btnAdd.setOnClickListener(v -> { if (searchMode) searchAndPopulate(); else addEntry(); });
        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        etFat.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { addEntry(); return true; }
            return false;
        });

        // Goals + progress
        loadGoals();
        pbCalories.setMax(goalCal);
        pbProtein.setMax(goalPro);
        pbCarbs.setMax(goalCar);
        pbFat.setMax(goalFat);

        // Data + date header
        loadData();
        currentDate = getTodayString();
        refreshForDate(currentDate);

        btnPrevDay.setOnClickListener(v -> {
            currentDate = shiftDateString(currentDate, -1);
            refreshForDate(currentDate);
        });
        btnNextDay.setOnClickListener(v -> {
            currentDate = shiftDateString(currentDate, +1);
            refreshForDate(currentDate);
        });

        // === Serving scaling UI (default gram/oz + listeners) ===
        setupScalingUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        applySavedTheme();
        loadGoals();
        pbCalories.setMax(goalCal);
        pbProtein.setMax(goalPro);
        pbCarbs.setMax(goalCar);
        pbFat.setMax(goalFat);
        updateProgressUI();
        adapter.setData(entries);
    }

    // THEME
    private void applySavedTheme() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        int mode = prefs.getInt("themeMode", 0); // 0=System, 1=Light, 2=Dark
        int m = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (mode == 1) m = AppCompatDelegate.MODE_NIGHT_NO;
        else if (mode == 2) m = AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(m);
    }

    // Add entry
    private void addEntry() {
        String name = etFood.getText().toString();
        int cal = parseInt(etCalories.getText().toString());
        int pro = parseInt(etProtein.getText().toString());
        int car = parseInt(etCarbs.getText().toString());
        int fat = parseInt(etFat.getText().toString());

        if (!validateInputs(name, cal, pro, car, fat)) return;

        String today = getTodayString();
        String mealType = (String) spMealType.getSelectedItem();

        FoodEntry entry = new FoodEntry(name, cal, pro, car, fat, today, mealType);
        entries.add(0, entry);                 // newest logical first
        totalCalories += cal;
        totalProtein  += pro;
        totalCarbs    += car;
        totalFat      += fat;

        adapter.setData(entries);              // rebuild sections
        rvEntries.scrollToPosition(0);
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
    private void confirmDelete(FoodEntry e) {
        if (!currentDate.equals(getTodayString())) {
            toast("Switch to Today to edit entries.");
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete entry?")
                .setMessage("Remove this food from today?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    totalCalories -= e.calories;
                    totalProtein  -= e.protein;
                    totalCarbs    -= e.carbs;
                    totalFat      -= e.fat;
                    entries.remove(e);

                    adapter.setData(entries);
                    updateTotalsText();
                    saveData();
                    updateProgressUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // Edit dialog
    private void showEditDialog(FoodEntry e) {
        if (!currentDate.equals(getTodayString())) {
            toast("Switch to Today to edit entries.");
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_entry, null);

        EditText etEditName     = dialogView.findViewById(R.id.etEditName);
        EditText etEditCalories = dialogView.findViewById(R.id.etEditCalories);
        EditText etEditProtein  = dialogView.findViewById(R.id.etEditProtein);
        EditText etEditCarbs    = dialogView.findViewById(R.id.etEditCarbs);
        EditText etEditFat      = dialogView.findViewById(R.id.etEditFat);

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

                    adapter.setData(entries);
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
        String today = getTodayString();
        editor.putString("lastSavedDate", today);
        editor.apply();

        // Mirror into "history" map keyed by date
        java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<java.util.HashMap<String, java.util.ArrayList<FoodEntry>>>(){}.getType();
        String hJson = prefs.getString("history", null);
        java.util.HashMap<String, java.util.ArrayList<FoodEntry>> history =
                (hJson == null ? new java.util.HashMap<>() : gson.fromJson(hJson, mapType));
        history.put(today, new java.util.ArrayList<>(entries));
        prefs.edit().putString("history", gson.toJson(history)).apply();
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
                totalCalories = prefs.getInt("totalCalories", 0);
                totalProtein  = prefs.getInt("totalProtein", 0);
                totalCarbs    = prefs.getInt("totalCarbs", 0);
                totalFat      = prefs.getInt("totalFat", 0);
            }
        }
    }

    // Goals (used by progress)
    private void loadGoals() {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        goalCal = prefs.getInt("goalCal", goalCal);
        goalPro = prefs.getInt("goalPro", goalPro);
        goalCar = prefs.getInt("goalCar", goalCar);
        goalFat = prefs.getInt("goalFat", goalFat);
    }

    // UI helpers
    private void updateTotalsText() {
        tvTotals.setText("Totals: " + totalCalories + " kcal • P" + totalProtein + "/C" + totalCarbs + "/F" + totalFat + " g");
    }

    private void updateProgressUI() {
        pbCalories.setProgress(Math.min(totalCalories, goalCal));
        pbProtein.setProgress(Math.min(totalProtein,  goalPro));
        pbCarbs.setProgress(Math.min(totalCarbs,    goalCar));
        pbFat.setProgress(Math.min(totalFat,      goalFat));

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
        totalCalories = totalProtein = totalCarbs = totalFat = 0;
        adapter.setData(entries);
        updateTotalsText();

        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        prefs.edit().remove("entries")
                .putInt("totalCalories", 0)
                .putInt("totalProtein", 0)
                .putInt("totalCarbs", 0)
                .putInt("totalFat", 0)
                .putString("lastSavedDate", getTodayString())
                .apply();

        updateProgressUI();
    }

    // Utils
    private int parseInt(String s) { try { return Integer.parseInt(s); } catch (NumberFormatException e) { return 0; } }
    private boolean validateInputs(String name, int cal, int pro, int car, int fat) {
        if (name == null || name.trim().isEmpty()) { etFood.setError("Enter a food name"); toast("Please enter a food name."); return false; }
        if (cal < 0 || pro < 0 || car < 0 || fat < 0) { toast("Values cannot be negative."); return false; }
        if (cal == 0 && pro == 0 && car == 0 && fat == 0) { toast("Enter at least one non-zero value."); return false; }
        if (cal > 5000 || pro > 1000 || car > 1000 || fat > 1000) { toast("One or more values look too large."); return false; }
        return true;
    }
    private void toast(String msg) { android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show(); }
    private void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && view != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }
    private String getTodayString() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) return java.time.LocalDate.now().toString();
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
    }

    // Refresh UI for a given date
    private void refreshForDate(String date) {
        tvDate.setText("Entries for " + date);

        boolean isToday = date.equals(getTodayString());

        // Load display list
        if (isToday) {
            displayEntries = entries; // live list
        } else {
            displayEntries = loadEntriesFor(date); // history snapshot or empty
        }

        // Rebuild sections
        adapter.setData(displayEntries);

        // Compute local totals for this day
        int cal=0, pro=0, car=0, fat=0;
        for (FoodEntry e : displayEntries) {
            cal += e.calories; pro += e.protein; car += e.carbs; fat += e.fat;
        }
        // Show totals for that day
        tvTotals.setText("Totals: " + cal + " kcal • P" + pro + "/C" + car + "/F" + fat + " g");

        // Update progress bars against current goals
        pbCalories.setProgress(Math.min(cal, goalCal));
        pbProtein.setProgress(Math.min(pro, goalPro));
        pbCarbs.setProgress(Math.min(car, goalCar));
        pbFat.setProgress(Math.min(fat, goalFat));

        int pCal = (int)Math.round(100.0 * cal / Math.max(goalCal, 1));
        int pPro = (int)Math.round(100.0 * pro / Math.max(goalPro, 1));
        int pCar = (int)Math.round(100.0 * car / Math.max(goalCar, 1));
        int pFat = (int)Math.round(100.0 * fat / Math.max(goalFat, 1));

        tvCalorieProgress.setText("Calories: " + cal + " / " + goalCal + " (" + pCal + "%)");
        tvProteinProgress.setText("Protein: "  + pro + " / " + goalPro + " g (" + pPro + "%)");
        tvCarbProgress.setText("Carbs: "      + car + " / " + goalCar + " g (" + pCar + "%)");
        tvFatProgress.setText("Fat: "         + fat + " / " + goalFat + " g (" + pFat + "%)");

        // Enable edits only for today
        btnAdd.setEnabled(isToday);
        btnClear.setEnabled(isToday);
        float alpha = isToday ? 1f : 0.5f;
        btnAdd.setAlpha(alpha);
        btnClear.setAlpha(alpha);

        // Next arrow disabled when at today (no future)
        btnNextDay.setEnabled(!isToday);
    }

    // Load entries for a specific date from history map
    private java.util.ArrayList<FoodEntry> loadEntriesFor(String date) {
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        String hJson = prefs.getString("history", null);
        if (hJson == null) return new java.util.ArrayList<>();
        Gson gson = new Gson();
        java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<java.util.HashMap<String, java.util.ArrayList<FoodEntry>>>(){}.getType();
        java.util.HashMap<String, java.util.ArrayList<FoodEntry>> history = gson.fromJson(hJson, mapType);
        java.util.ArrayList<FoodEntry> list = history.get(date);
        return (list == null) ? new java.util.ArrayList<>() : list;
    }

    // Shift a yyyy-MM-dd string by +/- days
    private String shiftDateString(String yyyyMmDd, int days) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                java.time.LocalDate d = java.time.LocalDate.parse(yyyyMmDd);
                return d.plusDays(days).toString();
            } else {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault());
                java.util.Date d = sdf.parse(yyyyMmDd);
                java.util.Calendar c = java.util.Calendar.getInstance();
                c.setTime(d);
                c.add(java.util.Calendar.DAY_OF_YEAR, days);
                return sdf.format(c.getTime());
            }
        } catch (Exception e) {
            return getTodayString(); // fallback
        }
    }

    private boolean isEmpty(EditText e){ return e.getText()==null || e.getText().toString().trim().isEmpty(); }

    private void updateAddButtonLabel() {
        String name = etFood.getText()==null ? "" : etFood.getText().toString().trim();
        boolean noMacros = isEmpty(etCalories) && isEmpty(etProtein) && isEmpty(etCarbs) && isEmpty(etFat);
        searchMode = (!name.isEmpty() && noMacros);
        btnAdd.setText(searchMode ? "Search" : "Add Entry");
    }

    /* ==============================  SCALING UI  ============================== */

    private void setupScalingUi() {
        // Default units before any result arrives
        unitList.clear();
        Unit g = new Unit(); g.label="gram (g)"; g.gramsPerUnit=1.0; unitList.add(g);
        Unit oz = new Unit(); oz.label="ounce (oz)"; oz.gramsPerUnit=28.3495; unitList.add(oz);
        setUnitsAdapterAndSelect(0);

        if (etQuantity.getText()==null || etQuantity.getText().length()==0) etQuantity.setText("1");

        etQuantity.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void onTextChanged(CharSequence s,int a,int b,int c){}
            @Override public void afterTextChanged(Editable s){ recomputeAndRender(); }
        });

        actvUnit.setOnItemClickListener((parent, view, position, id) -> {
            selectedUnit = unitList.get(position);
            recomputeAndRender();
        });
    }

    private void setUnitsAdapterAndSelect(int index) {
        ArrayList<String> labels = new ArrayList<>();
        for (Unit u : unitList) labels.add(u.label);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels);
        actvUnit.setAdapter(ad);
        index = Math.max(0, Math.min(index, unitList.size()-1));
        selectedUnit = unitList.get(index);
        actvUnit.setText(selectedUnit.label, false);
    }

    private void recomputeAndRender() {
        if (selectedUnit == null || basePer100g == null) return;

        double qty = parseD(etQuantity.getText()==null? "" : etQuantity.getText(), 1.0);
        if (qty <= 0) qty = 1.0;
        double grams = qty * selectedUnit.gramsPerUnit;

        Double cal100 = (basePer100g.calories==null? null : basePer100g.calories.doubleValue());
        Double pro100 = basePer100g.protein, carb100 = basePer100g.carbs, fat100 = basePer100g.fat;

        etCalories.setText(cal100==null? "" : String.valueOf(r0(cal100 * grams / 100.0)));
        etProtein .setText(pro100==null? "" : String.valueOf(r1(pro100 * grams / 100.0)));
        etCarbs   .setText(carb100==null? "" : String.valueOf(r1(carb100 * grams / 100.0)));
        etFat     .setText(fat100==null? "" : String.valueOf(r1(fat100 * grams / 100.0)));
    }

    /* ==============================  SEARCH  ============================== */

    private void searchAndPopulate() {
        if (!currentDate.equals(getTodayString())) {
            toast("Switch to Today to search/add.");
            return;
        }

        String query = etFood.getText() == null ? "" : etFood.getText().toString().trim();
        if (query.isEmpty()) { toast("Enter a food name to search."); return; }

        btnAdd.setEnabled(false);
        btnAdd.setText("Searching…");

        UsdaProxyService api = RetroFitProvider.get();
        api.searchFoodsNormalized(query, 10, 1).enqueue(new Callback<FoodSearchResponseV2>() {
            @Override
            public void onResponse(Call<FoodSearchResponseV2> call, Response<FoodSearchResponseV2> resp) {
                btnAdd.setEnabled(true);

                if (!resp.isSuccessful() || resp.body() == null || resp.body().items == null || resp.body().items.isEmpty()) {
                    btnAdd.setText("Search");
                    toast("No matches found.");
                    updateAddButtonLabel();
                    return;
                }

                ArrayList<NormalizedFoodItem> list = resp.body().items;

                if (list.size() > 1) {
                    ArrayList<NormalizedFoodItem> show = new ArrayList<>(list.subList(0, Math.min(10, list.size())));
                    ArrayList<String> labels = new ArrayList<>();
                    for (NormalizedFoodItem f : show) {
                        String brand = (f.brandName == null || f.brandName.isEmpty()) ? "" : " • " + f.brandName;
                        String serv = (f.servings!=null && f.servings.perServing!=null && f.servings.perServing.grams!=null)
                                ? (" • " + r0(f.servings.perServing.grams) + "g serving") : "";
                        String kcal100 = (f.servings!=null && f.servings.per100g!=null && f.servings.per100g.calories!=null)
                                ? (" — " + f.servings.per100g.calories + " kcal/100g") : "";
                        labels.add(f.description + brand + serv + kcal100);
                    }

                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                            .setTitle("Pick a match")
                            .setItems(labels.toArray(new String[0]), (d, which) -> {
                                NormalizedFoodItem best = show.get(which);
                                applyChosenFood(best);
                            })
                            .setNegativeButton("Cancel", (d, w) -> {
                                btnAdd.setText("Search");
                                updateAddButtonLabel();
                            })
                            .show();
                    return;
                }

                // Single result
                applyChosenFood(list.get(0));
            }

            @Override
            public void onFailure(Call<FoodSearchResponseV2> call, Throwable t) {
                btnAdd.setEnabled(true);
                btnAdd.setText("Search");
                toast("Search failed. Check connection/server.");
                updateAddButtonLabel();
            }
        });
    }

    private void applyChosenFood(NormalizedFoodItem best) {
        // Name
        String chosenName = ((best.brandName != null && !best.brandName.isEmpty()) ? best.brandName + " " : "")
                + (best.description != null ? best.description : "");
        chosenName = chosenName.trim();
        if (!chosenName.isEmpty()) etFood.setText(chosenName);

        // Base per-100g
        if (best.servings != null && best.servings.per100g != null) {
            basePer100g = best.servings.per100g;
        } else {
            basePer100g = new Per100g();
        }

        // Units (gram/oz always present; plus serving/household measures when available)
        unitList = (best.units != null) ? best.units : new ArrayList<>();
        int defaultIndex = 0;
        for (int i = 0; i < unitList.size(); i++) {
            if ("serving".equalsIgnoreCase(unitList.get(i).label)) { defaultIndex = i; break; }
        }
        setUnitsAdapterAndSelect(defaultIndex);

        // Default qty
        if (etQuantity.getText() == null || etQuantity.getText().length() == 0) etQuantity.setText("1");

        // Render scaled macros now
        recomputeAndRender();

        btnAdd.setText("Add Entry");
        toast("Filled from USDA.");
        updateAddButtonLabel();
    }

    /* ==============================  BARCODE  ============================== */

    private void searchByBarcode(String code) {
        btnAdd.setEnabled(false);
        btnAdd.setText("Searching…");

        UsdaProxyService api = RetroFitProvider.get();
        api.getByBarcodeNormalized(code).enqueue(new Callback<BarcodeLookupResponse>() {
            @Override public void onResponse(Call<BarcodeLookupResponse> call, Response<BarcodeLookupResponse> resp) {
                btnAdd.setEnabled(true);

                if (!resp.isSuccessful() || resp.body()==null || resp.body().item==null) {
                    btnAdd.setText("Search");
                    toast("No product found for code.");
                    updateAddButtonLabel();
                    return;
                }

                applyChosenFood(resp.body().item);
            }

            @Override public void onFailure(Call<BarcodeLookupResponse> call, Throwable t) {
                btnAdd.setEnabled(true);
                btnAdd.setText("Search");
                toast("Barcode lookup failed. Check connection/server.");
                updateAddButtonLabel();
            }
        });
    }
}
