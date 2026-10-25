package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Google TV-style Home (Launcher settings, Layout: Google TV): tabs (Home · Apps · Inputs), a glass banner
 * naming the selected app over the wallpaper and its colour glow, and rows of app banners. MainActivity stays the
 * HOME component and opens this screen when the layout is chosen, so the TV's default-home setting never changes.
 * Built in code; no network, no new permission.
 */
public final class GtvActivity extends Activity {
    static final String PREF_LAYOUT = "layout";
    static final String LAYOUT_GTV = "gtv";
    private static final int SETTINGS_REQUEST = 7;
    private static final int TAB_HOME = 0, TAB_APPS = 1, TAB_INPUTS = 2;

    /** Crash guard: the process id that is building this screen, cleared once it has drawn. */
    private static final String PREF_STARTING = "gtv_starting_pid";

    /**
     * True when the last start of this screen never finished drawing in a process that has since died (it
     * crashed). MainActivity then falls back to the Apple TV layout instead of redirecting into a crash loop.
     */
    static boolean lastStartFailed(Context ctx) {
        int pid = ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getInt(PREF_STARTING, 0);
        return pid != 0 && pid != android.os.Process.myPid();
    }

    /** Switches back to the Apple TV layout and says why. */
    static void fallBack(Context ctx) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit()
                .remove(PREF_STARTING).putString(PREF_LAYOUT, "apple").commit();
        android.widget.Toast.makeText(ctx, R.string.gtv_failed, android.widget.Toast.LENGTH_LONG).show();
    }

    static boolean enabled(Context ctx) {
        return LAYOUT_GTV.equals(ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE)
                .getString(PREF_LAYOUT, LAYOUT_GTV.equals(ctx.getString(R.string.default_layout)) ? LAYOUT_GTV : "apple"));
    }

    /** One launchable app. */
    private static final class App {
        String pkg, label;
        ComponentName component;
        Drawable banner, icon;
        int color;
    }

    private float dp;
    private FrameLayout root;
    private View glow;
    private GradientDrawable glowDrawable;
    private TextView title, subtitle, clock;
    private LinearLayout banner;
    private final TextView[] tabs = new TextView[3];
    private FrameLayout content;
    private View homePage, appsPage, inputsPage;
    private RecyclerView firstHomeRow, appsGrid;
    private ScrollView homeScroll;
    /** Apps per row (Launcher settings) and rows of Other apps under Your apps (Rows below dock). */
    private int cols, rowsBelow;
    private List<String> dockApps;
    /** The Your apps tile being moved (hold OK, Move), or null. */
    private App moving;
    private LinearLayout inputsRow;
    private int currentTab = TAB_HOME;
    private int tileW, tileH;
    private final List<App> dock = new ArrayList<>(), others = new ArrayList<>(), all = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            clock.setText(DateFormat.getTimeFormat(GtvActivity.this).format(new Date()));
            handler.postDelayed(this, 30_000);
        }
    };
    /** A Launcher settings choice changed while this screen was in the background: rebuild on return. */
    private boolean settingsChanged, resumed;
    private final SharedPreferences.OnSharedPreferenceChangeListener settingsListener =
            (p, k) -> { if (!resumed) settingsChanged = true; };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        prefs.edit().putInt(PREF_STARTING, android.os.Process.myPid()).commit();
        try {
            build();
        } catch (RuntimeException e) {
            fallBack(this);
            openAppleLayout();
            return;
        }
        // Drawn: this start succeeded.
        root.post(() -> prefs.edit().remove(PREF_STARTING).apply());
        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addDataScheme("package");
        registerReceiver(packageReceiver, pf);
        receiverOn = true;
    }

    /** An app was installed, removed or changed: rebuild now, or on return when another app is in front. */
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (resumed) recreate(); else settingsChanged = true;
        }
    };
    private boolean receiverOn;

    /** Home pressed while this screen is in front (or brought back): Home tab, first app. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (homePage == null) return;
        showTab(TAB_HOME);
        styleTabs();
        if (homeScroll != null) homeScroll.scrollTo(0, 0);
        if (firstHomeRow != null) {
            firstHomeRow.scrollToPosition(0);
            firstHomeRow.post(() -> focusFirst(firstHomeRow));
        }
    }

    private void build() {
        dp = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        cols = Math.max(5, Math.min(8, cfg.columns));
        rowsBelow = Math.max(0, Math.min(3, cfg.rowsBelowDock));
        dockApps = cfg.dockApps;
        tileW = (int) ((screenW - 2 * 48 * dp - (cols - 1) * 18 * dp) / cols);
        tileH = tileW * 9 / 16;
        loadApps(cfg);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D12);
        setContentView(root);
        buildBackground(cfg);
        buildTopBar();
        buildBanner();
        content = new FrameLayout(this);
        content.setClipChildren(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.topMargin = (int) (300 * dp);
        root.addView(content, clp);
        homePage = buildHome();
        appsPage = buildApps();
        inputsPage = buildInputs();
        content.addView(homePage);
        content.addView(appsPage);
        content.addView(inputsPage);
        showTab(TAB_HOME);
        Fonts.apply(root);
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).registerOnSharedPreferenceChangeListener(settingsListener);
        if (!dock.isEmpty()) showApp(dock.get(0));
        else if (!all.isEmpty()) showApp(all.get(0));
        root.post(() -> { if (firstHomeRow != null) focusFirst(firstHomeRow); });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsChanged) {
            settingsChanged = false;
            if (!enabled(this)) { openAppleLayout(); return; }
            recreate();
            return;
        }
        resumed = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        handler.removeCallbacks(tick);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiverOn) unregisterReceiver(packageReceiver);
        getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(settingsListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != SETTINGS_REQUEST || resultCode != RESULT_OK) return;
        settingsChanged = false;
        if (!enabled(this)) openAppleLayout(); else recreate();
    }

    /** Home is never left with Back: from the content Back goes to the tabs, from the tabs it does nothing. */
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (f != null && !isTab(f)) tabs[currentTab].requestFocus();
    }

    private void openAppleLayout() {
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        finish();
        overridePendingTransition(0, 0);
    }

    // ---- apps ------------------------------------------------------------------------------------

    private void loadApps(Config cfg) {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>(cfg.hiddenApps);
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName()) || hidden.contains(pkg) || !seen.add(pkg)) continue;
            App a = new App();
            a.pkg = pkg;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.component = new ComponentName(pkg, ri.activityInfo.name);
            a.banner = AppAdapter.loadBanner(this, pm, ri);
            if (a.banner == null) a.icon = ri.loadIcon(pm);
            a.color = AppAdapter.vividColor(a.banner != null ? a.banner : a.icon);
            all.add(a);
        }
        // The Apps tab stays alphabetical.
        Collections.sort(all, (x, y) -> x.label.compareToIgnoreCase(y.label));
        // Your apps and Other apps follow the Apple TV layout's order (AppAdapter): the owner's saved order
        // (order.txt), then dock_apps, then name. Your apps = the first row, so both layouts share one dock.
        Map<String, Integer> ranks = Order.ranks(Order.load(Order.file(this)), cfg.dockApps);
        List<App> ordered = new ArrayList<>(all);
        Collections.sort(ordered, (x, y) -> {
            int saved = Order.compare(ranks, key(x), key(y));
            if (saved != 0) return saved;
            int fx = cfg.dockApps.indexOf(x.pkg), fy = cfg.dockApps.indexOf(y.pkg);
            if (fx >= 0 || fy >= 0) {
                if (fx < 0) return 1;
                if (fy < 0) return -1;
                return Integer.compare(fx, fy);
            }
            return x.label.compareToIgnoreCase(y.label);
        });
        for (int i = 0; i < ordered.size(); i++) (i < cols ? dock : others).add(ordered.get(i));
    }

    private static String key(App a) {
        return a.component.flattenToShortString();
    }

    /**
     * Saves the moved dock in order.txt, the file the Apple TV layout reads too. Saved keys this screen does not
     * show (the Apple layout's cards) keep their places; the apps take the remaining places in their new order.
     */
    private void saveOrder() {
        List<String> mine = new ArrayList<>();
        for (App a : dock) mine.add(key(a));
        for (App a : others) mine.add(key(a));
        Set<String> mineSet = new HashSet<>(mine);
        List<String> keys = new ArrayList<>();
        Order old = Order.load(Order.file(this));
        int next = 0;
        if (old != null && old.dock.equals(Order.dockKey(dockApps))) {
            for (String k : old.keys) {
                if (!mineSet.contains(k)) keys.add(k);
                else if (next < mine.size()) keys.add(mine.get(next++));
            }
        }
        while (next < mine.size()) keys.add(mine.get(next++));
        if (!Order.save(Order.file(this), new Order(Order.dockKey(dockApps), keys))) {
            Toast.makeText(this, R.string.reorder_not_saved, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(a.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            startActivity(i);
            LastOpened.record(this, a.pkg);
        } catch (ActivityNotFoundException | SecurityException e) {
            android.widget.Toast.makeText(this, R.string.not_available, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(App a) {
        boolean inDock = dock.contains(a);
        String[] items = inDock
                ? new String[]{getString(R.string.gtv_open), getString(R.string.gtv_move), getString(R.string.library_app_info)}
                : new String[]{getString(R.string.gtv_open), getString(R.string.library_app_info)};
        new AlertDialog.Builder(this).setTitle(a.label).setItems(items, (d, which) -> {
            if (which == 0) open(a);
            else if (inDock && which == 1) startMove(a);
            else {
                try {
                    startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                } catch (ActivityNotFoundException ignored) {
                }
            }
        }).show();
    }

    // ---- moving a Your apps tile ----------------------------------------------------------------

    private void startMove(App a) {
        moving = a;
        Toast.makeText(this, R.string.gtv_move_hint, Toast.LENGTH_LONG).show();
        refocusMoving();
    }

    /** Left/Right move the tile within Your apps; OK or Back puts it down and saves. Other keys do nothing. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        int code = e.getKeyCode();
        boolean drop = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_BACK;
        if (code == KeyEvent.KEYCODE_HOME) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;   // the up of OK/Back must not open the app
        int from = dock.indexOf(moving);
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            int to = from + (code == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
            if (from >= 0 && to >= 0 && to < dock.size()) {
                Collections.swap(dock, from, to);
                firstHomeRow.getAdapter().notifyItemMoved(from, to);
                firstHomeRow.scrollToPosition(to);
                firstHomeRow.post(this::refocusMoving);
            }
        } else if (drop && e.getRepeatCount() == 0) {
            endMove();
        }
        return true;
    }

    private void endMove() {
        App a = moving;
        moving = null;
        saveOrder();
        firstHomeRow.getAdapter().notifyDataSetChanged();
        int pos = dock.indexOf(a);
        firstHomeRow.post(() -> {
            RecyclerView.ViewHolder vh = firstHomeRow.findViewHolderForAdapterPosition(pos);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void refocusMoving() {
        int pos = dock.indexOf(moving);
        RecyclerView.ViewHolder vh = firstHomeRow.findViewHolderForAdapterPosition(pos);
        if (vh == null) return;
        vh.itemView.requestFocus();
        vh.itemView.setForeground(ring(MOVE_COLOR));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (moving != null) endMove();   // leaving Home while moving keeps the order so far
    }

    // ---- background, top bar, banner ------------------------------------------------------------

    private void buildBackground(Config cfg) {
        File wall = cfg.wallpaper;
        if (wall == null && cfg.wallpapers != null && !cfg.wallpapers.isEmpty()) wall = cfg.wallpapers.get(0);
        if (wall != null) {
            Bitmap bmp = decodeWallpaper(wall);
            if (bmp != null) {
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setImageBitmap(bmp);
                root.addView(iv, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        glow = new View(this);
        glowDrawable = new GradientDrawable();
        glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glowDrawable.setGradientCenter(0.8f, 0.05f);
        glowDrawable.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.6f);
        glow.setBackground(glowDrawable);
        glow.setVisibility(cfg.ambientGlow ? View.VISIBLE : View.GONE);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        // Keep the lower half dark so rows stay readable over any picture.
        View shade = new View(this);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x000B0D12, 0x660B0D12, 0xEE0B0D12});
        shade.setBackground(g);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
    }

    /**
     * Full colour (no banding on dark gradients), but never larger than the screen: a 4000×3000 photo would
     * otherwise take about 48 MB. Null when the file can't be read or memory runs out.
     */
    private Bitmap decodeWallpaper(File file) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getPath(), o);
            int sw = getResources().getDisplayMetrics().widthPixels, sh = getResources().getDisplayMetrics().heightPixels;
            int sample = 1;
            while (o.outWidth / (sample * 2) >= sw && o.outHeight / (sample * 2) >= sh) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            o.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return BitmapFactory.decodeFile(file.getPath(), o);
        } catch (OutOfMemoryError | RuntimeException e) {
            return null;
        }
    }

    private void buildTopBar() {
        LinearLayout bar = glassStrip();
        bar.setPadding((int) (6 * dp), (int) (6 * dp), (int) (6 * dp), (int) (6 * dp));
        String[] names = {getString(R.string.gtv_home), getString(R.string.gtv_apps), getString(R.string.gtv_inputs)};
        for (int i = 0; i < 3; i++) {
            final int tab = i;
            TextView t = pill(names[i]);
            if (i == TAB_HOME) {
                t.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_home, 0, 0, 0);
                t.setCompoundDrawablePadding((int) (8 * dp));
            }
            t.setOnFocusChangeListener((v, has) -> { if (has) showTab(tab); styleTabs(); });
            t.setOnKeyListener((v, code, e) -> {
                if (e.getAction() != KeyEvent.ACTION_DOWN || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
                focusContent();
                return true;
            });
            tabs[i] = t;
            bar.addView(t);
        }
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = (int) (48 * dp);
        lp.topMargin = (int) (20 * dp);
        root.addView(bar, lp);

        LinearLayout right = glassStrip();
        right.setGravity(Gravity.CENTER_VERTICAL);
        right.setPadding((int) (6 * dp), (int) (6 * dp), (int) (18 * dp), (int) (6 * dp));
        TextView settings = pill("");
        settings.setContentDescription(getString(R.string.launcher_settings));
        settings.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_settings, 0, 0, 0);
        settings.setPadding((int) (12 * dp), (int) (8 * dp), (int) (12 * dp), (int) (8 * dp));
        settings.setOnClickListener(v -> startActivityForResult(new Intent(this, LauncherSettingsActivity.class), SETTINGS_REQUEST));
        settings.setOnFocusChangeListener((v, has) -> stylePill((TextView) v, has, false));
        settings.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            focusContent();
            return true;
        });
        right.addView(settings);
        clock = new TextView(this);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        clock.setPadding((int) (14 * dp), 0, 0, 0);
        right.addView(clock);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        rp.rightMargin = (int) (48 * dp);
        rp.topMargin = (int) (20 * dp);
        root.addView(right, rp);
        styleTabs();
    }

    private void buildBanner() {
        banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x24FFFFFF);
        g.setStroke((int) Math.max(1, dp), 0x3DFFFFFF);
        g.setCornerRadius(24 * dp);
        banner.setBackground(g);
        banner.setPadding((int) (28 * dp), (int) (20 * dp), (int) (28 * dp), (int) (22 * dp));
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        title.setSingleLine(true);
        banner.addView(title);
        subtitle = new TextView(this);
        subtitle.setTextColor(0xFFC3CAD4);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        banner.addView(subtitle);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (420 * dp), -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = (int) (48 * dp);
        lp.topMargin = (int) (110 * dp);
        root.addView(banner, lp);
    }

    /** The banner and glow follow the selected app. */
    private void showApp(App a) {
        title.setText(a.label);
        long when = LastOpened.get(this, a.pkg);
        subtitle.setText(when > 0 ? getString(R.string.last_opened, LastOpened.describe(this, when)) : "");
        subtitle.setVisibility(when > 0 ? View.VISIBLE : View.GONE);
        glowDrawable.setColors(new int[]{(a.color & 0x00FFFFFF) | 0xA6000000, (a.color & 0x00FFFFFF) | 0x33000000, 0x00000000});
    }

    // ---- pages -----------------------------------------------------------------------------------

    /**
     * Your apps (one row), then Other apps: as many rows as "Rows below dock" (0 = none), each as wide as
     * "Apps per row". The page scrolls when the rows don't fit; every app is still in the Apps tab.
     */
    private View buildHome() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        if (!dock.isEmpty()) {
            page.addView(header(getString(R.string.gtv_your_apps)));
            firstHomeRow = row(dock);
            page.addView(firstHomeRow);
        }
        List<App> shown = others.subList(0, Math.min(others.size(), cols * rowsBelow));
        if (!shown.isEmpty()) {
            page.addView(header(getString(R.string.gtv_other_apps)));
            RecyclerView r = grid(new ArrayList<>(shown));
            int rows = (shown.size() + cols - 1) / cols;
            r.setNestedScrollingEnabled(false);
            r.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (rows * (tileH + 18 * dp) + 20 * dp)));
            page.addView(r);
            if (firstHomeRow == null) firstHomeRow = r;
        }
        homeScroll = new ScrollView(this);
        homeScroll.setVerticalScrollBarEnabled(false);
        homeScroll.addView(page, new FrameLayout.LayoutParams(-1, -2));
        return homeScroll;
    }

    /** A grid of tiles exactly cols wide: the right padding leaves room for the last tile's 18 dp gap. */
    private RecyclerView grid(List<App> apps) {
        RecyclerView r = new RecyclerView(this);
        r.setLayoutManager(new GridLayoutManager(this, cols));
        r.setAdapter(new TileAdapter(apps));
        r.setPadding((int) (48 * dp), (int) (10 * dp), (int) (30 * dp), (int) (10 * dp));
        r.setClipToPadding(false);
        r.setClipChildren(false);
        return r;
    }

    private View buildApps() {
        appsGrid = grid(all);
        appsGrid.setPadding((int) (48 * dp), (int) (8 * dp), (int) (30 * dp), (int) (40 * dp));
        appsGrid.setClipToPadding(false);
        appsGrid.setClipChildren(false);
        return appsGrid;
    }

    private View buildInputs() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        // Nothing clips the selected input's 1.1× zoom (it was cut at the row's edges).
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.addView(header(getString(R.string.gtv_inputs)));
        inputsRow = new LinearLayout(this);
        inputsRow.setPadding((int) (48 * dp), (int) (10 * dp), 0, (int) (10 * dp));
        inputsRow.setClipChildren(false);
        inputsRow.setClipToPadding(false);
        for (InputPreview.Port p : InputPreview.ports(this)) {
            TextView t = new TextView(this);
            t.setText(p.label);
            t.setTextColor(Color.WHITE);
            t.setGravity(Gravity.CENTER);
            // Long names ("HDMI 3 (eARC/ARC)") use two lines and shrink to fit the tile.
            t.setMaxLines(2);
            t.setPadding((int) (8 * dp), (int) (4 * dp), (int) (8 * dp), (int) (4 * dp));
            t.setAutoSizeTextTypeUniformWithConfiguration(11, 16, 1, TypedValue.COMPLEX_UNIT_SP);
            t.setFocusable(true);
            t.setBackground(tileBackground(0xFF1B2029));
            t.setOnClickListener(v -> openInput(p.id));
            t.setOnFocusChangeListener((v, has) -> focusTile(v, has));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.rightMargin = (int) (18 * dp);
            inputsRow.addView(t, lp);
        }
        page.addView(inputsRow);
        return page;
    }

    private void openInput(String id) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(id), "vnd.android.cursor.item/channel")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            android.widget.Toast.makeText(this, R.string.not_available, android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void showTab(int tab) {
        currentTab = tab;
        homePage.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.GONE);
        appsPage.setVisibility(tab == TAB_APPS ? View.VISIBLE : View.GONE);
        inputsPage.setVisibility(tab == TAB_INPUTS ? View.VISIBLE : View.GONE);
        // The banner belongs to Home; Apps and Inputs use the full height.
        banner.setVisibility(tab == TAB_HOME ? View.VISIBLE : View.INVISIBLE);
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) content.getLayoutParams();
        int top = (int) ((tab == TAB_HOME ? 300 : 90) * dp);
        if (lp != null && lp.topMargin != top) {
            lp.topMargin = top;
            content.setLayoutParams(lp);
        }
    }

    private void focusContent() {
        if (currentTab == TAB_HOME && firstHomeRow != null) focusFirst(firstHomeRow);
        else if (currentTab == TAB_APPS) focusFirst(appsGrid);
        else if (currentTab == TAB_INPUTS && inputsRow.getChildCount() > 0) inputsRow.getChildAt(0).requestFocus();
    }

    private static void focusFirst(RecyclerView r) {
        RecyclerView.ViewHolder vh = r.findViewHolderForAdapterPosition(0);
        if (vh != null) vh.itemView.requestFocus();
        else r.requestFocus();
    }

    private boolean isTab(View v) {
        for (TextView t : tabs) if (t == v) return true;
        return false;
    }

    // ---- rows and tiles -------------------------------------------------------------------------

    private TextView header(String text) {
        TextView h = new TextView(this);
        h.setText(text);
        h.setTextColor(0xFFDFE5EE);
        h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        h.setPadding((int) (48 * dp), (int) (14 * dp), 0, (int) (6 * dp));
        return h;
    }

    private RecyclerView row(List<App> apps) {
        RecyclerView r = new RecyclerView(this);
        r.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        r.setAdapter(new TileAdapter(apps));
        r.setPadding((int) (48 * dp), (int) (10 * dp), (int) (48 * dp), (int) (10 * dp));
        r.setClipToPadding(false);
        r.setClipChildren(false);
        r.setLayoutParams(new LinearLayout.LayoutParams(-1, (int) (tileH + 20 * dp)));
        return r;
    }

    private final class TileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<App> apps;
        TileAdapter(List<App> apps) { this.apps = apps; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout card = new FrameLayout(GtvActivity.this);
            card.setFocusable(true);
            RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(tileW, tileH);
            lp.rightMargin = (int) (18 * dp);
            lp.bottomMargin = (int) (18 * dp);
            card.setLayoutParams(lp);
            card.setClipToOutline(true);
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 12 * dp); }
            });
            ImageView img = new ImageView(GtvActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
            TextView name = new TextView(GtvActivity.this);
            name.setTextColor(Color.WHITE);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            name.setGravity(Gravity.CENTER);
            name.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
            card.addView(name, new FrameLayout.LayoutParams(-1, -1));
            Fonts.apply(card);
            return new RecyclerView.ViewHolder(card) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            App a = apps.get(position);
            FrameLayout card = (FrameLayout) h.itemView;
            ImageView img = (ImageView) card.getChildAt(0);
            TextView name = (TextView) card.getChildAt(1);
            card.setBackground(tileBackground(a.banner != null ? 0xFF1B2029 : darker(a.color)));
            if (a.banner != null) {
                img.setImageDrawable(a.banner);
                name.setText("");
            } else {
                img.setImageDrawable(null);
                name.setText(a.label);
            }
            card.setOnClickListener(v -> open(a));
            card.setOnLongClickListener(v -> { menu(a); return true; });
            card.setOnFocusChangeListener((v, has) -> {
                focusTile(v, has);
                if (has) showApp(a);
                if (has && apps == dock && homeScroll != null) homeScroll.smoothScrollTo(0, 0);
            });
            // Up from Your apps always goes to the Home tab, not the tab that happens to be above.
            card.setOnKeyListener(apps != dock ? null : (v, code, e) -> {
                if (code != KeyEvent.KEYCODE_DPAD_UP || moving != null) return false;
                if (e.getAction() == KeyEvent.ACTION_DOWN) tabs[TAB_HOME].requestFocus();
                return true;
            });
        }

        @Override public int getItemCount() { return apps.size(); }
    }

    private void focusTile(View v, boolean has) {
        v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).translationZ(has ? 8 * dp : 0).setDuration(140).start();
        v.setForeground(has ? ring(Color.WHITE) : null);
    }

    private static final int MOVE_COLOR = 0xFF6EC1FF;

    private GradientDrawable ring(int color) {
        GradientDrawable r = new GradientDrawable();
        r.setCornerRadius(12 * dp);
        r.setStroke((int) ((color == MOVE_COLOR ? 4 : 3) * dp), color);
        return r;
    }

    private GradientDrawable tileBackground(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(12 * dp);
        return g;
    }

    private static int darker(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] *= 0.7f;
        return Color.HSVToColor(hsv);
    }

    // ---- glass pills ----------------------------------------------------------------------------

    private LinearLayout glassStrip() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x26FFFFFF);
        g.setStroke((int) Math.max(1, dp), 0x33FFFFFF);
        g.setCornerRadius(28 * dp);
        l.setBackground(g);
        return l;
    }

    private TextView pill(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        t.setPadding((int) (20 * dp), (int) (8 * dp), (int) (20 * dp), (int) (8 * dp));
        t.setFocusable(true);
        stylePill(t, false, false);
        return t;
    }

    private void styleTabs() {
        for (int i = 0; i < 3; i++) stylePill(tabs[i], tabs[i].hasFocus(), i == currentTab);
    }

    private void stylePill(TextView t, boolean focused, boolean selected) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(22 * dp);
        // Selected (not focused) is a dark glass pill with a white edge, so it stays readable over a bright glow.
        if (focused) g.setColor(Color.WHITE);
        else if (selected) {
            g.setColor(0x991B2029);
            g.setStroke((int) Math.max(1, 1.5f * dp), 0xCCFFFFFF);
        } else g.setColor(Color.TRANSPARENT);
        t.setBackground(g);
        int text = focused ? 0xFF111111 : selected ? Color.WHITE : 0xFFE3E8EF;
        t.setTextColor(text);
        t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(text));
    }
}
