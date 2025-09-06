package com.TDavis.foodie_macrotracker.net.models;

public class BarcodeResult {
    public String code;
    public String description;     // product name
    public String brand;
    public String servingSize;     // e.g., "30 g" (string from OFF)
    public String servingSizeUnit; // may be null
    public Double calories;
    public Double protein;
    public Double carbs;
    public Double fat;
}
