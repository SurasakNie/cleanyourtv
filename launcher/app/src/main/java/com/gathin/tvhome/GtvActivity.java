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
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
 * Google TV-style Home: tabs (Home · Apps · Inputs), a banner naming the selected app over the wallpaper and
 * its colour glow, and rows of app banners. The only layout there is, since the Apple TV one was removed.
 * MainActivity stays the HOME component and opens this screen, so the TV's default-home setting never changes.
 * Built in code; no network, no new permission.
 */
public final class GtvActivity extends Activity {
    private static final int SETTINGS_REQUEST = 7;
    private static final int TAB_HOME = 0, TAB_APPS = 1, TAB_INPUTS = 2;

    /** Crash guard: the process id that is building this screen, cleared once it has drawn. */
    private static final String PREF_STARTING = "gtv_starting_pid";

    /** Set when building this screen threw, so the next HOME press shows the recovery screen, not a crash loop. */
    private static final String PREF_FAILED = "gtv_failed";

    /**
     * True when the last start of this screen never finished drawing in a process that has since died (it
     * crashed), or when building it threw outright. MainActivity shows its recovery screen instead of coming
     * back here; before the Apple TV layout was removed, falling back to that layout was what this guarded.
     */
    static boolean startFailed(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        int pid = prefs.getInt(PREF_STARTING, 0);
        return (pid != 0 && pid != android.os.Process.myPid()) || prefs.getBoolean(PREF_FAILED, false);
    }

    /** Records a failure to build this screen. Committed, not applied: the process may be about to go. */
    private static void recordFailure(Context ctx) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit()
                .remove(PREF_STARTING).putBoolean(PREF_FAILED, true).commit();
    }

    /** "Try again" on the recovery screen: forget the failure so this screen is given another go. */
    static void clearFailure(Context ctx) {
        ctx.getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).edit()
                .remove(PREF_STARTING).remove(PREF_FAILED).commit();
    }

    /**
     * One launchable app, with its artwork loaded once and kept. Loading it per tile instead was tried in 2.08 and
     * measured worse on this TV: every tile scrolling into view decoded a fresh copy, and the native heap climbed
     * from 21.8 MB to 38 MB and kept going. If this 22 MB is worth reclaiming, decode the banners at tile size
     * (about 207x116) rather than trading memory for repeated decoding.
     */
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
    /** Glow style (Launcher settings, Glow): where the glow sits and whether others join it. */
    private int glowStyle = GlowStyle.CLASSIC;
    /** Animations and the advanced extras (config.xml or Launcher settings), honoured here since 2.26. */
    private boolean animations = true, advancedEffects;
    /** Focus style (Launcher settings); the zoom is what every build before 2.26 did. */
    private int focusStyle = FOCUS_ZOOM;
    /** The colour the glow is showing, so an advanced fade knows where to start. */
    private int glowColor;
    private android.animation.ValueAnimator glowFade;
    private View[] extraGlows = new View[0];
    private GradientDrawable[] extraDrawables = new GradientDrawable[0];
    /** Slow drift moves the glow by itself; the ticker runs only while Home is in front. */
    private long driftStart;
    private final Runnable drift = new Runnable() {
        @Override public void run() {
            if (!resumed || currentTab != TAB_HOME) return;
            placeGlow(GlowStyle.drift((SystemClock.elapsedRealtime() - driftStart) / 1000.0));
            handler.postDelayed(this, 120);
        }
    };
    private TextView title, subtitle, clock;
    /** One dot per app in Your apps; the selected one stretches into a pill. */
    private LinearLayout dots;
    private int dotShown = -1;
    private android.animation.ValueAnimator dotAnimator;
    private LinearLayout banner;
    private final TextView[] tabs = new TextView[3];
    private FrameLayout content;
    private View homePage, appsPage, inputsPage;
    /** Home: one grid of Your apps (first row) and Favorite apps (Rows below dock), with a header over each. */
    private RecyclerView homeGrid, appsGrid, dockRow;
    /** The Inputs row on Home and its heading (Launcher settings, off by default). */
    private LinearLayout homeInputs;
    private TextView homeInputsHeader;
    /** Whether Home shows the Inputs row, read once with the rest of the settings. */
    private boolean inputsOnHome;
    /** The "Your apps" heading above the dock row; the banner and the card are fitted above it. */
    private TextView dockHeader;
    private final List<Object> homeItems = new ArrayList<>();
    /** Your apps: the owner's dock (DockApps), plus the + tile at the end. */
    private final List<Object> dockItems = new ArrayList<>();
    /** The packages in Your apps, in the owner's order; gtv_dock.txt, seeded from dock_apps. */
    private List<String> dockPackages = new ArrayList<>();
    /** The + tile's place in dockItems: its own type, so the adapter can tell it from an app. */
    private static final Object PLUS = new Object();
    private LinearLayout topBar;
    /** The clock and gear strip: hidden while the settings panel is open, which sits over that corner. */
    private LinearLayout topRight;
    /** Apps per row (Launcher settings) and rows of Favorite apps under Your apps (Rows below dock). */
    private int cols, rowsBelow;
    /** The banner title's size in sp (Launcher settings); the compact title is 16 less. */
    private int bannerSp = BANNER_SP_DEFAULT;
    /** Banner style (Launcher settings): name over the glow, the same on glass, no banner, or a live input. */
    private int bannerStyle = BANNER_NAME;
    /** The live input window in the banner (Banner: Live input), and whether it is tuned right now. */
    private InputPreview livePreview;
    private boolean liveRunning;
    private List<String> dockApps;
    /** The Home tile being moved (hold OK, Move), or null. */
    private App moving;
    /** False when the banner can't fit between the top bar and the rows (then only the glow shows). */
    private boolean bannerFits = true, bannerCompact;
    /** False when the Home rows reach up to where the performance card would sit. */
    private boolean perfFits = true;
    /** True while a tile is selected: with the selection on a tab there is no app for the banner to name. */
    private boolean appSelected;
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
    /** A tile below this is too small to read; the Inputs row gives up its place rather than go under it. */
    private static final int MIN_TILE_DP = 56;
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
            recordFailure(this);
            startActivity(MainActivity.recovery(this));
            finish();
            overridePendingTransition(0, 0);
            return;
        }
        // Drawn: this start succeeded, so any earlier failure is stale and the recovery screen steps aside.
        root.post(() -> prefs.edit().remove(PREF_STARTING).remove(PREF_FAILED).apply());
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
        clearSelectedApp();   // the selection sits on the Home tab: no app to name until Down picks one
    }

    private void build() {
        dp = getResources().getDisplayMetrics().density;
        Config cfg = Config.load(this);
        int screenW = getResources().getDisplayMetrics().widthPixels;
        cols = Math.max(5, Math.min(8, cfg.columns));
        rowsBelow = Math.max(0, Math.min(3, cfg.rowsBelowDock));
        SharedPreferences effects = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        inputsOnHome = INPUTS_ROW_READY && effects.getBoolean(PREF_INPUTS_ON_HOME, false);
        animations = cfg.animations && !cfg.reducedMotion;
        advancedEffects = animations && cfg.advancedEffects;
        focusStyle = effects.getInt(PREF_FOCUS_STYLE, FOCUS_ZOOM);
        dockApps = cfg.dockApps;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        tileW = (int) ((screenW - 2 * 48 * dp - (cols - 1) * GAP_DP * dp) / cols);
        tileH = tileW * 9 / 16;
        // Home must fit under the top bar: Your apps + Favorite rows, their headers, padding and the chevron.
        // When it doesn't (5 or 6 per row with 2-3 rows), tiles shrink to fit and the grid stays centred.
        // Your apps is its own row since 2.23, with its own 2 x GRID_PAD_DP; the grid below keeps its own
        // whenever it has rows to show, and so does the Inputs row when it is on — the padding counted here
        // follows the structure, section by section.
        int maxTileH = 0;
        for (int attempt = 0; attempt < 2; attempt++) {
            int sections = 1 + (rowsBelow > 0 ? 1 : 0) + (inputsOnHome ? 1 : 0);
            int rowsShown = 1 + rowsBelow + (inputsOnHome ? 1 : 0);
            float fixed = (CONTENT_TOP_DP + 2 * sections * GRID_PAD_DP + sections * HEADER_DP + ARROW_DP
                    + rowsShown * GAP_DP) * dp;
            maxTileH = (int) ((screenH - fixed) / rowsShown);
            // Five rows of tiles do not fit a short screen: rather than shrink them all past reading, the
            // Inputs row steps aside, as the banner and the performance card already do when there is no room.
            if (inputsOnHome && maxTileH < MIN_TILE_DP * dp) {
                inputsOnHome = false;
                continue;
            }
            break;
        }
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
        // Colour the glow from the first app, but leave the banner hidden: nothing is selected yet.
        if (!dock.isEmpty()) showApp(dock.get(0));
        else if (!all.isEmpty()) showApp(all.get(0));
        clearSelectedApp();
        root.post(this::focusHomeFirst);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (settingsChanged) {
            settingsChanged = false;
            recreate();
            return;
        }
        if (topRight != null) topRight.setVisibility(View.VISIBLE);
        resumed = true;
        handler.removeCallbacks(tick);
        handler.post(tick);
        startPerf();
        updateBanner();   // the live input window, if there is one, may run again now Home is in front
        if (glowStyle == GlowStyle.DRIFT) {
            driftStart = SystemClock.elapsedRealtime();
            handler.removeCallbacks(drift);
            handler.post(drift);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        closeMenu();
        stopPerf();
        resumed = false;
        handler.removeCallbacks(drift);
        updateLivePreview(false);   // Home is leaving: the input goes back to the TV
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
        recreate();
    }

    /** Home is never left with Back: from the content Back goes to the tabs, from the tabs it does nothing. */
    @Override
    public void onBackPressed() {
        View f = getCurrentFocus();
        if (f != null && !isTab(f)) tabs[currentTab].requestFocus();
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
        List<String> savedDock = DockApps.load(this);
        if (savedDock == null) {
            // First run on 2.23, or an unreadable file: start from dock_apps, so the row looks as it did.
            savedDock = new ArrayList<>();
            for (String pkg : cfg.dockApps) {
                if (savedDock.size() >= DockApps.MAX || savedDock.contains(pkg)) continue;
                if (LibraryApps.validPackage(pkg)) savedDock.add(pkg);
            }
            // Nothing configured: the first apps of the Home order, as many as fit, which is what 2.22 showed.
            for (int i = 0; savedDock.isEmpty() && i < ordered.size() && i < cols; i++) {
                savedDock.add(ordered.get(i).pkg);
            }
        }
        dockPackages = new ArrayList<>(savedDock);
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

    /**
     * Your apps = the owner's dock, in their order, however many it holds; Favorites = their other list, minus
     * anything already in Your apps. The dock is its own scrolling row, so only the Favorite rows are in the grid.
     */
    private void splitOrdered() {
        dock.clear();
        others.clear();
        for (String pkg : dockPackages) {
            for (App a : ordered) {
                if (a.pkg.equals(pkg) && !dock.contains(a)) dock.add(a);
            }
        }
        for (String pkg : favPackages) {
            for (App a : ordered) {
                if (a.pkg.equals(pkg) && !dock.contains(a) && !others.contains(a)) others.add(a);
            }
        }
        dockItems.clear();
        dockItems.addAll(dock);
        dockItems.add(PLUS);
        homeItems.clear();
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
        buildDots();
        homeGrid.getAdapter().notifyDataSetChanged();
        Toast.makeText(this, getString(favourite ? R.string.gtv_added_favorite : R.string.gtv_removed_favorite,
                a.label), Toast.LENGTH_SHORT).show();
    }

    /**
     * Puts an app in Your apps or takes it out, and rebuilds Home in place. The last app stays: an empty row
     * would leave only the + tile, and nothing names the screen.
     */
    private void setInDock(App a, boolean inDock) {
        if (!inDock && dock.size() <= 1) {
            Toast.makeText(this, R.string.gtv_dock_last_app, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> next;
        try {
            next = inDock ? DockApps.added(dockPackages, a.pkg) : DockApps.removed(dockPackages, a.pkg);
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, R.string.gtv_dock_full, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!DockApps.save(this, next)) {
            Toast.makeText(this, R.string.gtv_dock_not_saved, Toast.LENGTH_SHORT).show();
            return;
        }
        dockPackages = new ArrayList<>(next);
        splitOrdered();
        buildDots();
        dockRow.getAdapter().notifyDataSetChanged();
        homeGrid.getAdapter().notifyDataSetChanged();
        Toast.makeText(this, getString(inDock ? R.string.gtv_added_to_dock : R.string.gtv_removed_from_dock,
                a.label), Toast.LENGTH_SHORT).show();
        focusLastDockApp();
    }

    /** The + tile: every app that is not in Your apps yet, in the Apps tab's order. */
    private void showDockChooser(View anchor) {
        List<String> titles = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        for (App a : all) {
            if (dock.contains(a)) continue;
            titles.add(a.label);
            actions.add(() -> setInDock(a, true));
        }
        if (titles.isEmpty()) {
            Toast.makeText(this, R.string.gtv_dock_has_everything, Toast.LENGTH_SHORT).show();
            return;
        }
        showMenu(anchor, getString(R.string.gtv_add_to_dock), titles, actions);
    }

    /** Focuses the first input on Home, when that row is on and the TV has any ports. True when it took focus. */
    private boolean focusHomeInputs() {
        if (homeInputs == null || homeInputs.getVisibility() != View.VISIBLE
                || homeInputs.getChildCount() == 0) return false;
        return homeInputs.getChildAt(0).requestFocus();
    }

    /**
     * Focuses the last app in Your apps - which also scrolls the row to put it in the middle - once the row has
     * been laid out again. A dock edit rebuilds the tile that held the focus, and a runnable posted straight
     * after notifyDataSetChanged() can run before the next layout pass, when the holder is not there yet; the
     * choice then went to the platform, so the focus landed somewhere that looked random. This waits for a layout
     * pass instead, scrolls to the tile if it is still missing, and tries three passes at most.
     */
    private void focusLastDockApp() {
        if (dockRow == null || dock.isEmpty()) return;
        dockRow.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            private int pass;

            @Override
            public void onGlobalLayout() {
                int at = dock.size() - 1;
                RecyclerView.ViewHolder vh = at >= 0 ? dockRow.findViewHolderForAdapterPosition(at) : null;
                boolean done = vh != null || ++pass >= 3;
                if (vh != null) {
                    View tile = vh.itemView;
                    tile.requestFocus();
                    // The menu that started this edit is still closing; if the platform moves the focus once
                    // more as the window comes back, take it again on the next frame.
                    dockRow.postOnAnimation(() -> {
                        if (getCurrentFocus() != tile && tile.isAttachedToWindow()) tile.requestFocus();
                    });
                } else if (!done) {
                    dockRow.scrollToPosition(at);
                }
                if (done) {
                    ViewTreeObserver tree = dockRow.getViewTreeObserver();
                    if (tree.isAlive()) tree.removeOnGlobalLayoutListener(this);
                }
            }
        });
        dockRow.requestLayout();   // there has to be a layout pass to wait for
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
        if (!DockApps.save(this, dockPackages)) {
            Toast.makeText(this, R.string.gtv_dock_not_saved, Toast.LENGTH_SHORT).show();
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
        updateLivePreview(false);   // hand the input back before the app asks the TV for it
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
        boolean inDock = dock.contains(a);
        List<Runnable> actions = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        titles.add(getString(R.string.gtv_open));
        actions.add(() -> open(a));
        if (onHome) {
            titles.add(getString(R.string.gtv_move));
            actions.add(() -> startMove(a));
        }
        if (inDock) {
            // The row never shrinks below Apps per row, so Remove is not offered at that size. setInDock still
            // refuses to take the last app, as the final line of defence.
            if (dock.size() > cols) {
                titles.add(getString(R.string.gtv_remove_from_dock));
                actions.add(() -> setInDock(a, false));
            }
        } else {
            titles.add(getString(R.string.gtv_add_to_dock));
            actions.add(() -> setInDock(a, true));
            titles.add(getString(favourite ? R.string.gtv_remove_favorite : R.string.gtv_add_favorite));
            actions.add(() -> setFavourite(a, !favourite));
        }
        titles.add(getString(R.string.library_app_info));
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

    /** Puts the carried tile's lift and ghost on, or takes them off and gives the ring back. */
    private void styleCarried(View v, boolean carried) {
        v.animate().cancel();
        v.animate().scaleX(carried ? 1.15f : 1.1f).scaleY(carried ? 1.15f : 1.1f)
                .translationZ((carried ? 16 : 8) * dp)
                .alpha(carried ? GHOST : 1f)
                .setDuration(140).start();
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
        boolean inDock = from >= 0 && from < dock.size();
        // In Your apps the row is one line, so Up and Down have nothing to move through.
        if (code == KeyEvent.KEYCODE_DPAD_LEFT) to = from - 1;
        else if (code == KeyEvent.KEYCODE_DPAD_RIGHT) to = from + 1;
        else if (!inDock && code == KeyEvent.KEYCODE_DPAD_UP) to = from - cols;
        else if (!inDock && code == KeyEvent.KEYCODE_DPAD_DOWN) to = from + cols;
        else if (drop && e.getRepeatCount() == 0) {
            endMove();
            return true;
        }
        if (from >= 0 && to != from && to >= 0 && to < homeCount() && moveTo(from, to)) {
            splitOrdered();
            dockRow.getAdapter().notifyDataSetChanged();
            homeGrid.getAdapter().notifyDataSetChanged();
            homeGrid.post(this::refocusMoving);
        }
        return true;
    }

    /**
     * Moves the carried tile from one Home place to another. Your apps reorder their own list (gtv_dock.txt) and
     * the Favorite rows reorder theirs.
     *
     * Crossing between the two used to trade one app for the other, back when Your apps was simply the first row
     * of one list and had to stay the same size. Since 2.23 the dock is a list the owner adds to with + and takes
     * from by holding OK, so a move across the boundary would quietly change what is in it — it is refused, and
     * the explicit actions do that job.
     */
    private boolean moveTo(int from, int to) {
        App carried = homeApp(from), other = homeApp(to);
        if (carried == null || other == null) return false;
        boolean fromDock = from < dock.size(), toDock = to < dock.size();
        if (fromDock != toDock) return false;
        if (fromDock) {
            int a = dockPackages.indexOf(carried.pkg), b = dockPackages.indexOf(other.pkg);
            if (a < 0 || b < 0) return false;
            dockPackages.add(b, dockPackages.remove(a));
        } else {
            int a = favPackages.indexOf(carried.pkg), b = favPackages.indexOf(other.pkg);
            if (a < 0 || b < 0) return false;
            favPackages.add(b, favPackages.remove(a));
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
        if ((code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT)
                && atRowEnd(f, code == KeyEvent.KEYCODE_DPAD_RIGHT)) {
            // Nothing else handles Left or Right here, so at a row's end the platform's focus search picks
            // whatever focusable it can reach - the next item in adapter order, which is the neighbouring row.
            // Swallow the key instead: a row stops at its ends, and Up and Down are the only ways out of it.
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && currentTab == TAB_HOME && f.getParent() == homeInputs) {
            openAppsAt(0);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && currentTab == TAB_HOME && f.getParent() == dockRow) {
            // Down from Your apps: the Favorite rows, else the Inputs row, else on to the Apps tab.
            if (favouritesShown() > 0 && !others.isEmpty()) {
                RecyclerView.ViewHolder vh =
                        homeGrid.findViewHolderForAdapterPosition(homeItems.indexOf(others.get(0)));
                if (vh != null) {
                    vh.itemView.requestFocus();
                    return true;
                }
            }
            if (focusHomeInputs()) return true;
            openAppsAt(0);
            return true;
        }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN && currentTab == TAB_HOME && f.getParent() == homeGrid) {
            int pos = homeGrid.getChildAdapterPosition(f);
            if (pos < 0 || pos >= homeItems.size() || !(homeItems.get(pos) instanceof App)) return false;
            int at = others.indexOf((App) homeItems.get(pos)), shown = favouritesShown();
            int lastRowStart = shown <= cols ? 0 : ((shown - 1) / cols) * cols;
            if (at < 0 || at < lastRowStart) return false;
            if (focusHomeInputs()) return true;
            openAppsAt(at % cols);
            return true;
        }
        return false;
    }

    /**
     * True when this tile is the last one in its row in the direction pressed, so the key must stop here. Every
     * lookup is guarded by the tile's parent: {@code getChildAdapterPosition} casts the layout parameters to
     * RecyclerView's own and throws for a view from any other container (2.28).
     */
    private boolean atRowEnd(View f, boolean right) {
        ViewParent parent = f.getParent();
        if (currentTab == TAB_HOME && parent == dockRow) {
            int at = dockRow.getChildAdapterPosition(f);
            if (at < 0) return false;
            return right ? at >= dockItems.size() - 1 : at == 0;
        }
        if (currentTab == TAB_HOME && parent == homeGrid) {
            // Position 0 is the section header, so the Favorites start at 1.
            int at = homeGrid.getChildAdapterPosition(f) - 1;
            if (at < 0) return false;
            return right ? at % cols == cols - 1 || at >= favouritesShown() - 1 : at % cols == 0;
        }
        if (currentTab == TAB_APPS && parent == appsGrid) {
            int at = appsGrid.getChildAdapterPosition(f);
            if (at < 0) return false;
            return right ? at % cols == cols - 1 || at >= all.size() - 1 : at % cols == 0;
        }
        // The Inputs rows are plain LinearLayouts, so no adapter is asked: the tile's place among its siblings.
        LinearLayout row = currentTab == TAB_HOME && parent == homeInputs ? homeInputs
                : currentTab == TAB_INPUTS && parent == inputsRow ? inputsRow : null;
        if (row != null) {
            int at = row.indexOfChild(f);
            return right ? at == row.getChildCount() - 1 : at == 0;
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
        dockRow.getAdapter().notifyDataSetChanged();
        homeGrid.getAdapter().notifyDataSetChanged();
        homeGrid.post(() -> {
            RecyclerView.ViewHolder vh = holderOf(a);
            if (vh == null) return;
            vh.itemView.requestFocus();
            styleCarried(vh.itemView, false);   // put down: back to an ordinary selection
            dimOthers(vh.itemView);
        });
    }

    private void refocusMoving() {
        if (moving == null) return;
        RecyclerView.ViewHolder vh = holderOf(moving);
        if (vh == null) return;
        vh.itemView.requestFocus();
        styleCarried(vh.itemView, true);
        dimOthers(vh.itemView);
    }

    /** The tile showing this app, wherever it is: Your apps, or the Favorite rows. */
    private RecyclerView.ViewHolder holderOf(App a) {
        int at = dockItems.indexOf(a);
        if (at >= 0) return dockRow.findViewHolderForAdapterPosition(at);
        at = homeItems.indexOf(a);
        return at >= 0 ? homeGrid.findViewHolderForAdapterPosition(at) : null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (moving != null) endMove();   // leaving Home while moving keeps the order so far
    }

    // ---- menus (the Google TV look: glass panel, white pill on the selected row) --------------

    /** More rows than this and the menu scrolls: the + tile's chooser can list every app on the TV. */
    private static final int MENU_ROWS_BEFORE_SCROLL = 8;

    /** OK on the gear: Launcher settings or the TV's own settings, as in the Apple TV layout. */
    private void showSettingsChoice(View anchor) {
        List<String> titles = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        titles.add(getString(R.string.launcher_settings));
        actions.add(this::openSettings);
        titles.add(getString(R.string.tv_settings));
        actions.add(() -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        });
        showMenu(anchor, null, titles, actions);
    }

    /** The settings panel covers the top-right corner, so the clock and gear step aside while it is open. */
    private void openSettings() {
        if (topRight != null) topRight.setVisibility(View.INVISIBLE);
        startActivityForResult(new Intent(this, LauncherSettingsActivity.class), SETTINGS_REQUEST);
    }

    /**
     * The one menu both the gear and hold OK use, so they match the tabs: a glass panel under (or over) the
     * tile, each row a pill that turns white with dark text when selected.
     */
    private void showMenu(View anchor, String heading, List<String> titles, List<Runnable> actions) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF151A21);   // solid: over a bright wallpaper the tiles read straight through a sheer panel
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
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < titles.size(); i++) {
            rows.addView(menuRow(popup, titles.get(i), actions.get(i)));
        }
        if (titles.size() > MENU_ROWS_BEFORE_SCROLL) {
            ScrollView scroller = new ScrollView(this);
            scroller.setScrollbarFadingEnabled(false);
            scroller.addView(rows);
            panel.addView(scroller, new LinearLayout.LayoutParams(-1,
                    (int) (getResources().getDisplayMetrics().heightPixels * 0.55f)));
        } else {
            panel.addView(rows);
        }
        Fonts.apply(panel);
        showPanel(popup, panel, anchor);
        rows.getChildAt(0).requestFocus();
    }

    /**
     * Hangs the menu under the thing it belongs to, or above it when there is no room below — a tile in the
     * bottom row has none, and the menu used to run off the screen.
     */
    private void showPanel(PopupWindow popup, View panel, View anchor) {
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int height = panel.getMeasuredHeight(), width = Math.max(popup.getWidth(), panel.getMeasuredWidth());
        int gap = (int) (10 * dp), edge = (int) (8 * dp);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int[] rootAt = new int[2];
        root.getLocationInWindow(rootAt);
        int anchorTop = at[1] - rootAt[1], anchorBottom = anchorTop + anchor.getHeight();
        if (anchorBottom + gap + height <= root.getHeight() - edge) {
            popup.showAsDropDown(anchor, 0, gap, Gravity.END);
            return;
        }
        // Above the tile, left-aligned with it; against the right edge of the screen, right-aligned instead.
        int x = at[0] - rootAt[0];
        if (x + width > root.getWidth() - edge) {
            x = x + Math.round(anchor.getWidth() * anchor.getScaleX()) - width;
        }
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY,
                Math.max(edge, x), Math.max(edge, anchorTop - height - gap));
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
        glowStyle = GlowStyle.load(this);
        glowDrawable = new GradientDrawable();
        if (glowStyle == GlowStyle.RIBBON) {
            // A diagonal band of light rather than a spot: a linear gradient across the screen.
            glowDrawable.setOrientation(GradientDrawable.Orientation.BL_TR);
            glowDrawable.setGradientType(GradientDrawable.LINEAR_GRADIENT);
        } else {
            glowDrawable.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        }
        glow.setBackground(glowDrawable);
        glow.setVisibility(cfg.ambientGlow ? View.VISIBLE : View.GONE);
        root.addView(glow, new FrameLayout.LayoutParams(-1, -1));
        // The extra glows of Two glows, Random x2 and Random x3: their own views, tinted a shifted colour.
        int extras = GlowStyle.extraCount(glowStyle);
        extraGlows = new View[extras];
        extraDrawables = new GradientDrawable[extras];
        for (int i = 0; i < extras; i++) {
            extraDrawables[i] = new GradientDrawable();
            extraDrawables[i].setGradientType(GradientDrawable.RADIAL_GRADIENT);
            extraGlows[i] = new View(this);
            extraGlows[i].setBackground(extraDrawables[i]);
            extraGlows[i].setVisibility(cfg.ambientGlow ? View.VISIBLE : View.GONE);
            root.addView(extraGlows[i], new FrameLayout.LayoutParams(-1, -1));
        }
        placeGlow(GlowStyle.classic(getResources().getBoolean(R.bool.rtl_app_grid)));
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
            t.setOnFocusChangeListener((v, has) -> {
                if (has) {
                    clearSelectedApp();
                    showTab(tab);
                }
                styleTabs();
            });
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
        settings.setOnFocusChangeListener((v, has) -> {
            if (has) clearSelectedApp();
            stylePill((TextView) v, has, false);
        });
        settings.setOnKeyListener((v, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || code != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            focusContent();
            return true;
        });
        topRight = right;
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
        // Forced off at the most Favorite rows, whatever was chosen: the rows reach up to the card there.
        if (rowsBelow >= Config.PERF_CARD_OFF_AT_ROWS) return;
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
        // Plain text over the glow by default; the fill and the outline are each a switch in Launcher settings,
        // which is how the owner wanted them back after 2.15 took them off the screen.
        SharedPreferences look = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        bannerSp = look.getInt(PREF_BANNER_SP, BANNER_SP_DEFAULT);
        bannerStyle = bannerStyle(look);
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(24 * dp);
        if (bannerStyle == BANNER_GLASS) {
            g.setColor(0xCC0B0D12);
            g.setStroke((int) (1 * dp), 0x33FFFFFF);
        }
        banner.setBackground(g);
        // The bottom padding is all the clearance the banner keeps from the rows, so it sits close to them.
        banner.setPadding((int) (28 * dp), (int) (20 * dp), (int) (28 * dp), (int) (16 * dp));
        if (bannerStyle == BANNER_LIVE) buildLiveWindow();
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, bannerSp);
        title.setSingleLine(true);
        banner.addView(title);
        subtitle = new TextView(this);
        subtitle.setTextColor(0xFFC3CAD4);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        // The dots come first and the "Last opened" line after them: with the order the other way round, the dots
        // jumped up whenever an app had never been opened and that line was empty.
        dots = new LinearLayout(this);
        dots.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(-2, -2);
        dlp.topMargin = (int) (14 * dp);
        banner.addView(dots, dlp);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = (int) (12 * dp);
        banner.addView(subtitle, slp);
        buildDots();
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams((int) (420 * dp), -2, Gravity.TOP | Gravity.START);
        lp.leftMargin = sidePad;
        lp.topMargin = (int) (110 * dp);
        root.addView(banner, lp);
    }

    /**
     * The live input window: a 16:9 picture of one HDMI port at the top of the banner, driven by InputPreview,
     * which mutes it, says "connecting", "no signal" or "unavailable", and hands the input back on stop().
     *
     * Kept small (260 dp) so the banner stays near the height it has without it; where even that will not fit
     * above the rows, fitBanner() hides the banner, which is what it does for every other banner too.
     */
    private void buildLiveWindow() {
        int w = (int) (260 * dp), h = w * 9 / 16;
        FrameLayout frame = new FrameLayout(this);
        frame.setBackground(tileBackground(0xFF10131A));
        frame.setClipToOutline(true);
        android.media.tv.TvView tv = new android.media.tv.TvView(this);
        frame.addView(tv, new FrameLayout.LayoutParams(-1, -1));
        TextView status = new TextView(this);
        status.setTextColor(0xFFC3CAD4);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        status.setGravity(Gravity.CENTER);
        status.setPadding((int) (8 * dp), 0, (int) (8 * dp), 0);
        frame.addView(status, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.bottomMargin = (int) (12 * dp);
        banner.addView(frame, lp);
        livePreview = new InputPreview(this, frame, tv, status);
    }

    /**
     * Starts the live window only while Home is in front with the banner on screen, and stops it the moment any
     * of that stops being true: the TV's own Inputs and Live TV must get the input back, and nothing may stay
     * tuned behind an app.
     */
    private void updateLivePreview(boolean mayRun) {
        // open(), openInput(), onPause(): a start still queued must not undo the stop.
        if (!mayRun && banner != null) banner.removeCallbacks(liveStart);
        if (livePreview == null) return;
        if (mayRun == liveRunning) return;
        liveRunning = mayRun;
        if (mayRun) livePreview.start();
        else livePreview.stop();
    }

    /** A dot for each app in Your apps, the selected one wider. Rebuilt whenever the dock itself changes. */
    private void buildDots() {
        if (dots == null) return;
        // One dot per app reads well for a row of five to eight. A long row would be a thicket of them, so past
        // DOTS_MAX the indicator steps aside instead.
        if (dock.size() > DOTS_MAX) {
            if (dotAnimator != null) dotAnimator.cancel();
            dots.removeAllViews();
            dotShown = -1;
            return;
        }
        if (dotAnimator != null) dotAnimator.cancel();
        dots.removeAllViews();
        dotShown = -1;
        for (int i = 0; i < dock.size(); i++) {
            View dot = new View(this);
            dot.setBackground(dotDrawable(false));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (DOT_DP * dp), (int) (DOT_DP * dp));
            lp.rightMargin = (int) (7 * dp);
            dots.addView(dot, lp);
        }
    }

    private static final int DOT_DP = 6, DOT_WIDE_DP = 22;
    /** Above this many apps in Your apps the page indicator hides rather than drawing a dot for each. */
    private static final int DOTS_MAX = 12;

    /** Banner look, all from Launcher settings: the title's size in sp, and whether it has an outline or a fill. */
    static final String PREF_BANNER_SP = "gtv_banner_title_sp";
    static final String PREF_BANNER_BORDER = "gtv_banner_border";
    static final String PREF_BANNER_FILL = "gtv_banner_fill";
    static final int BANNER_SP_DEFAULT = 46;
    /**
     * Banner style: 0 the app's name over the glow, 1 that on a glass panel, 2 no banner at all, 3 a live input
     * (HDMI) in a small window above the name — the NVR on HDMI 2, in the owner's case.
     */
    static final String PREF_BANNER_STYLE = "gtv_banner_style";
    static final int BANNER_NAME = 0, BANNER_GLASS = 1, BANNER_OFF = 2, BANNER_LIVE = 3;
    /** The Inputs row under the Favorite rows, and the frosted strip behind the tabs and the clock. */
    static final String PREF_INPUTS_ON_HOME = "gtv_inputs_on_home";
    /**
     * The Inputs row on Home (2.24) is out of the APK, at the owner's word: on this TV (density 2.0) it stands down
     * at two or more Favorite rows, and what is wanted in its place is a live window per input. Its code, its
     * stored value and its row-end rule stay in the tree; flip this, and the row in Launcher settings with it, to
     * bring it back. The Inputs tab is a different thing and is untouched.
     */
    static final boolean INPUTS_ROW_READY = false;
    static final String PREF_GLASS_BARS = "gtv_glass_bars";
    /** Focus style: 0 zoom (what Home has always done), 1 lift, 2 glass halo, 3 underline. */
    static final String PREF_FOCUS_STYLE = "gtv_focus_style";
    static final int FOCUS_ZOOM = 0, FOCUS_LIFT = 1, FOCUS_HALO = 2, FOCUS_UNDERLINE = 3;
    /** The bar an underlined tile shows; looked up by tag, since tiles, inputs and the + tile differ. */
    private static final String TAG_FOCUS_BAR = "focusBar";

    /**
     * The style the banner draws in, with 2.21's two switches folded in: an owner who turned either of them on
     * meant the glass panel, so that is what they get, and the old keys are not read again.
     */
    static int bannerStyle(SharedPreferences prefs) {
        if (prefs.contains(PREF_BANNER_STYLE)) return prefs.getInt(PREF_BANNER_STYLE, BANNER_NAME);
        boolean glass = prefs.getBoolean(PREF_BANNER_BORDER, false) || prefs.getBoolean(PREF_BANNER_FILL, false);
        return glass ? BANNER_GLASS : BANNER_NAME;
    }

    private GradientDrawable dotDrawable(boolean selected) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(DOT_DP * dp / 2f);
        d.setColor(selected ? Color.WHITE : 0x59FFFFFF);
        return d;
    }

    /**
     * Stretches the selected dot into a pill and lets every other one shrink back.
     *
     * Each run starts from the widths the dots actually have, not from the widths they would have at rest, and it
     * moves all of them rather than just the two either side of the move. Holding left or right outruns the 140 ms
     * animation, and the first version — which animated only the new dot and the one before it, from their resting
     * widths — stranded every dot further back part-way open, leaving a row of dashes.
     *
     * Only Your apps has dots: a position in that row is what the banner is describing, so the indicator steps
     * aside for any other row.
     */
    private void showDot(int index) {
        if (dots == null) return;
        dots.setVisibility(index >= 0 && dots.getChildCount() > 0 ? View.VISIBLE : View.GONE);
        if (index < 0 || index >= dots.getChildCount() || index == dotShown) return;
        dotShown = index;
        if (dotAnimator != null) dotAnimator.cancel();
        final int narrow = (int) (DOT_DP * dp), wide = (int) (DOT_WIDE_DP * dp);
        final int[] from = new int[dots.getChildCount()];
        for (int i = 0; i < from.length; i++) {
            View dot = dots.getChildAt(i);
            from[i] = dot.getLayoutParams().width;
            dot.setBackground(dotDrawable(i == index));
        }
        dotAnimator = android.animation.ValueAnimator.ofFloat(0f, 1f);
        dotAnimator.setDuration(animations ? 140 : 0);
        dotAnimator.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            for (int i = 0; i < from.length && i < dots.getChildCount(); i++) {
                int to = i == index ? wide : narrow;
                setDotWidth(dots.getChildAt(i), from[i] + Math.round((to - from[i]) * t));
            }
        });
        dotAnimator.start();
    }

    private static void setDotWidth(View dot, int width) {
        ViewGroup.LayoutParams lp = dot.getLayoutParams();
        if (lp.width == width) return;
        lp.width = width;
        dot.setLayoutParams(lp);
    }

    /** The banner and glow follow the selected app. */
    private void showApp(App a) {
        showApp(a, null);
    }

    /** The tile is passed in for Glow: Follow, which sits the glow above the tile that has just taken focus. */
    private void showApp(App a, View tile) {
        appSelected = true;
        fitBanner();   // bannerFits may be stale; decide on a fresh fit, or the banner shows for an instant and goes
        updateBanner();
        showDot(dock.indexOf(a));
        title.setText(a.label);
        long when = LastOpened.get(this, a.pkg);
        subtitle.setText(when > 0 ? getString(R.string.last_opened, LastOpened.describe(this, when)) : "");
        subtitle.setVisibility(when > 0 && !bannerCompact && bannerStyle != BANNER_LIVE
                ? View.VISIBLE : View.GONE);
        tintGlow(a.color);
        placeGlow(spotFor(a, tile));
    }

    /**
     * The glow takes the app's colour, and the extra glows take it turned round the hue wheel. With advanced
     * effects on it fades from the colour it is showing instead of cutting to the new one.
     */
    private void tintGlow(int color) {
        if (glowFade != null) glowFade.cancel();
        if (!advancedEffects || glowColor == 0) {
            paintGlow(color);
            glowColor = color;
            return;
        }
        final int from = glowColor;
        glowFade = android.animation.ValueAnimator.ofFloat(0f, 1f);
        glowFade.setDuration(260);
        glowFade.addUpdateListener(an -> {
            float t = (float) an.getAnimatedValue();
            paintGlow((int) new android.animation.ArgbEvaluator().evaluate(t, from, color));
        });
        glowFade.start();
        glowColor = color;
    }

    private void paintGlow(int color) {
        if (glowStyle == GlowStyle.RIBBON) {
            // The band is brightest down its middle and fades out to both edges.
            glowDrawable.setColors(new int[]{0x00000000, (color & 0x00FFFFFF) | 0x8C000000, 0x00000000});
        } else {
            glowDrawable.setColors(new int[]{(color & 0x00FFFFFF) | 0xA6000000,
                    (color & 0x00FFFFFF) | 0x33000000, 0x00000000});
        }
        for (int i = 0; i < extraDrawables.length; i++) {
            int c = GlowStyle.shifted(color, 45f * (i + 1));
            extraDrawables[i].setColors(new int[]{(c & 0x00FFFFFF) | 0x66000000, (c & 0x00FFFFFF) | 0x22000000, 0x00000000});
        }
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
        dockHeader = header(getString(R.string.gtv_your_apps));
        dockHeader.setPadding(sidePad + (int) (2 * dp), 0, 0, 0);
        page.addView(dockHeader, new LinearLayout.LayoutParams(-1, (int) (HEADER_DP * dp)));
        page.addView(dockRow = dockRow(), new LinearLayout.LayoutParams(-1, -2));
        homeGrid = grid(homeItems);
        homeGrid.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        page.addView(homeGrid);
        if (inputsOnHome) {
            homeInputsHeader = header(getString(R.string.gtv_inputs));
            homeInputsHeader.setPadding(sidePad + (int) (2 * dp), 0, 0, 0);
            page.addView(homeInputsHeader, new LinearLayout.LayoutParams(-1, (int) (HEADER_DP * dp)));
            homeInputs = new LinearLayout(this);
            homeInputs.setPadding(sidePad, (int) (GRID_PAD_DP * dp), sidePad, (int) (GRID_PAD_DP * dp));
            homeInputs.setClipChildren(false);
            homeInputs.setClipToPadding(false);
            fillInputs(homeInputs, true);
            page.addView(homeInputs, new LinearLayout.LayoutParams(-1, -2));
            // No ports (a set with no TvInputManager): the heading would name an empty row.
            if (homeInputs.getChildCount() == 0) {
                homeInputsHeader.setVisibility(View.GONE);
                homeInputs.setVisibility(View.GONE);
            }
        }
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
        dockRow.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (t != ot || b != ob) v.post(this::fitBanner);
        });
        return page;
    }

    /**
     * Your apps: one row that scrolls, so it can hold more apps than fit on screen. Apps per row sets how many
     * are on screen; moving right brings the next one in, and the selected tile is kept in the middle of the
     * screen except at the two ends, where the row has nothing left to scroll.
     */
    private RecyclerView dockRow() {
        RecyclerView r = new RecyclerView(this);
        r.setLayoutManager(new CentreManager());
        r.setAdapter(new TileAdapter(dockItems));
        r.setPadding(sidePad, (int) (GRID_PAD_DP * dp), sidePad, (int) (GRID_PAD_DP * dp));
        r.setClipToPadding(false);
        r.setClipChildren(false);
        r.setItemAnimator(null);
        r.setHorizontalScrollBarEnabled(false);
        return r;
    }

    /**
     * Keeps the focused tile in the middle of the row. RecyclerView's own "scroll it just into view" is turned
     * off (onRequestChildFocus answers true) and replaced by a scroll to centre, which the row clamps at its ends
     * by itself — so the first and last tiles sit at the edges instead of being dragged to the middle.
     */
    private final class CentreManager extends LinearLayoutManager {
        CentreManager() {
            super(GtvActivity.this, LinearLayoutManager.HORIZONTAL, false);
        }

        @Override
        protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extra) {
            extra[0] = tileW;
            extra[1] = tileW;
        }

        @Override
        public boolean onRequestChildFocus(@NonNull RecyclerView parent, @NonNull RecyclerView.State state,
                                           @NonNull View child, View focused) {
            int to = (child.getLeft() + child.getRight()) / 2 - parent.getWidth() / 2;
            if (to != 0) parent.smoothScrollBy(to, 0);
            return true;
        }
    }

    /**
     * Places the banner between the top bar and the Home rows: full size where it fits, a smaller title without
     * the "Last opened" line where only that fits, otherwise hidden. Never overlaps the top bar or the rows.
     */
    private void fitBanner() {
        if (topBar == null || dockHeader == null || dockRow == null || dockRow.getHeight() == 0) return;
        int gap = (int) (12 * dp);
        // The rows' block starts at the "Your apps" heading now that the dock is a row of its own above the grid.
        int rowsTop = content.getTop() + homePage.getTop() + dockHeader.getTop();
        int roomTop = topBar.getBottom() + gap;
        int roomBottom = rowsTop - gap;            // the performance card keeps the gap it has always had
        // The banner carries its own 16 dp of bottom padding, which is the clearance it keeps from the rows: a
        // further gap on top of that left the text floating well above the row it names.
        int bannerBottom = rowsTop;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) banner.getLayoutParams();
        setBannerCompact(false);
        int h = bannerHeight();
        int top;
        // The banner belongs with the rows it names: sit its foot just above "Your apps" and grow upward.
        if (bannerBottom - h >= roomTop) top = bannerBottom - h;
        else if (bannerBottom - roomTop >= h) top = roomTop;
        else {
            setBannerCompact(true);
            top = bannerBottom - roomTop >= bannerHeight() ? roomTop : -1;
        }
        layoutPerfCard(roomBottom);
        bannerFits = top >= 0;
        if (bannerFits && lp.topMargin != top) {
            lp.topMargin = top;
            banner.setLayoutParams(lp);
        }
        updateBanner();
    }

    /**
     * Puts the glow where a style says: the centre as a fraction of the screen, the radius from rx (0.78 is the
     * size the glow has always had), and ry through the view's own scale about that centre, which is the only way
     * a radial GradientDrawable can be an ellipse.
     */
    private void placeGlow(GlowStyle.Spot spot) {
        if (glow == null || spot == null || glowStyle == GlowStyle.RIBBON) return;
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        // The ellipse is made by stretching the full-screen view about the spot, and a stretch may only ever GROW it:
        // squashing it (a scale below 1) left its rectangle smaller than the screen, and the gradient, not yet
        // transparent at that edge, showed as a hard dark bar along the top and bottom (2.26, Random position and
        // shape). Wider than tall: stretch X up and shrink the radius by the same ratio, so the ellipse is the same.
        float ratio = spot.ry / spot.rx, radius = w * 0.6f * (spot.rx / 0.78f);
        glowDrawable.setGradientCenter(spot.x, spot.y);
        glowDrawable.setGradientRadius(ratio >= 1f ? radius : radius * ratio);
        glow.setPivotX(spot.x * w);
        glow.setPivotY(spot.y * h);
        glow.setScaleX(ratio >= 1f ? 1f : 1f / ratio);
        glow.setScaleY(ratio >= 1f ? ratio : 1f);
        glow.invalidate();
        for (int i = 0; i < extraDrawables.length; i++) {
            GlowStyle.Spot extra = i == 0 ? GlowStyle.second(spot) : GlowStyle.random(false);
            extraDrawables[i].setGradientCenter(extra.x, extra.y);
            extraDrawables[i].setGradientRadius(w * 0.6f * (extra.rx / 0.78f));
            extraGlows[i].invalidate();
        }
    }

    /** The spot this style wants for the app that has just been selected. */
    private GlowStyle.Spot spotFor(App a, View tile) {
        boolean rtl = getResources().getBoolean(R.bool.rtl_app_grid);
        switch (glowStyle) {
            case GlowStyle.RANDOM:
                return GlowStyle.random(false);
            case GlowStyle.RANDOM_SHAPE:
                return GlowStyle.random(true);
            case GlowStyle.RANDOM_2:
            case GlowStyle.RANDOM_3:
                return GlowStyle.random(false);
            case GlowStyle.PER_APP:
                return GlowStyle.perApp(a == null ? null : a.pkg);
            case GlowStyle.FOLLOW:
                if (tile == null) return GlowStyle.classicNormal();
                int[] at = new int[2];
                tile.getLocationOnScreen(at);
                float centre = (at[0] + tile.getWidth() / 2f) / getResources().getDisplayMetrics().widthPixels;
                return GlowStyle.follow(Math.max(0.05f, Math.min(0.95f, centre)));
            case GlowStyle.DRIFT:
                return null;   // the ticker owns the position
            default:
                return GlowStyle.classic(rtl);
        }
    }

    /**
     * Whether the banner is on screen: only on Home, only when it fits, and only with a selection -- except the live
     * input window, which belongs to the Home page rather than to an app and stays while the focus is on a tab or the gear.
     */
    private boolean bannerShown() {
        return bannerStyle != BANNER_OFF && currentTab == TAB_HOME && bannerFits
                && (appSelected || bannerStyle == BANNER_LIVE);
    }

    private void updateBanner() {
        boolean show = bannerShown();
        banner.setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        // With no app selected the window stays but the name and dots go; INVISIBLE, not GONE, so the banner keeps its
        // height and the window does not move. showApp() -> showDot() brings the dots back.
        title.setVisibility(appSelected ? View.VISIBLE : View.INVISIBLE);
        if (!appSelected && dots.getVisibility() == View.VISIBLE) dots.setVisibility(View.INVISIBLE);
        if (!(show && resumed)) {
            updateLivePreview(false);
            return;
        }
        // Start only after one more pass, with every condition read again: a fit that follows this one may still
        // hide the banner, and a window started in between flashes black.
        banner.removeCallbacks(liveStart);
        banner.post(liveStart);
    }

    private final Runnable liveStart = () -> updateLivePreview(bannerShown() && resumed);

    /** The selection left the tiles (a tab or the gear): there is no app to name. */
    private void clearSelectedApp() {
        appSelected = false;
        updateBanner();
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
        // The compact title keeps its old distance from the full one (46 sp down to 30), whatever size is chosen.
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, compact ? Math.max(24, bannerSp - 16) : bannerSp);
        if (compact) subtitle.setVisibility(View.GONE);
    }

    /** The banner's height with its "Last opened" line (when not compact), whatever the current app shows. */
    private int bannerHeight() {
        CharSequence text = subtitle.getText();
        int vis = subtitle.getVisibility();
        subtitle.setText("Xg");
        subtitle.setVisibility(bannerCompact || bannerStyle == BANNER_LIVE ? View.GONE : View.VISIBLE);
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
        fillInputs(inputsRow, false);
        page.addView(inputsRow);
        return page;
    }

    /**
     * The HDMI, AV and Live TV tiles, in the Inputs tab and (since 2.24) in the row on Home. The row on Home
     * clears the banner's selection when it takes focus: the banner names an app, and an input is not one.
     */
    private void fillInputs(LinearLayout row, boolean onHome) {
        row.removeAllViews();
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
            tile.setOnFocusChangeListener((v, has) -> {
                focusTile(v, has);
                if (has && onHome) clearSelectedApp();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(tileW, tileH);
            lp.rightMargin = (int) (18 * dp);
            row.addView(tile, lp);
        }
    }

    private void openInput(String id) {
        updateLivePreview(false);   // the full-screen input needs the hardware this window is holding
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
        updateBanner();
        if (perfCard != null) perfCard.setVisibility(tab == TAB_HOME && perfFits ? View.VISIBLE : View.GONE);
        if (glowStyle == GlowStyle.DRIFT) {
            handler.removeCallbacks(drift);
            if (tab == TAB_HOME && resumed) handler.post(drift);
        }
    }

    /** Home's first app (Your apps, first place), or the + tile when the dock is empty. */
    private void focusHomeFirst() {
        if (dockRow == null) return;
        RecyclerView.ViewHolder vh = dockRow.findViewHolderForAdapterPosition(0);
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
        private static final int HEADER = 1, TILE = 0, PLUS_TILE = 2;
        private final List<?> items;
        TileAdapter(List<?> items) { this.items = items; }

        @Override public int getItemViewType(int position) {
            Object item = items.get(position);
            if (item instanceof String) return HEADER;
            return item == PLUS ? PLUS_TILE : TILE;
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
            if (viewType == PLUS_TILE) {
                TextView plus = new TextView(GtvActivity.this);
                plus.setFocusable(true);
                plus.setText("+");
                plus.setTextColor(0xFFC3CAD4);
                plus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
                plus.setGravity(Gravity.CENTER);
                plus.setContentDescription(getString(R.string.gtv_add_to_dock));
                RecyclerView.LayoutParams plp = new RecyclerView.LayoutParams(tileW, tileH);
                plp.rightMargin = (int) (18 * dp);
                plus.setLayoutParams(plp);
                Fonts.apply(plus);
                return new RecyclerView.ViewHolder(plus) { };
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
            // The underline focus style's bar: along the tile's bottom edge, hidden until that style selects it.
            View bar = new View(GtvActivity.this);
            bar.setTag(TAG_FOCUS_BAR);
            bar.setVisibility(View.GONE);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-1, (int) (4 * dp), Gravity.BOTTOM);
            card.addView(bar, blp);
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
            if (item == PLUS) {
                View plus = h.itemView;
                plus.setBackground(tileBackground(0x33FFFFFF));
                plus.setScaleX(1f);
                plus.setScaleY(1f);
                plus.setAlpha(moving != null ? DIM_MOVING : 1f);
                plus.setOnClickListener(v -> showDockChooser(v));
                plus.setOnFocusChangeListener((v, has) -> {
                    focusTile(v, has);
                    if (has) clearSelectedApp();   // the + names no app, so the banner steps aside
                });
                return;
            }
            App a = (App) item;
            FrameLayout card = (FrameLayout) h.itemView;
            ImageView img = (ImageView) card.getChildAt(0);
            TextView name = (TextView) card.getChildAt(1);
            View focusBar = card.findViewWithTag(TAG_FOCUS_BAR);
            if (focusBar != null) focusBar.setBackgroundColor((a.color & 0x00FFFFFF) | 0xFF000000);
            card.setBackground(tileBackground(a.banner != null ? 0xFF1B2029 : darker(a.color)));
            if (a.banner != null) {
                img.setImageDrawable(a.banner);
                name.setText("");
            } else {
                img.setImageDrawable(null);
                name.setText(a.label);
            }
            // A recycled card may still carry the zoom or dimness of the tile it showed before.
            boolean focused = card.isFocused(), carried = a == moving;
            card.animate().cancel();
            boolean carrying = moving != null;
            float scale = focused ? (carrying ? 1.15f : focusScale()) : 1f;
            card.setScaleX(scale);
            card.setScaleY(scale);
            card.setTranslationY(focused && !carrying && focusStyle == FOCUS_LIFT ? -10 * dp : 0f);
            card.setTranslationZ(focused ? (carrying ? 16 : focusStyle == FOCUS_ZOOM ? 8 : 16) * dp : 0);
            card.setAlpha(focused ? (carried ? GHOST : 1f) : (carrying ? DIM_MOVING : 1f));
            showFocusBar(card, focused && !carrying && focusStyle == FOCUS_UNDERLINE);
            haloTile(card, focused && !carrying && focusStyle == FOCUS_HALO);
            card.setOnClickListener(v -> open(a));
            card.setOnLongClickListener(v -> { menu(a, v); return true; });
            card.setOnFocusChangeListener((v, has) -> {
                focusTile(v, has);
                if (has) showApp(a, v);
            });
        }

        @Override public int getItemCount() { return items.size(); }
    }

    /** While a tile is carried: how far the others step back, and how see-through the carried one goes. */
    private static final float DIM_MOVING = 0.4f, GHOST = 0.75f;

    /**
     * An ordinary selection: the tile simply zooms and lifts — no frame, which told you nothing on white artwork
     * anyway. Carrying one (hold OK, Move) lifts it further, makes it see-through and fades every other tile, so
     * it reads as held.
     */
    private void focusTile(View v, boolean has) {
        boolean carrying = moving != null;
        float scale = has ? (carrying ? 1.15f : focusScale()) : 1f;
        float lift = has && !carrying && focusStyle == FOCUS_LIFT ? -10 * dp : 0f;
        float z = has ? (carrying ? 16 : focusStyle == FOCUS_ZOOM ? 8 : 16) * dp : 0;
        float alpha = has ? (carrying ? GHOST : 1f) : (carrying ? DIM_MOVING : 1f);
        if (animations) {
            v.animate().scaleX(scale).scaleY(scale).translationY(lift).translationZ(z).alpha(alpha)
                    .setDuration(140).start();
        } else {
            // Animations off: the same places, reached at once.
            v.animate().cancel();
            v.setScaleX(scale);
            v.setScaleY(scale);
            v.setTranslationY(lift);
            v.setTranslationZ(z);
            v.setAlpha(alpha);
        }
        showFocusBar(v, has && !carrying && focusStyle == FOCUS_UNDERLINE);
        haloTile(v, has && !carrying && focusStyle == FOCUS_HALO);
        if (has && carrying) dimOthers(v);
    }

    /** Underline leaves the tile its own size; every other style zooms it, as Home always has. */
    private float focusScale() {
        return focusStyle == FOCUS_UNDERLINE ? 1f : focusStyle == FOCUS_LIFT ? 1.06f : 1.1f;
    }

    /** The bar under an underlined tile, in the app's own colour. */
    private void showFocusBar(View v, boolean on) {
        if (!(v instanceof ViewGroup)) return;
        View bar = v.findViewWithTag(TAG_FOCUS_BAR);
        if (bar == null) return;
        bar.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    /**
     * Glass halo: the tile's shadow is painted in its own colour, so the light around it looks like the app's.
     * Tinted shadows arrived in Android 9, which is this TV; older sets keep a plain shadow.
     */
    private void haloTile(View v, boolean on) {
        if (android.os.Build.VERSION.SDK_INT < 28) return;
        int colour = on ? haloColour(v) : 0xFF000000;
        v.setOutlineSpotShadowColor(colour);
        v.setOutlineAmbientShadowColor(colour);
    }

    /**
     * The colour for that halo. Only a tile that is a child of one of the two RecyclerViews has an app behind it:
     * {@code getChildAdapterPosition} casts the view's layout parameters to RecyclerView's own, so asking it about
     * a view from anywhere else throws. The Inputs row is a LinearLayout, on Home and in the Inputs tab alike, and
     * an input has no app colour to borrow, so anything else gets plain white.
     */
    private int haloColour(View v) {
        ViewParent parent = v.getParent();
        if (parent == dockRow) {
            int at = dockRow.getChildAdapterPosition(v);
            if (at >= 0 && at < dock.size()) return dock.get(at).color;
        } else if (parent == homeGrid) {
            int at = homeGrid.getChildAdapterPosition(v);
            if (at >= 0 && at < homeItems.size() && homeItems.get(at) instanceof App) {
                return ((App) homeItems.get(at)).color;
            }
        }
        return 0xFFFFFFFF;
    }

    /** Fades every tile but this one, for the duration of a move. */
    private void dimOthers(View focused) {
        float others = moving != null ? DIM_MOVING : 1f;
        for (ViewGroup group : new ViewGroup[]{dockRow, homeGrid, homeInputs, appsGrid, inputsRow}) {
            if (group == null) continue;
            for (int i = 0; i < group.getChildCount(); i++) {
                View child = group.getChildAt(i);
                if (child == focused || child instanceof TextView) continue;   // headers keep their own look
                child.animate().cancel();
                child.setAlpha(others);
                child.setScaleX(1f);
                child.setScaleY(1f);
                child.setTranslationZ(0f);
            }
        }
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
        // Glass bars off (Launcher settings): the strip behind the pills goes, the pills keep their own look.
        if (!getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE).getBoolean(PREF_GLASS_BARS, true)) return l;
        GradientDrawable g = new GradientDrawable();
        g.setColor(0x33FFFFFF);          // frosted, no outline: the owner found the hairline edge busy
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
        // Selected (not focused) is a darker frosted pill, with no outline, which still reads over a bright glow.
        if (focused) g.setColor(Color.WHITE);
        else if (selected) g.setColor(0xA61B2029);
        else g.setColor(Color.TRANSPARENT);
        t.setBackground(g);
        int text = focused ? 0xFF111111 : selected ? Color.WHITE : 0xFFE3E8EF;
        t.setTextColor(text);
        t.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(text));
    }
}
