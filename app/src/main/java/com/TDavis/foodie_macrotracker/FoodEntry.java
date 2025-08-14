package com.TDavis.foodie_macrotracker;

/**
 * FoodEntry represents a single food item that the user logs.
 * It stores the food's name, calorie count, macronutrient values,
 * and the date it was logged.
 */
public class FoodEntry {
    // Name of the food (e.g., "Chicken Breast")
    String name;

    // Macronutrient information
    int calories; // Total calories in the food
    int protein;  // Protein content (grams)
    int carbs;    // Carbohydrate content (grams)
    int fat;      // Fat content (grams)

    // Date when the entry was logged (format: yyyy-MM-dd)
    String date;

    /**
     * Constructor — creates a new FoodEntry with all details.
     *
     * @param name     Name of the food
     * @param calories Calorie amount
     * @param protein  Protein in grams
     * @param carbs    Carbs in grams
     * @param fat      Fat in grams
     * @param date     Date the entry was recorded
     */
    public FoodEntry(String name, int calories, int protein, int carbs, int fat, String date) {
        this.name = name;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.date = date;
    }
}
