package com.TDavis.foodie_macrotracker;

/** Model for a single logged food entry. */
public class FoodEntry {
    // Basic info
    String name;
    int calories, protein, carbs, fat;

    // Metadata
    String date;      // e.g., "2025-08-14"
    long createdAt;   // ms since epoch
    String mealType;  // Breakfast/Lunch/Dinner/Snack/Other

    public FoodEntry(String name, int calories, int protein, int carbs, int fat, String date, String mealType) {
        this.name = name;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.date = date;
        this.createdAt = System.currentTimeMillis();
        this.mealType = mealType;
    }
}
