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
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvContract;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
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
import android.widget.PopupWindow;
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
    /** Home: one grid of Your apps (first row) and Favorite apps (Rows below dock), with a header over each. */
    private RecyclerView homeGrid, appsGrid;
    private final List<Object> homeItems = new ArrayList<>();
    private LinearLayout topBar;
    /** Apps per row (Launcher settings) and rows of Favorite apps under Your apps (Rows below dock). */
    private int cols, rowsBelow;
    private List<String> dockApps;
    /** The Home tile being moved (hold OK, Move), or null. */
    private App moving;
    /** False when the banner can't fit between the top bar and the rows (then only the glow shows). */
    private boolean bannerFits = true, bannerCompact;
    /** False when the Home rows reach up to where the performance card would sit. */
    private boolean perfFits = true;
    /** Every app in Home order (saved order, dock_apps, name): the first row is Your apps, then Favorites. */
    private final List<App> ordered = new ArrayList<>();
    /** The owner's Favorite apps (packages, in their order) shown under Your apps. */
    private List<String> favPackages = new ArrayList<>();
    /** The gear or hold-OK menu while it is on screen, so Home and pausing can close it. */
    private PopupWindow openPopup;
    /** The performance card (Launcher settings), its reader and the thread it reads on. */
    private LinearLayout perfCard;
    private Stats stats;
    private HandlerThread perfThread;
    private volatile Handler perfBg;
    private int framesDrawn;
    private long framesSince;
    private LinearLayout inputsRow;
    private int currentTab = TAB_HOME;
    private int tileW, tileH;
    /** Left padding of the grids and the banner: 48 dp, more when tiles had to shrink (keeps the grid centred). */
    private int sidePad;
    private static final int CONTENT_TOP_DP = 90, HEADER_DP = 40, ARROW_DP = 30, GRID_PAD_DP = 10, GAP_DP = 18;
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
        closeMenu();
        // Home leaves the selection on the Home tab: Down from there enters Your apps.
        tabs[TAB_HOME].requestFocus();
        if (currentTab != TAB_HOME) showTab(TAB_HOME);
        styleTabs();
        if (homeGrid != null) homeGrid.scrollToPosition(0);
    }

    private void build() {
        dp = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        cols = Math.max(5, Math.min(8, cfg.columns));
        rowsBelow = Math.max(0, Math.min(3, cfg.rowsBelowDock));
        dockApps = cfg.dockApps;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        tileW = (int) ((screenW - 2 * 48 * dp - (cols - 1) * GAP_DP * dp) / cols);
        tileH = tileW * 9 / 16;
        // Home must fit under the top bar: Your apps + Favorite rows, their headers, padding and the chevron.
        // When it doesn't (5 or 6 per row with 2-3 rows), tiles shrink to fit and the grid stays centred.
        int rowsShown = 1 + rowsBelow;
        float fixed = (CONTENT_TOP_DP + 2 * GRID_PAD_DP + (rowsBelow > 0 ? 2 : 1) * HEADER_DP + ARROW_DP
                + rowsShown * GAP_DP) * dp;
        int maxTileH = (int) ((screenH - fixed) / rowsShown);
        if (tileH > maxTileH) {
            tileH = maxTileH;
            tileW = tileH * 16 / 9;
        }
        sidePad = Math.max((int) (48 * dp), (int) ((screenW - cols * tileW - (cols - 1) * GAP_DP * dp) / 2));
        loadApps(cfg);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D12);
        setContentView(root);
        buildBackground(cfg);
        buildTopBar();
        buildPerfCard(cfg);
        buildBanner();
        content = new FrameLayout(this);
        content.setClipChildren(false);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-1, -1);
        clp.topMargin = (int) (CONTENT_TOP_DP * dp);
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
        root.post(this::focusHomeFirst);
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
        startPerf();
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeMenu();
        stopPerf();
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
        ordered.clear();
        ordered.addAll(all);
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
        List<String> saved = Favorites.load(this);
        if (saved == null) {
            // First run (or an unreadable file): start with the apps the rows show today, so nothing disappears.
            saved = new ArrayList<>();
            for (int i = cols; i < ordered.size() && saved.size() < Favorites.MAX; i++) {
                saved.add(ordered.get(i).pkg);
            }
        }
        favPackages = new ArrayList<>(saved);
        splitOrdered();
    }

    /** Your apps = the first row of the Home order; Favorites = the owner's list, minus anything in Your apps. */
    private void splitOrdered() {
        dock.clear();
        others.clear();
        for (int i = 0; i < ordered.size() && i < cols; i++) dock.add(ordered.get(i));
        for (String pkg : favPackages) {
            for (App a : ordered) {
                if (a.pkg.equals(pkg) && !dock.contains(a) && !others.contains(a)) others.add(a);
            }
        }
        homeItems.clear();
        if (!dock.isEmpty()) {
            homeItems.add(getString(R.string.gtv_your_apps));
            homeItems.addAll(dock);
        }
        int fav = favouritesShown();
        if (fav > 0) {
            homeItems.add(getString(R.string.gtv_other_apps));
            homeItems.addAll(others.subList(0, fav));
        }
    }

    private int favouritesShown() {
        return Math.min(others.size(), cols * rowsBelow);
    }

    /** How many apps Home shows: Your apps plus the Favorite rows. */
    private int homeCount() {
        return dock.size() + favouritesShown();
    }

    /** The Home tile at this place: Your apps first, then the Favorites that are shown. */
    private App homeApp(int index) {
        if (index < 0) return null;
        if (index < dock.size()) return dock.get(index);
        int fav = index - dock.size();
        return fav < favouritesShown() ? others.get(fav) : null;
    }

    private int homeIndex(App a) {
        int i = dock.indexOf(a);
        if (i >= 0) return i;
        i = others.indexOf(a);
        return i >= 0 && i < favouritesShown() ? dock.size() + i : -1;
    }

    /** Adds or removes a Favorite and rebuilds Home in place. */
    private void setFavourite(App a, boolean favourite) {
        try {
            favPackages = favourite ? new ArrayList<>(Favorites.added(favPackages, a.pkg))
                    : new ArrayList<>(Favorites.removed(favPackages, a.pkg));
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.gtv_favorites_full, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Favorites.save(this, favPackages)) {
            Toast.makeText(this, R.string.gtv_favorite_not_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        splitOrdered();
        homeGrid.getAdapter().notifyDataSetChanged();
        Toast.makeText(this, getString(favourite ? R.string.gtv_added_favorite : R.string.gtv_removed_favorite,
                a.label), Toast.LENGTH_SHORT).show();
    }

    private static String key(App a) {
        return a.component.flattenToShortString();
    }

    /**
     * Saves the moved dock in order.txt, the file the Apple TV layout reads too. Saved keys this screen does not
     * show (the Apple layout's cards) keep their places; the apps take the remaining places in their new order.
     */
    private void saveOrder() {
        if (!Favorites.save(this, favPackages)) {
            Toast.makeText(this, R.string.gtv_favorite_not_saved, Toast.LENGTH_SHORT).show();
        }
        List<String> mine = new ArrayList<>();
        for (App a : ordered) mine.add(key(a));
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

    /** Hold OK on a tile: Open, Move (Home only), add or remove a Favorite, and App info. */
    private void menu(App a, View card) {
        boolean onHome = currentTab == TAB_HOME && homeIndex(a) >= 0;
        boolean favourite = others.contains(a) && others.indexOf(a) < favouritesShown();
        List<Runnable> actions = new ArrayList<>();
        List<Integer> titles = new ArrayList<>();
        titles.add(R.string.gtv_open);
        actions.add(() -> open(a));
        if (onHome) {
            titles.add(R.string.gtv_move);
            actions.add(() -> startMove(a));
        }
        if (!dock.contains(a)) {
            titles.add(favourite ? R.string.gtv_remove_favorite : R.string.gtv_add_favorite);
            actions.add(() -> setFavourite(a, !favourite));
        }
        titles.add(R.string.library_app_info);
        actions.add(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException ignored) {
            }
        });
        showMenu(card, a.label, titles, actions);
    }

    // ---- moving a Home tile, and keys -------------------------------------------------------------

    private void startMove(App a) {
        moving = a;
        Toast.makeText(this, R.string.gtv_move_hint, Toast.LENGTH_LONG).show();
        refocusMoving();
    }

    /**
     * Move mode: arrows move the tile through Your apps and the Favorite rows as one list (Left/Right one place,
     * Up/Down one row); OK or Back puts it down and saves. Otherwise: Up from a page's top row goes to that page's
     * tab, and Down from Home's lowest row goes to the Apps tab.
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        if (moving == null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && handleEdgeKey(code)) return true;
            return super.dispatchKeyEvent(e);
        }
        boolean drop = code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                || code == KeyEvent.KEYCODE_BACK;
        if (code == KeyEvent.KEYCODE_HOME) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN) return true;   // the up of OK/Back must not open the app
        int from = homeIndex(moving), to = from;
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) to = from - 1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) to = from + 1;
        else if (code == KeyEvent.KEYCODE_DPAD_UP) to = from - cols;
        else if (code == KeyEvent.KEYCODE_DPAD_DOWN) to = from + cols;
        else if (drop && e.getRepeatCount() == 0) {
            endMove();
            return true;
        }
        if (from >= 0 && to != from && to >= 0 && to < homeCount() && moveTo(from, to)) {
            splitOrdered();
            homeGrid.getAdapter().notifyDataSetChanged();
            homeGrid.post(this::refocusMoving);
        }
        return true;
    }

    /**
     * Moves the carried tile from one Home place to another. Your apps keep the shared order (order.txt, the
     * dock the Apple TV layout shows too); the Favorite rows keep their own list. Crossing between the two swaps
     * the apps, so neither section changes size.
     */
    private boolean moveTo(int from, int to) {
        App carried = homeApp(from), other = homeApp(to);
        if (carried == null || other == null) return false;
        boolean fromDock = from < dock.size(), toDock = to < dock.size();
        if (fromDock && toDock) {
            Collections.swap(ordered, ordered.indexOf(carried), ordered.indexOf(other));
        } else if (!fromDock && !toDock) {
            int a = favPackages.indexOf(carried.pkg), b = favPackages.indexOf(other.pkg);
            if (a < 0 || b < 0) return false;
            favPackages.add(b, favPackages.remove(a));
        } else {
            // One is in Your apps and one is a Favorite: trade places.
            App inDock = fromDock ? carried : other, fav = fromDock ? other : carried;
            int at = favPackages.indexOf(fav.pkg);
            if (at < 0) return false;
            favPackages.set(at, inDock.pkg);
            int slot = ordered.indexOf(inDock), favAt = ordered.indexOf(fav);
            if (slot < 0 || favAt < 0) return false;
            ordered.set(slot, fav);
            ordered.set(favAt, inDock);
        }
        return true;
    }

    private boolean handleEdgeKey(int code) {
        View f = getCurrentFocus();
        if (f == null || !inside(f, content)) return false;
        if (code == KeyEvent.KEYCODE_DPAD_UP) {
            View next = f.focusSearch(View.FOCUS_UP);
            if (next != null && inside(next, content)) return false;
            tabs[currentTab].requestFocus();
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && currentTab == TAB_HOME && f.getParent() == homeGrid) {
            int pos = homeGrid.getChildAdapterPosition(f);
            if (pos < 0 || !(homeItems.get(pos) instanceof App)) return false;
            int at = homeIndex((App) homeItems.get(pos)), n = homeCount();
            int lastRowStart = n <= cols ? 0 : cols + ((n - cols - 1) / cols) * cols;
            if (at < 0 || at < lastRowStart) return false;
            openAppsAt(at % cols);
            return true;
        }
        return false;
    }

    /** Down from Home's lowest row: the Apps tab, on the app in the same column of its top row. */
    private void openAppsAt(int column) {
        // Focus the Apps pill first (its listener switches the page): hiding Home while it holds the focus would
        // send focus to the Home pill, whose listener re-showed Home under the Apps grid.
        tabs[TAB_APPS].requestFocus();
        if (currentTab != TAB_APPS) showTab(TAB_APPS);
        styleTabs();
        if (all.isEmpty()) return;
        appsGrid.scrollToPosition(0);
        int pos = Math.min(column, all.size() - 1);
        appsGrid.post(() -> {
            RecyclerView.ViewHolder vh = appsGrid.findViewHolderForAdapterPosition(pos);
            if (vh != null) vh.itemView.requestFocus();
            else focusFirst(appsGrid);
        });
    }

    private static boolean inside(View v, View parent) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == parent) return true;
        }
        return false;
    }

    private void endMove() {
        App a = moving;
        moving = null;
        saveOrder();
        homeGrid.getAdapter().notifyDataSetChanged();
        int pos = homeItems.indexOf(a);
        homeGrid.post(() -> {
            RecyclerView.ViewHolder vh = homeGrid.findViewHolderForAdapterPosition(pos);
            if (vh != null) vh.itemView.requestFocus();
        });
    }

    private void refocusMoving() {
        if (moving == null) return;
        RecyclerView.ViewHolder vh = homeGrid.findViewHolderForAdapterPosition(homeItems.indexOf(moving));
        if (vh == null) return;
        vh.itemView.requestFocus();
        vh.itemView.setForeground(ring(MOVE_COLOR));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (moving != null) endMove();   // leaving Home while moving keeps the order so far
    }

    // ---- menus (the Google TV look: glass panel, white pill on the selected row) --------------

    /** OK on the gear: Launcher settings or the TV's own settings, as in the Apple TV layout. */
    private void showSettingsChoice(View anchor) {
        List<Integer> titles = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        titles.add(R.string.launcher_settings);
        actions.add(() -> startActivityForResult(new Intent(this, LauncherSettingsActivity.class), SETTINGS_REQUEST));
        titles.add(R.string.tv_settings);
        actions.add(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        });
        showMenu(anchor, null, titles, actions);
    }

    /**
     * The one menu both the gear and hold OK use, so they match the tabs: a glass panel under (or over) the
     * tile, each row a pill that turns white with dark text when selected.
     */
    private void showMenu(View anchor, String heading, List<Integer> titles, List<Runnable> actions) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF21B2029);
        bg.setStroke((int) Math.max(1, 1.5f * dp), 0x33FFFFFF);
        bg.setCornerRadius(20 * dp);
        panel.setBackground(bg);
        panel.setPadding((int) (8 * dp), (int) (8 * dp), (int) (8 * dp), (int) (8 * dp));
        panel.setElevation(12 * dp);

        PopupWindow popup = new PopupWindow(panel, (int) (300 * dp), ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setOnDismissListener(() -> { if (openPopup == popup) openPopup = null; });
        openPopup = popup;

        if (heading != null) {
            TextView h = new TextView(this);
            h.setText(heading);
            h.setTextColor(0xFF9AA5B4);
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            h.setSingleLine(true);
            h.setPadding((int) (16 * dp), (int) (6 * dp), (int) (16 * dp), (int) (8 * dp));
            panel.addView(h);
        }
        for (int i = 0; i < titles.size(); i++) {
            panel.addView(menuRow(popup, getString(titles.get(i)), actions.get(i)));
        }
        Fonts.apply(panel);
        popup.showAsDropDown(anchor, 0, (int) (10 * dp), Gravity.END);
        panel.getChildAt(heading == null ? 0 : 1).requestFocus();
    }

    private TextView menuRow(PopupWindow popup, String title, Runnable action) {
        TextView row = new TextView(this);
        row.setText(title);
        row.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.setSingleLine(true);
        row.setFocusable(true);
        row.setPadding((int) (16 * dp), (int) (11 * dp), (int) (16 * dp), (int) (11 * dp));
        styleMenuRow(row, false);
        row.setOnFocusChangeListener((v, has) -> styleMenuRow((TextView) v, has));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = (int) (2 * dp);
        row.setLayoutParams(lp);
        // The OK-up that opened the menu must not activate its first row.
        final boolean[] pressed = {false};
        row.setOnKeyListener((v, code, event) -> {
            if (code != KeyEvent.KEYCODE_DPAD_CENTER && code != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (event.getRepeatCount() == 0) pressed[0] = true;
                return true;
            }
            boolean activate = pressed[0];
            pressed[0] = false;
            if (activate) {
                popup.dismiss();
                action.run();
            }
            return true;
        });
        row.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        return row;
    }

    private void styleMenuRow(TextView row, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(14 * dp);
        g.setColor(focused ? Color.WHITE : Color.TRANSPARENT);
        row.setBackground(g);
        row.setTextColor(focused ? 0xFF111111 : 0xFFE3E8EF);
    }

    /** Nothing of ours stays on screen when Home is pressed or another app comes in front. */
    private void closeMenu() {
        if (openPopup != null) {
            openPopup.dismiss();
            openPopup = null;
        }
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
        topBar = bar;
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
        settings.setOnClickListener(this::showSettingsChoice);
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

    /**
     * A small glass card under the clock with what the TV is doing. Off unless Launcher settings turns it on:
     * while it is off nothing is read and no thread runs.
     */
    private void buildPerfCard(Config cfg) {
        if (!getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(Config.PERF_CARD_KEY, false)) return;
        stats = new Stats(this);
        perfCard = new LinearLayout(this);
        perfCard.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xCC0B0D12);
        g.setStroke((int) Math.max(1, dp), 0x33FFFFFF);
        g.setCornerRadius(18 * dp);
        perfCard.setBackground(g);
        perfCard.setPadding((int) (16 * dp), (int) (12 * dp), (int) (18 * dp), (int) (12 * dp));
        TextView head = new TextView(this);
        head.setText(R.string.perf_title);
        head.setTextColor(0xFF8F9AAB);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        head.setPadding(0, 0, 0, (int) (6 * dp));
        perfCard.addView(head);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (230 * dp), -2, Gravity.TOP | Gravity.END);
        lp.rightMargin = sidePad;
        lp.topMargin = (int) (84 * dp);
        root.addView(perfCard, lp);
    }

    /** The figures on the card now, so it can be re-laid out without waiting for the next reading. */
    private java.util.List<Stats.Item> perfItems = java.util.Collections.emptyList();

    /** One row of the card: the figure's name on the left, its value on the right. */
    private void setPerfRows(java.util.List<Stats.Item> items) {
        perfItems = items;
        if (perfCard == null) return;
        int rows = perfCard.getChildCount() - 1;            // the heading stays
        for (int i = rows; i < items.size(); i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            TextView name = new TextView(this);
            name.setTextColor(0xFFC3CAD4);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView value = new TextView(this);
            value.setTextColor(Color.WHITE);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            row.addView(value);
            Fonts.apply(row);
            perfCard.addView(row);
        }
        for (int i = items.size(); i < rows; i++) perfCard.getChildAt(i + 1).setVisibility(View.GONE);
        for (int i = 0; i < items.size(); i++) {
            LinearLayout row = (LinearLayout) perfCard.getChildAt(i + 1);
            row.setVisibility(View.VISIBLE);
            ((TextView) row.getChildAt(0)).setText(items.get(i).labelRes);
            ((TextView) row.getChildAt(1)).setText(items.get(i).value);
        }
    }

    /** Reads the figures off the main thread every two seconds while Home is in front. */
    private void startPerf() {
        if (perfCard == null || perfThread != null) return;
        perfThread = new HandlerThread("perf");
        perfThread.start();
        Handler bg = new Handler(perfThread.getLooper());
        perfBg = bg;
        framesDrawn = 0;
        framesSince = SystemClock.elapsedRealtime();
        countFrame();
        bg.post(new Runnable() {
            @Override public void run() {
                if (perfBg != bg) return;
                java.util.List<Stats.Item> items = stats.read();
                    runOnUiThread(() -> {
                    if (perfBg != bg) return;
                    setPerfRows(items);
                    fitBanner();   // the card grows as figures arrive; keep it clear of the rows
                });
                if (perfBg == bg) bg.postDelayed(this, 2000);
            }
        });
    }

    private void stopPerf() {
        if (perfThread == null) return;
        perfBg.removeCallbacksAndMessages(null);
        perfBg = null;
        perfThread.quit();
        perfThread = null;
    }

    /** Counts drawn frames for the card's frame rate; stops with the card. */
    private void countFrame() {
        if (perfCard == null || perfThread == null) return;
        android.view.Choreographer.getInstance().postFrameCallback(t -> {
            framesDrawn++;
            long now = SystemClock.elapsedRealtime();
            if (now - framesSince >= 1000) {
                stats.frameRate = (int) Math.round(framesDrawn * 1000.0 / (now - framesSince));
                framesDrawn = 0;
                framesSince = now;
            }
            countFrame();
        });
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
        lp.leftMargin = sidePad;
        lp.topMargin = (int) (110 * dp);
        root.addView(banner, lp);
    }

    /** The banner and glow follow the selected app. */
    private void showApp(App a) {
        title.setText(a.label);
        long when = LastOpened.get(this, a.pkg);
        subtitle.setText(when > 0 ? getString(R.string.last_opened, LastOpened.describe(this, when)) : "");
        subtitle.setVisibility(when > 0 && !bannerCompact ? View.VISIBLE : View.GONE);
        glowDrawable.setColors(new int[]{(a.color & 0x00FFFFFF) | 0xA6000000, (a.color & 0x00FFFFFF) | 0x33000000, 0x00000000});
    }

    // ---- pages -----------------------------------------------------------------------------------

    /**
     * Home: Your apps, then as many Favorite rows as "Rows below dock" (0 = none), each "Apps per row" wide, sitting
     * on the bottom edge with the double chevron under the lowest row (Down opens the Apps tab). The banner takes
     * the room left above them.
     */
    private View buildHome() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.BOTTOM);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        homeGrid = grid(homeItems);
        homeGrid.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        page.addView(homeGrid);
        ImageView more = new ImageView(this);
        more.setImageResource(R.drawable.ic_more_below);
        more.setAlpha(0.75f);
        LinearLayout.LayoutParams mlp = new LinearLayout.LayoutParams(-2, (int) ((ARROW_DP - 10) * dp));
        mlp.gravity = Gravity.CENTER_HORIZONTAL;
        mlp.bottomMargin = (int) (10 * dp);
        page.addView(more, mlp);
        // Refit the banner only when the rows' block actually moves (not on every layout pass).
        homeGrid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (t != ot || b != ob) v.post(this::fitBanner);
        });
        return page;
    }

    /**
     * Places the banner between the top bar and the Home rows: full size where it fits, a smaller title without
     * the "Last opened" line where only that fits, otherwise hidden. Never overlaps the top bar or the rows.
     */
    private void fitBanner() {
        if (topBar == null || homeGrid.getHeight() == 0) return;
        int gap = (int) (12 * dp);
        int roomTop = topBar.getBottom() + gap;
        int roomBottom = content.getTop() + homePage.getTop() + homeGrid.getTop() - gap;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) banner.getLayoutParams();
        setBannerCompact(false);
        int h = bannerHeight();
        int top;
        if (roomBottom - (int) (110 * dp) >= h) top = (int) (110 * dp);
        else if (roomBottom - roomTop >= h) top = roomTop;
        else {
            setBannerCompact(true);
            top = roomBottom - roomTop >= bannerHeight() ? roomTop : -1;
        }
        layoutPerfCard(roomBottom);
        bannerFits = top >= 0;
        if (bannerFits && lp.topMargin != top) {
            lp.topMargin = top;
            banner.setLayoutParams(lp);
        }
        banner.setVisibility(currentTab == TAB_HOME && bannerFits ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * The card hangs under the clock on the right, where the Home rows can reach up to it. It shows every figure
     * where there is room; where there is not, it drops to the few that matter (processor, memory, storage,
     * frames); and where even those do not fit, it steps aside altogether.
     */
    private void layoutPerfCard(int roomBottom) {
        if (perfCard == null) return;
        int cardTop = ((FrameLayout.LayoutParams) perfCard.getLayoutParams()).topMargin;
        perfFits = false;
        for (int pass = 0; pass < 2 && !perfFits; pass++) {
            boolean essentialOnly = pass == 1;
            for (int i = 0; i < perfItems.size() && i + 1 < perfCard.getChildCount(); i++) {
                perfCard.getChildAt(i + 1).setVisibility(
                        !essentialOnly || perfItems.get(i).essential ? View.VISIBLE : View.GONE);
            }
            perfCard.measure(View.MeasureSpec.makeMeasureSpec(perfCard.getLayoutParams().width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.UNSPECIFIED);
            perfFits = cardTop + perfCard.getMeasuredHeight() <= roomBottom;
        }
        perfCard.setVisibility(currentTab == TAB_HOME && perfFits ? View.VISIBLE : View.GONE);
    }

    private void setBannerCompact(boolean compact) {
        bannerCompact = compact;
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? 30 : 46);
        if (compact) subtitle.setVisibility(View.GONE);
    }

    /** The banner's height with its "Last opened" line (when not compact), whatever the current app shows. */
    private int bannerHeight() {
        CharSequence text = subtitle.getText();
        int vis = subtitle.getVisibility();
        subtitle.setText("Xg");
        subtitle.setVisibility(bannerCompact ? View.GONE : View.VISIBLE);
        banner.measure(View.MeasureSpec.makeMeasureSpec(banner.getLayoutParams().width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED);
        subtitle.setText(text);
        subtitle.setVisibility(vis);
        return banner.getMeasuredHeight();
    }

    /** A grid exactly cols wide (the right padding leaves room for the last tile's 18 dp gap); headers span a row. */
    private RecyclerView grid(List<?> items) {
        RecyclerView r = new RecyclerView(this);
        GridLayoutManager glm = new GridLayoutManager(this, cols);
        glm.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
            @Override public int getSpanSize(int position) {
                return items.get(position) instanceof String ? cols : 1;
            }
        });
        r.setLayoutManager(glm);
        r.setAdapter(new TileAdapter(items));
        r.setPadding(sidePad, (int) (GRID_PAD_DP * dp), sidePad - (int) (GAP_DP * dp), (int) (GRID_PAD_DP * dp));
        r.setClipToPadding(false);
        r.setClipChildren(false);
        r.setItemAnimator(null);
        return r;
    }

    private View buildApps() {
        appsGrid = grid(all);
        appsGrid.setPadding(sidePad, (int) (8 * dp), sidePad - (int) (GAP_DP * dp), (int) (40 * dp));
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
        inputsRow.setPadding(sidePad, (int) (10 * dp), 0, (int) (10 * dp));
        inputsRow.setClipChildren(false);
        inputsRow.setClipToPadding(false);
        for (InputPreview.Port p : InputPreview.ports(this)) {
            // Every port name sits at the same height; extra text in brackets ("eARC/ARC") is a small caption
            // at the bottom, so HDMI 3 lines up with the others.
            String label = String.valueOf(p.label);
            String name = label, caption = null;
            int open = label.indexOf(" (");
            if (open > 0 && label.endsWith(")")) {
                name = label.substring(0, open);
                caption = label.substring(open + 2, label.length() - 1);
            }
            FrameLayout tile = new FrameLayout(this);
            tile.setFocusable(true);
            tile.setBackground(tileBackground(0xFF1B2029));
            tile.setContentDescription(label);
            TextView n = new TextView(this);
            n.setText(name);
            n.setTextColor(Color.WHITE);
            n.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            n.setSingleLine(true);
            n.setGravity(Gravity.CENTER);
            n.setTranslationY(-tileH * 0.12f);
            tile.addView(n, new FrameLayout.LayoutParams(-1, -1));
            if (caption != null) {
                TextView c = new TextView(this);
                c.setText(caption);
                c.setTextColor(0xFFC3CAD4);
                c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                c.setSingleLine(true);
                FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
                clp.bottomMargin = (int) (tileH * 0.08f);
                tile.addView(c, clp);
            }
            tile.setOnClickListener(v -> openInput(p.id));
            tile.setOnFocusChangeListener((v, has) -> focusTile(v, has));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.rightMargin = (int) (18 * dp);
            inputsRow.addView(tile, lp);
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
        // The banner and the performance card belong to Home (when they fit above the rows); Apps and Inputs
        // use the full height, and a card left on screen there would sit over the grid's right-hand tiles.
        banner.setVisibility(tab == TAB_HOME && bannerFits ? View.VISIBLE : View.INVISIBLE);
        if (perfCard != null) perfCard.setVisibility(tab == TAB_HOME && perfFits ? View.VISIBLE : View.GONE);
    }

    /** Home's first app (Your apps, first place). */
    private void focusHomeFirst() {
        if (homeGrid == null || dock.isEmpty()) return;
        RecyclerView.ViewHolder vh = homeGrid.findViewHolderForAdapterPosition(homeItems.indexOf(dock.get(0)));
        if (vh != null) vh.itemView.requestFocus();
    }

    private void focusContent() {
        if (currentTab == TAB_HOME) focusHomeFirst();
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
        h.setPadding(sidePad, (int) (14 * dp), 0, (int) (6 * dp));
        return h;
    }

    /** Tiles of one grid; a String item is a row header ("Your apps", "Favorite apps") across the grid. */
    private final class TileAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int HEADER = 1, TILE = 0;
        private final List<?> items;
        TileAdapter(List<?> items) { this.items = items; }

        @Override public int getItemViewType(int position) {
            return items.get(position) instanceof String ? HEADER : TILE;
        }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == HEADER) {
                TextView h = new TextView(GtvActivity.this);
                h.setTextColor(0xFFDFE5EE);
                h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
                h.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
                h.setPadding((int) (2 * dp), 0, 0, 0);
                h.setLayoutParams(new RecyclerView.LayoutParams(-1, (int) (HEADER_DP * dp)));
                Fonts.apply(h);
                return new RecyclerView.ViewHolder(h) { };
            }
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
            Object item = items.get(position);
            if (item instanceof String) {
                ((TextView) h.itemView).setText((String) item);
                return;
            }
            App a = (App) item;
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
            // A recycled card may still carry the zoom of the tile it showed before.
            boolean focused = card.isFocused();
            card.animate().cancel();
            card.setScaleX(focused ? 1.1f : 1f);
            card.setScaleY(focused ? 1.1f : 1f);
            card.setForeground(focused ? ring(a == moving ? MOVE_COLOR : Color.WHITE) : null);
            card.setOnClickListener(v -> open(a));
            card.setOnLongClickListener(v -> { menu(a, v); return true; });
            card.setOnFocusChangeListener((v, has) -> {
                focusTile(v, has);
                if (has) showApp(a);
            });
        }

        @Override public int getItemCount() { return items.size(); }
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
