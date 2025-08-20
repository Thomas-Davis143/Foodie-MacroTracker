package com.TDavis.foodie_macrotracker;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {

    EditText etGoalCal, etGoalPro, etGoalCar, etGoalFat;
    RadioGroup rgTheme;
    Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply theme so this screen opens correctly styled
        applySavedTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etGoalCal = findViewById(R.id.etGoalCal);
        etGoalPro = findViewById(R.id.etGoalPro);
        etGoalCar = findViewById(R.id.etGoalCar);
        etGoalFat = findViewById(R.id.etGoalFat);
        rgTheme   = findViewById(R.id.rgTheme);
        btnSave   = findViewById(R.id.btnSaveSettings);

        // Prefill from prefs
        SharedPreferences prefs = getSharedPreferences("FoodiePrefs", MODE_PRIVATE);
        etGoalCal.setText(String.valueOf(prefs.getInt("goalCal", 2000)));
        etGoalPro.setText(String.valueOf(prefs.getInt("goalPro", 150)));
        etGoalCar.setText(String.valueOf(prefs.getInt("goalCar", 250)));
        etGoalFat.setText(String.valueOf(prefs.getInt("goalFat", 70)));

        int themeMode = prefs.getInt("themeMode", 0); // 0 system, 1 light, 2 dark
        switch (themeMode) {
            case 1: rgTheme.check(R.id.rbLight); break;
            case 2: rgTheme.check(R.id.rbDark); break;
            default: rgTheme.check(R.id.rbSystem); break;
        }

        btnSave.setOnClickListener(v -> {
            int goalCal = safeInt(etGoalCal.getText().toString(), 2000, 1);
            int goalPro = safeInt(etGoalPro.getText().toString(), 150, 1);
            int goalCar = safeInt(etGoalCar.getText().toString(), 250, 1);
            int goalFat = safeInt(etGoalFat.getText().toString(), 70, 1);

            int mode = 0;
            if (rgTheme.getCheckedRadioButtonId() == R.id.rbLight) mode = 1;
            else if (rgTheme.getCheckedRadioButtonId() == R.id.rbDark) mode = 2;

            getSharedPreferences("FoodiePrefs", MODE_PRIVATE).edit()
                    .putInt("goalCal", goalCal)
                    .putInt("goalPro", goalPro)
                    .putInt("goalCar", goalCar)
                    .putInt("goalFat", goalFat)
                    .putInt("themeMode", mode)
                    .apply();

            // Apply theme immediately
            int m = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
            if (mode == 1) m = AppCompatDelegate.MODE_NIGHT_NO;
            else if (mode == 2) m = AppCompatDelegate.MODE_NIGHT_YES;
            AppCompatDelegate.setDefaultNightMode(m);

            // Close and return to MainActivity (which refreshes onResume)
            finish();
        });
    }

    private int safeInt(String s, int def, int min) {
        try {
            int v = Integer.parseInt(s.trim());
            return Math.max(min, v);
        } catch (Exception e) {
            return def;
        }
    }

    private void applySavedTheme() {
        int mode = getSharedPreferences("FoodiePrefs", MODE_PRIVATE).getInt("themeMode", 0);
        int m = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (mode == 1) m = AppCompatDelegate.MODE_NIGHT_NO;
        else if (mode == 2) m = AppCompatDelegate.MODE_NIGHT_YES;
        AppCompatDelegate.setDefaultNightMode(m);
    }
}
