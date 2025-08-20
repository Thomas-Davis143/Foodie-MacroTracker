package com.TDavis.foodie_macrotracker;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.*;

/** Collapsible sections (Breakfast/Lunch/Dinner/Snack/Other) with per-section totals. */
public class SectionedEntryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM   = 1;

    private final List<String> sectionOrder = Arrays.asList("Breakfast","Lunch","Dinner","Snack","Other");
    private final LinkedHashMap<String, Boolean> expanded = new LinkedHashMap<>();
    { for (String s : sectionOrder) expanded.put(s, true); }

    private final List<FoodEntry> source = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final Map<String,Integer> counts = new HashMap<>();

    // Per-section totals
    private final Map<String, SectionTotals> totals = new HashMap<>();
    private static class SectionTotals { int cal, pro, car, fat; void add(FoodEntry e){ cal+=e.calories; pro+=e.protein; car+=e.carbs; fat+=e.fat; } }

    public interface OnItemClickListener { void onItemClick(FoodEntry e); }
    public interface OnItemLongClickListener { void onItemLongClick(FoodEntry e); }
    private OnItemClickListener clickListener;
    private OnItemLongClickListener longClickListener;
    public void setOnItemClickListener(OnItemClickListener l) { this.clickListener = l; }
    public void setOnItemLongClickListener(OnItemLongClickListener l) { this.longClickListener = l; }

    public void setData(List<FoodEntry> entries) {
        source.clear();
        if (entries != null) source.addAll(entries);
        rebuild();
    }

    private void rebuild() {
        rows.clear(); counts.clear(); totals.clear();

        Map<String, List<FoodEntry>> byType = new LinkedHashMap<>();
        for (String s : sectionOrder) byType.put(s, new ArrayList<>());
        for (FoodEntry e : source) {
            String key = (e.mealType == null || e.mealType.trim().isEmpty()) ? "Other" : e.mealType;
            if (!byType.containsKey(key)) byType.put(key, new ArrayList<>());
            byType.get(key).add(e);
        }
        for (List<FoodEntry> list : byType.values()) {
            Collections.sort(list, (a,b) -> Long.compare(b.createdAt, a.createdAt));
        }
        for (String sec : sectionOrder) {
            List<FoodEntry> list = byType.get(sec);
            int count = (list == null) ? 0 : list.size();
            counts.put(sec, count);

            SectionTotals st = new SectionTotals();
            if (list != null) for (FoodEntry e : list) st.add(e);
            totals.put(sec, st);

            rows.add(Row.header(sec));
            if (expanded.getOrDefault(sec, true) && list != null) {
                for (FoodEntry e : list) rows.add(Row.item(sec, e));
            }
        }
        notifyDataSetChanged();
    }

    private static class Row {
        final int type; final String header; final FoodEntry entry;
        private Row(int type, String header, FoodEntry entry){ this.type=type; this.header=header; this.entry=entry; }
        static Row header(String h){ return new Row(TYPE_HEADER, h, null); }
        static Row item(String h, FoodEntry e){ return new Row(TYPE_ITEM, h, e); }
    }

    static class HeaderVH extends RecyclerView.ViewHolder {
        TextView t;
        HeaderVH(View v){ super(v); t = v.findViewById(android.R.id.text1); }
    }
    static class ItemVH extends RecyclerView.ViewHolder {
        TextView t1, t2;
        ItemVH(View v){ super(v); t1 = v.findViewById(android.R.id.text1); t2 = v.findViewById(android.R.id.text2); }
    }

    @Override public int getItemViewType(int position) { return rows.get(position).type; }

    @NonNull @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new HeaderVH(v);
        } else {
            View v = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
            return new ItemVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (row.type == TYPE_HEADER) {
            HeaderVH h = (HeaderVH) holder;
            int cnt = counts.getOrDefault(row.header, 0);
            boolean isOpen = expanded.getOrDefault(row.header, true);
            SectionTotals st = totals.get(row.header);
            int kcal = st == null ? 0 : st.cal;
            int p = st == null ? 0 : st.pro;
            int c = st == null ? 0 : st.car;
            int f = st == null ? 0 : st.fat;
            String arrow = isOpen ? " ▾" : " ▸";
            h.t.setText(row.header + " (" + cnt + ") — " + kcal + " kcal • P" + p + "/C" + c + "/F" + f + arrow);
            h.itemView.setOnClickListener(v -> { expanded.put(row.header, !isOpen); rebuild(); });
            h.itemView.setOnLongClickListener(null);
        } else {
            ItemVH i = (ItemVH) holder;
            FoodEntry e = row.entry;
            i.t1.setText(e.name);
            String time = (e.createdAt > 0)
                    ? new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(e.createdAt)) : "";
            String mt = (e.mealType == null ? "" : " • " + e.mealType);
            i.t2.setText(e.calories + " kcal • P" + e.protein + "/C" + e.carbs + "/F" + e.fat + " g"
                    + (time.isEmpty() ? "" : " • " + time) + mt);
            i.itemView.setOnClickListener(v -> { if (clickListener != null) clickListener.onItemClick(e); });
            i.itemView.setOnLongClickListener(v -> { if (longClickListener != null) { longClickListener.onItemLongClick(e); return true; } return false; });
        }
    }

    @Override public int getItemCount() { return rows.size(); }
}
