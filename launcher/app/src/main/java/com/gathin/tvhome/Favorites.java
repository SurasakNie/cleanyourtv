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
 * The owner's Favorite apps, the rows under Your apps in the Google TV layout (hold OK on an app to add or
 * remove one). Kept in the launcher's private files as one package name per line, in the owner's order, so it
 * survives updates and never leaves the TV. Apple TV layout rows do not use it.
 */
final class Favorites {

    private static final String TAG = "TvHome.Favorites";
    static final String FILE_NAME = "gtv_favorites.txt";
    static final int MAX = 64;
    private static final int MAX_BYTES = MAX * 256;

    private Favorites() { }

    static File file(Context ctx) {
        return new File(ctx.getFilesDir(), FILE_NAME);
    }

    /** The saved favorites in order, or null when there is no file or it cannot be read (never an empty list). */
    static List<String> load(Context ctx) {
        AtomicFile af = new AtomicFile(file(ctx));
        if (!af.getBaseFile().exists() && !new File(af.getBaseFile() + ".bak").exists()) return null;
        try (FileInputStream in = af.openRead()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = in.read(buffer)) != -1) {
                if (bytes.size() + count > MAX_BYTES) throw new IOException("Favorites file too large");
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
            Log.w(TAG, "Could not save favorites", e);
            return false;
        }
    }

    /** The list with this package at the end, or unchanged when it is already in it. */
    static List<String> added(List<String> packages, String pkg) {
        if (!LibraryApps.validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
        if (packages.contains(pkg)) return packages;
        if (packages.size() >= MAX) throw new IllegalArgumentException("Too many favorites");
        List<String> next = new ArrayList<>(packages);
        next.add(pkg);
        return next;
    }

    static List<String> removed(List<String> packages, String pkg) {
        List<String> next = new ArrayList<>(packages);
        next.remove(pkg);
        return next;
    }

    static String encode(List<String> packages) {
        if (packages.size() > MAX) throw new IllegalArgumentException("Too many favorites");
        Set<String> seen = new HashSet<>();
        StringBuilder text = new StringBuilder();
        for (String pkg : packages) {
            if (!LibraryApps.validPackage(pkg)) throw new IllegalArgumentException("Invalid package");
            if (!seen.add(pkg)) throw new IllegalArgumentException("Duplicate package");
            text.append(pkg).append('\n');
        }
        return text.toString();
    }

    /** The packages in this text, in order. Throws when it is not a favorites file. */
    static List<String> decode(String text) {
        if (text.length() > MAX_BYTES) throw new IllegalArgumentException("Favorites file too large");
        List<String> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        int start = 0;
        for (int i = 0; i <= text.length(); i++) {
            if (i < text.length() && text.charAt(i) != '\n') continue;
            if (i == start && i == text.length()) break;
            String pkg = text.substring(start, i);
            if (!LibraryApps.validPackage(pkg) || !seen.add(pkg) || result.size() >= MAX)
                throw new IllegalArgumentException("Invalid favorites file");
            result.add(pkg);
            start = i + 1;
        }
        return Collections.unmodifiableList(result);
    }
}
