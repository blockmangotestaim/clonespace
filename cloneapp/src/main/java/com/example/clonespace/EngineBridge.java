package com.example.clonespace;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Process;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.ClientConfiguration;

public final class EngineBridge {
    private EngineBridge() {}

    /** One clone per app lives in virtual user 0. */
    public static final int USER_ID = 0;

    public static class Row {
        public final String pkg, label, note;
        public final Drawable icon;
        public final boolean noteIsError;
        public Row(String pkg, String label, Drawable icon, String note, boolean noteIsError) {
            this.pkg = pkg; this.label = label; this.icon = icon; this.note = note; this.noteIsError = noteIsError;
        }
    }

    public static class Outcome {
        public final boolean ok;
        public final String message;
        public Outcome(boolean ok, String message) { this.ok = ok; this.message = message; }
    }

    // ---- lifecycle -------------------------------------------------------
    public static void attach(final Context base) {
        try {
            BlackBoxCore.get().doAttachBaseContext(base, new ClientConfiguration() {
                @Override
                public String getHostPackageName() { return base.getPackageName(); }
            });
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void create() {
        try { BlackBoxCore.get().doCreate(); } catch (Throwable t) { t.printStackTrace(); }
    }

    // ---- reflection helpers ---------------------------------------------
    private static Class<?> box(Class<?> c) {
        if (!c.isPrimitive()) return c;
        if (c == int.class) return Integer.class;
        if (c == boolean.class) return Boolean.class;
        if (c == long.class) return Long.class;
        return c;
    }

    private static Object call(String name, Object... args) throws Exception {
        Object core = BlackBoxCore.get();
        for (Method m : core.getClass().getMethods()) {
            Class<?>[] pt = m.getParameterTypes();
            if (!m.getName().equals(name) || pt.length != args.length) continue;
            boolean match = true;
            for (int i = 0; i < pt.length; i++) {
                if (args[i] == null || !box(pt[i]).isInstance(args[i])) { match = false; break; }
            }
            if (!match) continue;
            try {
                return m.invoke(core, args);
            } catch (InvocationTargetException e) {
                Throwable c = e.getCause();
                throw (c instanceof Exception) ? (Exception) c : new RuntimeException(c);
            }
        }
        throw new NoSuchMethodException("Engine has no method " + name + " with " + args.length + " matching args");
    }

    private static Object read(Object o, String field, String... getters) {
        try { return o.getClass().getField(field).get(o); } catch (Throwable ignored) { }
        for (String g : getters) {
            try { return o.getClass().getMethod(g).invoke(o); } catch (Throwable ignored) { }
        }
        return null;
    }

    // ---- operations ------------------------------------------------------
    public static List<String> listClonedPackages() {
        List<String> out = new ArrayList<>();
        try {
            Object r = call("getInstalledApplications", 0, USER_ID);
            if (r instanceof List) for (Object o : (List<?>) r) {
                if (o instanceof ApplicationInfo) out.add(((ApplicationInfo) o).packageName);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    public static boolean isCloned(String pkg) { return listClonedPackages().contains(pkg); }

    public static Outcome clone(Context ctx, String pkg) {
        String why = checkSupport(ctx, pkg);
        if (why != null) return new Outcome(false, why);
        try {
            Object r = call("installPackageAsUser", pkg, USER_ID);
            Object success = r == null ? Boolean.TRUE : read(r, "success", "isSuccess", "getSuccess");
            if (Boolean.TRUE.equals(success)) return new Outcome(true, null);
            Object msg = r == null ? null : read(r, "msg", "getMsg", "getMessage");
            return new Outcome(false, "The engine could not install this app: "
                    + (msg != null ? msg : "unknown reason") + ".\n"
                    + "It likely uses features the virtual engine doesn't support (split APKs, anti-tamper, special system APIs).");
        } catch (Throwable t) {
            return new Outcome(false, "Cloning failed: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public static Outcome launch(String pkg) {
        try {
            if (!isCloned(pkg)) return new Outcome(false, "This clone no longer exists.");
            Object r = call("launchApk", pkg, USER_ID);
            if (Boolean.FALSE.equals(r)) return new Outcome(false, "The engine refused to start this app.");
            return new Outcome(true, null);
        } catch (Throwable t) {
            return new Outcome(false, "Couldn't launch the clone: " + t.getMessage());
        }
    }

    public static Outcome delete(String pkg) {
        try { call("uninstallPackageAsUser", pkg, USER_ID); return new Outcome(true, null); }
        catch (Throwable t) { return new Outcome(false, "Couldn't delete the clone: " + t.getMessage()); }
    }

    // ---- 32-bit / 64-bit handling ---------------------------------------
    public static boolean hostIs64Bit() {
        return Build.VERSION.SDK_INT >= 23 && Process.is64Bit();
    }

    /** Reads lib/<abi>/ folders of the base APK and its splits (no hidden APIs). */
    private static Set<String> nativeAbis(ApplicationInfo ai) {
        List<String> paths = new ArrayList<>();
        if (ai.sourceDir != null) paths.add(ai.sourceDir);
        if (ai.splitSourceDirs != null) for (String s : ai.splitSourceDirs) paths.add(s);
        Set<String> abis = new HashSet<>();
        for (String p : paths) {
            try (ZipFile z = new ZipFile(p)) {
                Enumeration<? extends ZipEntry> en = z.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.startsWith("lib/")) {
                        String[] parts = n.split("/");
                        if (parts.length >= 3) abis.add(parts[1]);
                    }
                }
            } catch (Throwable ignored) { }
        }
        return abis;
    }

    /** Returns a human-readable reason if this build can't clone the app, else null. */
    public static String checkSupport(Context ctx, String pkg) {
        if (pkg.equals(ctx.getPackageName())) return "CloneSpace can't clone itself.";
        ApplicationInfo ai;
        try { ai = ctx.getPackageManager().getApplicationInfo(pkg, 0); }
        catch (PackageManager.NameNotFoundException e) { return "App is not installed."; }

        boolean sys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        boolean updated = (ai.flags & ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0;
        if (sys && !updated) return "System apps can't be cloned.";

        Set<String> abis = nativeAbis(ai);
        boolean has64 = abis.contains("arm64-v8a") || abis.contains("x86_64");
        boolean has32 = abis.contains("armeabi-v7a") || abis.contains("armeabi") || abis.contains("x86");
        if (!abis.isEmpty()) {
            if (hostIs64Bit() && !has64)
                return "This app only ships 32-bit code. Install the 32-bit edition of CloneSpace (arm32 APK) to clone it.";
            if (!hostIs64Bit() && !has32)
                return "This app only ships 64-bit code. Use the 64-bit edition of CloneSpace (arm64 APK) to clone it.";
        }
        return null;
    }

    public static String deviceInfo() {
        StringBuilder sb = new StringBuilder(hostIs64Bit() ? "64-bit edition" : "32-bit edition").append(" • ABIs: ");
        for (int i = 0; i < Build.SUPPORTED_ABIS.length; i++) sb.append(i > 0 ? "," : "").append(Build.SUPPORTED_ABIS[i]);
        return sb.toString();
    }
}
