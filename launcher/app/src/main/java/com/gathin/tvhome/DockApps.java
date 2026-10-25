package com.gathin.tvhome;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The apps in Your apps, Home's own row (the + tile adds one, holding OK takes one out).
 *
 * The row used to be the first {@code columns} apps of the sorted list, so it could never hold more than fitted
 * on screen. Since 2.23 it is the owner's list, kept here as one package name per line in their order, the same
 * shape as {@link Favorites} — the launcher's private files, so it survives updates and never leaves the TV.
 *
 * With no file yet it is seeded from {@code dock_apps} (config.xml or a theme), so a TV updating to 2.23 keeps
 * exactly the row it had. After that this file is the truth; deleting it seeds again from the configuration,
 * which is what Follow theme and a theme push rely on.
 */
final class DockApps {

    private static final String TAG = "TvHome.DockApps";
    static final String FILE_NAME = "gtv_dock.txt";
    /** More than any dock needs, and small enough that the row stays something a remote can cross. */
    static final int MAX = 32;
    private static final int MAX_BYTES = MAX * 256;

    private DockApps() { }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** The saved dock in order, or null when there is no file or it cannot be read (never an empty list). */
    static List<String> load(Context ctx) {
        AtomicFile af = new AtomicFile(file(ctx));
        if (!af.getBaseFile().exists() && !new File(af.getBaseFile() + ".bak").exists()) return null;
        try (FileInputStream in = af.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Dock file too large");
                bytes.write(buffer, 0, count);
            }
            return decode(new String(bytes.toByteArray(), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            // An unreadable file is ignored, never replaced with an empty one.
            Log.w(TAG, "Could not read " + FILE_NAME, e);
            return null;
        }
    }

    /** Writes the list atomically. False (and a log line) when the write failed. */
    static boolean save(Context ctx, List<String> packages) {
        AtomicFile af = new AtomicFile(file(ctx));
        FileOutputStream out = null;
        try {
            byte[] bytes = encode(packages).getBytes(StandardCharsets.UTF_8);
            out = af.startWrite();
            out.write(bytes);
            af.finishWrite(out);
            return true;
        } catch (IOException | RuntimeException e) {
            if (out != null) af.failWrite(out);
            Log.w(TAG, "Could not save the dock", e);
            return false;
        }
    }

    /** The list with this package at the end, or unchanged when it is already in it. */
    static List<String> added(List<String> packages, String pkg) {
        if (!LibraryApps.validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
        if (packages.contains(pkg)) return packages;
        if (packages.size() >= MAX) throw new IllegalArgumentException("Dock is full");
        List<String> next = new ArrayList<>(packages);
        next.add(pkg);
        return next;
    }

    /** The list without this package. The caller keeps the last app: an empty dock has no way back. */
    static List<String> removed(List<String> packages, String pkg) {
        List<String> next = new ArrayList<>(packages);
        next.remove(pkg);
        return next;
    }

    static String encode(List<String> packages) {
        if (packages.size() > MAX) throw new IllegalArgumentException("Dock is full");
        Set<String> seen = new HashSet<>();
        StringBuilder text = new StringBuilder();
        for (String pkg : packages) {
            if (!LibraryApps.validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
            if (!seen.add(pkg)) throw new IllegalArgumentException("Duplicate package");
            text.append(pkg).append('\n');
        }
        return text.toString();
    }

    /** The packages in this text, in order. Throws when it is not a dock file. */
    static List<String> decode(String text) {
        if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Dock file too large");
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') continue;
            if (i == start && i == text.length()) break;
            String pkg = text.substring(start, i);
            if (!LibraryApps.validPackage(pkg) || !seen.add(pkg) || result.size() >= MAX)
                throw new IllegalArgumentException("Invalid dock file");
            result.add(pkg);
            start = i + 1;
        }
        return Collections.unmodifiableList(result);
    }
}
