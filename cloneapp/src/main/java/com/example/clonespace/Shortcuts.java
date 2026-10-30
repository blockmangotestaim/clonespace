package com.example.clonespace;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

import androidx.core.content.pm.ShortcutInfoCompat;
import androidx.core.content.pm.ShortcutManagerCompat;
import androidx.core.graphics.drawable.IconCompat;

public final class Shortcuts {
    private Shortcuts() {}

    private static Bitmap toBitmap(Drawable d) {
        Bitmap b = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 192, 192);
        d.draw(c);
        return b;
    }

    public static boolean add(Context ctx, EngineBridge.Row row) {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(ctx)) return false;
        Intent intent = new Intent(ctx, LaunchProxyActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(LaunchProxyActivity.EXTRA_PKG, row.pkg);
        ShortcutInfoCompat info = new ShortcutInfoCompat.Builder(ctx, "clone_" + row.pkg)
                .setShortLabel(row.label + " (clone)")
                .setIcon(IconCompat.createWithBitmap(toBitmap(row.icon)))
                .setIntent(intent)
                .build();
        return ShortcutManagerCompat.requestPinShortcut(ctx, info, null);
    }
}
