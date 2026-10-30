package com.example.clonespace;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PickAppActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppAdapter adapter;
    private ProgressBar progress;
    private AlertDialog blocking;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pick);
        progress = findViewById(R.id.progress);
        adapter = new AppAdapter(false, this::onPick, null);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        load();
    }

    private void load() {
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            List<String> cloned = EngineBridge.listClonedPackages();
            List<EngineBridge.Row> rows = new ArrayList<>();
            for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
                if (ai.packageName.equals(getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
                String reason = EngineBridge.checkSupport(this, ai.packageName);
                String note; boolean err = false;
                if (reason != null) { note = "Unsupported: " + reason; err = true; }
                else if (cloned.contains(ai.packageName)) note = "Already cloned";
                else note = ai.packageName;
                rows.add(new EngineBridge.Row(ai.packageName, pm.getApplicationLabel(ai).toString(),
                        pm.getApplicationIcon(ai), note, err));
            }
            Collections.sort(rows, (a, b) -> {
                if (a.noteIsError != b.noteIsError) return a.noteIsError ? 1 : -1;
                return a.label.compareToIgnoreCase(b.label);
            });
            runOnUiThread(() -> { adapter.setRows(rows); progress.setVisibility(View.GONE); });
        });
    }

    private void onPick(final EngineBridge.Row row) {
        if (row.noteIsError) { error(row, row.note.replaceFirst("^Unsupported: ", "")); return; }
        if ("Already cloned".equals(row.note)) {
            Toast.makeText(this, "Already cloned", Toast.LENGTH_SHORT).show(); return;
        }
        blocking = new AlertDialog.Builder(this).setMessage("Cloning " + row.label + "…").setCancelable(false).show();
        io.execute(() -> {
            EngineBridge.Outcome r = EngineBridge.clone(this, row.pkg);
            runOnUiThread(() -> {
                if (blocking != null) blocking.dismiss();
                if (r.ok) {
                    Toast.makeText(this, row.label + " cloned", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    error(row, r.message != null ? r.message : "Unknown error");
                }
            });
        });
    }

    private void error(EngineBridge.Row row, String msg) {
        new AlertDialog.Builder(this).setTitle("Can't clone " + row.label)
                .setMessage(msg).setPositiveButton("OK", null).show();
    }
}
