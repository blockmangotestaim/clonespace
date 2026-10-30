package com.example.clonespace;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Invisible activity used by home-screen shortcuts to start a clone. */
public class LaunchProxyActivity extends Activity {
    public static final String EXTRA_PKG = "pkg";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String pkg = getIntent().getStringExtra(EXTRA_PKG);
        if (pkg != null) {
            EngineBridge.Outcome r = EngineBridge.launch(pkg);
            if (!r.ok) Toast.makeText(this, r.message, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
