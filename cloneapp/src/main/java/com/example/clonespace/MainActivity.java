package com.example.clonespace;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private AppAdapter adapter;
    private TextView empty;
    private String pendingLaunch;
    private static boolean askedOnce = false;

    // Cloned apps ask the host for runtime permissions, so we pre-request the common ones.
    private static final String[] RUNTIME_PERMS = {
            Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS,
            Manifest.permission.GET_ACCOUNTS, Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.CALL_PHONE
    };

    private ActivityResultLauncher<String[]> permLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle("CloneSpace");
        if (getSupportActionBar() != null) getSupportActionBar().setSubtitle(EngineBridge.deviceInfo());

        permLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
            if (pendingLaunch != null) { String p = pendingLaunch; pendingLaunch = null; doLaunch(p); }
        });

        empty = findViewById(R.id.empty);
        adapter = new AppAdapter(true, row -> launch(row.pkg), this::showMenu);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        ((FloatingActionButton) findViewById(R.id.fab)).setOnClickListener(
                v -> startActivity(new Intent(this, PickAppActivity.class)));

        if (Build.VERSION.SDK_INT >= 33) { /* targetSdk 28: notifications need no runtime prompt */ }
    }

    @Override
    protected void onResume() { super.onResume(); refresh(); }

    private void refresh() {
        io.execute(() -> {
            PackageManager pm = getPackageManager();
            List<EngineBridge.Row> rows = new ArrayList<>();
            for (String pkg : EngineBridge.listClonedPackages()) {
                try {
                    android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    rows.add(new EngineBridge.Row(pkg, pm.getApplicationLabel(ai).toString(),
                            pm.getApplicationIcon(ai), null, false));
                } catch (Exception e) {
                    rows.add(new EngineBridge.Row(pkg, pkg, pm.getDefaultActivityIcon(),
                            "Original app was uninstalled", true));
                }
            }
            Collections.sort(rows, (a, b) -> a.label.compareToIgnoreCase(b.label));
            runOnUiThread(() -> {
                adapter.setRows(rows);
                empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void launch(String pkg) {
        List<String> missing = new ArrayList<>();
        for (String p : RUNTIME_PERMS)
            if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
        if (!missing.isEmpty() && !askedOnce) {
            askedOnce = true;
            pendingLaunch = pkg;
            permLauncher.launch(missing.toArray(new String[0]));
        } else {
            doLaunch(pkg);
        }
    }

    private void doLaunch(String pkg) {
        EngineBridge.Outcome r = EngineBridge.launch(pkg);
        if (!r.ok) new AlertDialog.Builder(this).setTitle("Can't launch clone")
                .setMessage(r.message).setPositiveButton("OK", null).show();
    }

    private void showMenu(View anchor, final EngineBridge.Row row) {
        PopupMenu pm = new PopupMenu(this, anchor);
        pm.getMenu().add(0, 1, 0, "Add shortcut to home screen");
        pm.getMenu().add(0, 2, 1, "Delete clone");
        pm.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == 1) {
                boolean ok = Shortcuts.add(this, row);
                Toast.makeText(this, ok ? "Shortcut requested" : "Your launcher doesn't support pinned shortcuts",
                        Toast.LENGTH_SHORT).show();
            } else {
                confirmDelete(row);
            }
            return true;
        });
        pm.show();
    }

    private void confirmDelete(final EngineBridge.Row row) {
        new AlertDialog.Builder(this)
                .setTitle("Delete clone of " + row.label + "?")
                .setMessage("All data inside the clone (accounts, chats, saves) will be erased. The original app is not affected.")
                .setPositiveButton("Delete", (d, w) -> io.execute(() -> {
                    EngineBridge.Outcome r = EngineBridge.delete(row.pkg);
                    runOnUiThread(() -> {
                        if (!r.ok) Toast.makeText(this, r.message, Toast.LENGTH_LONG).show();
                        refresh();
                    });
                }))
                .setNegativeButton("Cancel", null).show();
    }
}
