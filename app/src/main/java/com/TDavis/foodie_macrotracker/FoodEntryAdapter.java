package com.TDavis.foodie_macrotracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;

/**
 * FoodEntryAdapter is the bridge between the ArrayList<FoodEntry>
 * and the RecyclerView. It takes data (entries) and turns them into
 * list items the user sees on the screen.
 */
public class FoodEntryAdapter extends RecyclerView.Adapter<FoodEntryAdapter.ViewHolder> {

    // List of all food entries to display
    ArrayList<FoodEntry> entries;

    // Constructor — pass in the list of entries from MainActivity
    public FoodEntryAdapter(ArrayList<FoodEntry> entries) {
        this.entries = entries;
    }

    /**
     * Called when a new list item view needs to be created.
     * We "inflate" (load) the built-in Android simple_list_item_2 layout,
     * which has two text fields (text1 and text2).
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(android.R.layout.simple_list_item_2, parent, false);
        return new ViewHolder(v);
    }

    /**
     * Called to put data into a list item at a given position.
     * Here we set the food name and its calories/macros.
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FoodEntry entry = entries.get(position);
        holder.text1.setText(entry.name); // First line: food name
        holder.text2.setText(entry.calories + " kcal • P" +
                entry.protein + "/C" + entry.carbs + "/F" + entry.fat + " g"); // Second line: macros
    }

    /**
     * Returns how many items are in the list.
     */
    @Override
    public int getItemCount() {
        return entries.size();
    }

    /**
     * ViewHolder holds the references to the text views inside
     * each list item so we can reuse them instead of creating
     * new ones each time (saves memory and improves performance).
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView text1, text2;

        ViewHolder(View v) {
            super(v);
            text1 = v.findViewById(android.R.id.text1); // The built-in first line
            text2 = v.findViewById(android.R.id.text2); // The built-in second line
        }
    }
}
