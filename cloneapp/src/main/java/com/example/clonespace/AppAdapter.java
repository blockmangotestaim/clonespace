package com.example.clonespace;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppAdapter extends RecyclerView.Adapter<AppAdapter.VH> {
    public interface OnRow { void on(EngineBridge.Row row); }
    public interface OnMenu { void on(View anchor, EngineBridge.Row row); }

    private final boolean showMenu;
    private final OnRow onClick;
    private final OnMenu onMenu;
    private List<EngineBridge.Row> rows = new ArrayList<>();

    public AppAdapter(boolean showMenu, OnRow onClick, OnMenu onMenu) {
        this.showMenu = showMenu; this.onClick = onClick; this.onMenu = onMenu;
    }

    public void setRows(List<EngineBridge.Row> r) { rows = r; notifyDataSetChanged(); }

    static class VH extends RecyclerView.ViewHolder {
        final ImageView icon; final TextView name, sub; final ImageButton menu;
        VH(View v) {
            super(v);
            icon = v.findViewById(R.id.icon); name = v.findViewById(R.id.name);
            sub = v.findViewById(R.id.sub); menu = v.findViewById(R.id.menu);
        }
    }

    @NonNull @Override
    public VH onCreateViewHolder(@NonNull ViewGroup p, int t) {
        return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_app, p, false));
    }

    @Override public int getItemCount() { return rows.size(); }

    @Override
    public void onBindViewHolder(@NonNull VH h, int i) {
        final EngineBridge.Row r = rows.get(i);
        h.icon.setImageDrawable(r.icon);
        h.name.setText(r.label);
        h.sub.setText(r.note != null ? r.note : r.pkg);
        h.sub.setTextColor(r.noteIsError ? Color.parseColor("#C62828") : Color.GRAY);
        h.itemView.setOnClickListener(v -> onClick.on(r));
        h.menu.setVisibility(showMenu ? View.VISIBLE : View.GONE);
        h.menu.setOnClickListener(v -> { if (onMenu != null) onMenu.on(v, r); });
    }
}
