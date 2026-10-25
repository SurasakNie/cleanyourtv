package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/** Remote-friendly settings owned by the launcher; system settings stay one row away. */
public final class LauncherSettingsActivity extends Activity {
    private SharedPreferences choices;
    private final TextView[] columnChoices = new TextView[4];
    private final TextView[] rowChoices = new TextView[4];
    private TextView wallpaperChoice;
    private TextView animationsChoice;
    private TextView advancedChoice;
    private TextView followThemeChoice;
    private TextView cinematicChoice;
    private TextView namesChoice;
    private TextView clockChoice;
    private TextView previewChoice;
    private TextView glowChoice;
    private TextView fontChoice;
    private TextView ambientChoice, perfChoice;
    private TextView bannerSizeChoice, bannerBorderChoice, bannerFillChoice;
    /** The list and the choice page that takes its place; the row to focus again when the page closes. */
    private android.view.View list, page;
    private android.widget.LinearLayout pageList;
    private TextView pageTitle;
    private TextView pageOpenedBy;
    /**
     * Rows whose setting the Google TV layout does not read yet: the glow styles, animations, advanced effects,
     * the cinematic shelf and always-on app names. They drew the Apple TV layout, and the Effects phase decides
     * what each becomes here. Hidden rather than deleted, with their values untouched, so that phase flips this
     * one line back.
     */
    private static final boolean EFFECTS_READY = false;
    private boolean ambientGlow;
    private boolean cinematic;
    private boolean alwaysShowNames;
    private int idleClockMinutes;
    /** Clock screensaver choices, in minutes; 0 is off. */
    private static final int[] CLOCK_STEPS = {0, 5, 10, 30};
    private int columns;
    private int rowsBelowDock;
    private boolean animations;
    private boolean advancedEffects;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_launcher_settings);
        Fonts.apply(findViewById(android.R.id.content));
        choices = getSharedPreferences(Config.SETTINGS_NAME, MODE_PRIVATE);
        list = findViewById(R.id.panel);
        page = findViewById(R.id.page);
        pageList = findViewById(R.id.pageList);
        pageTitle = findViewById(R.id.pageTitle);
        fillAbout();
        Config config = Config.load(this);
        columns = config.columns;
        rowsBelowDock = config.rowsBelowDock;
        animations = config.animations;
        advancedEffects = config.animations && config.advancedEffects;
        cinematic = config.cinematicHome;
        ambientGlow = config.ambientGlow;
        alwaysShowNames = config.alwaysShowNames;
        idleClockMinutes = config.homeIdleClockMinutes;

        int[] ids = {R.id.columns4, R.id.columns5, R.id.columns6, R.id.columns7};
        for (int i = 0; i < ids.length; i++) {
            final int value = i + 5;
            columnChoices[i] = findViewById(ids[i]);
            columnChoices[i].setOnClickListener(v -> {
                if (columns == value) return;
                columns = value;
                choices.edit().putInt("columns", value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        int[] rowIds = {R.id.rows0, R.id.rows1, R.id.rows2, R.id.rows3};
        for (int i = 0; i < rowIds.length; i++) {
            final int value = i;
            rowChoices[i] = findViewById(rowIds[i]);
            rowChoices[i].setOnClickListener(v -> {
                if (rowsBelowDock == value) return;
                rowsBelowDock = value;
                choices.edit().putInt(Config.ROWS_BELOW_DOCK_KEY, value).apply();
                setResult(RESULT_OK);
                refresh();
            });
        }
        // Keep the numeric choices increasing in the Hebrew reading direction. Reorder the
        // actual views so D-pad focus follows their positions without changing the saved values.
        if (getResources().getBoolean(R.bool.rtl_app_grid)) {
            reverseRow(columnChoices);
            reverseRow(rowChoices);
        }
        wallpaperChoice = findViewById(R.id.wallpaperChoice);
        wallpaperChoice.setOnClickListener(v -> showWallpaperPicker());
        animationsChoice = findViewById(R.id.animationsChoice);
        animationsChoice.setOnClickListener(v -> {
            animations = !animations;
            if (!animations) advancedEffects = false;
            choices.edit().putBoolean("animations", animations)
                    .putBoolean("advanced_effects", advancedEffects).apply();
            setResult(RESULT_OK);
            refresh();
        });
        advancedChoice = findViewById(R.id.advancedChoice);
        advancedChoice.setOnClickListener(v -> {
            if (advancedEffects) {
                setAdvanced(false);
            } else {
                new AlertDialog.Builder(this)
                        .setTitle(R.string.advanced_effects)
                        .setMessage(R.string.advanced_effects_warning)
                        .setPositiveButton(R.string.enable, (dialog, which) -> setAdvanced(true))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }
        });
        cinematicChoice = findViewById(R.id.cinematicChoice);
        cinematicChoice.setOnClickListener(v -> {
            cinematic = !cinematic;
            choices.edit().putBoolean("cinematic_home", cinematic).apply();
            setResult(RESULT_OK);
            refresh();
        });
        namesChoice = findViewById(R.id.namesChoice);
        namesChoice.setOnClickListener(v -> {
            alwaysShowNames = !alwaysShowNames;
            choices.edit().putBoolean("always_show_names", alwaysShowNames).apply();
            setResult(RESULT_OK);
            refresh();
        });
        clockChoice = findViewById(R.id.clockChoice);
        clockChoice.setOnClickListener(v -> {
            int next = CLOCK_STEPS[0];
            for (int i = 0; i < CLOCK_STEPS.length; i++) {
                if (CLOCK_STEPS[i] > idleClockMinutes) { next = CLOCK_STEPS[i]; break; }
            }
            idleClockMinutes = next;
            choices.edit().putInt("home_idle_clock_minutes", next).apply();
            setResult(RESULT_OK);
            refresh();
        });
        glowChoice = findViewById(R.id.glowChoice);
        glowChoice.setOnClickListener(v -> {
            if (ambientGlow) showGlowPicker();
        });
        ambientChoice = findViewById(R.id.ambientChoice);
        ambientChoice.setOnClickListener(v -> {
            ambientGlow = !ambientGlow;
            choices.edit().putBoolean("ambient_glow", ambientGlow).apply();
            setResult(RESULT_OK);
            refresh();
        });
        perfChoice = findViewById(R.id.perfChoice);
        perfChoice.setOnClickListener(v -> {
            boolean on = !choices.getBoolean(Config.PERF_CARD_KEY, false);
            choices.edit().putBoolean(Config.PERF_CARD_KEY, on).apply();
            setResult(RESULT_OK);
            refresh();
        });
        fontChoice = findViewById(R.id.fontChoice);
        fontChoice.setOnClickListener(v -> showFontPicker());
        bannerSizeChoice = findViewById(R.id.bannerSizeChoice);
        bannerSizeChoice.setOnClickListener(v -> showBannerSizePage());
        bannerBorderChoice = findViewById(R.id.bannerBorderChoice);
        bannerBorderChoice.setOnClickListener(v -> toggle(GtvActivity.PREF_BANNER_BORDER));
        bannerFillChoice = findViewById(R.id.bannerFillChoice);
        bannerFillChoice.setOnClickListener(v -> toggle(GtvActivity.PREF_BANNER_FILL));
        previewChoice = findViewById(R.id.previewChoice);
        previewChoice.setOnClickListener(v -> {
            choices.edit().putBoolean(InputPreview.PREF_ON, !InputPreview.enabled(this)).apply();
            setResult(RESULT_OK);
            refresh();
        });
        followThemeChoice = findViewById(R.id.followThemeChoice);
        followThemeChoice.setOnClickListener(v -> {
            if (!hasOnTvChoices()) return;
            choices.edit().remove("columns").remove(Config.ROWS_BELOW_DOCK_KEY).remove("animations")
                    .remove("advanced_effects").remove(Config.WALLPAPER_CHOICE_KEY)
                    .remove("cinematic_home").remove("always_show_names").remove("home_idle_clock_minutes").remove("ambient_glow").remove(Config.PERF_CARD_KEY)
                    .remove(GtvActivity.PREF_BANNER_SP).remove(GtvActivity.PREF_BANNER_BORDER)
                    .remove(GtvActivity.PREF_BANNER_FILL).commit();
            Config current = Config.load(this);
            columns = current.columns;
            rowsBelowDock = current.rowsBelowDock;
            animations = current.animations;
            advancedEffects = current.animations && current.advancedEffects;
            cinematic = current.cinematicHome;
            ambientGlow = current.ambientGlow;
            alwaysShowNames = current.alwaysShowNames;
            idleClockMinutes = current.homeIdleClockMinutes;
            setResult(RESULT_OK);
            refresh();
        });
        findViewById(R.id.defaultHomeChoice).setOnClickListener(v -> {
            Toast.makeText(this, R.string.home_settings_help, Toast.LENGTH_LONG).show();
            open(new Intent(Settings.ACTION_HOME_SETTINGS));
        });
        findViewById(R.id.tvSettingsChoice).setOnClickListener(v ->
                open(new Intent(Settings.ACTION_SETTINGS)));
        refresh();
        columnChoices[Math.max(0, Math.min(columns - 5, 3))].requestFocus();
    }

    private void reverseRow(TextView[] views) {
        LinearLayout row = (LinearLayout) views[0].getParent();
        row.removeAllViews();
        for (int i = views.length - 1; i >= 0; i--) row.addView(views[i]);
    }

    /**
     * Opens a choice as the next page in the panel: the list steps aside, the page takes its place at the same
     * width, and Back brings the list back with the row that opened it still focused. The list is GONE rather
     * than merely covered, or the remote could still walk through it behind the page.
     */
    private void showPage(int titleRes, String[] labels, int checked, java.util.function.IntConsumer onPick) {
        pageOpenedBy = null;
        if (getCurrentFocus() instanceof TextView) pageOpenedBy = (TextView) getCurrentFocus();
        pageTitle.setText(titleRes);
        // Keep the title and the Back hint; drop the rows of the page shown before this one.
        while (pageList.getChildCount() > 2) pageList.removeViewAt(2);
        for (int i = 0; i < labels.length; i++) {
            final int which = i;
            TextView row = new TextView(this, null, 0, R.style.SettingsRow);
            row.setText(labels[i]);
            row.setSelected(i == checked);
            // Set in code as well as in the style: the remote must reach these rows even if the style does not take.
            row.setFocusable(true);
            row.setClickable(true);
            row.setOnClickListener(v -> {
                closePage();
                onPick.accept(which);
            });
            android.widget.LinearLayout.LayoutParams lp =
                    new android.widget.LinearLayout.LayoutParams(-1, (int) (48 * getResources().getDisplayMetrics().density));
            lp.topMargin = (int) (10 * getResources().getDisplayMetrics().density);
            pageList.addView(row, lp);
        }
        Fonts.apply(page);
        list.setVisibility(android.view.View.GONE);
        page.setVisibility(android.view.View.VISIBLE);
        pageList.getChildAt(2 + Math.max(0, checked)).requestFocus();
    }

    private void closePage() {
        page.setVisibility(android.view.View.GONE);
        list.setVisibility(android.view.View.VISIBLE);
        if (pageOpenedBy != null) pageOpenedBy.requestFocus();
    }

    /** Back closes an open page first; from the list it leaves settings, as before. */
    @Override
    public void onBackPressed() {
        if (page.getVisibility() == android.view.View.VISIBLE) {
            closePage();
            return;
        }
        super.onBackPressed();
    }

    /** Font: Outfit or CMU Sans Serif; this screen redraws in the new font straight away. */
    private void showFontPicker() {
        String[] values = {Fonts.OUTFIT, Fonts.CMU_SANS};
        String[] labels = {getString(R.string.font_outfit), getString(R.string.font_cmu_sans)};
        int checked = Fonts.CMU_SANS.equals(Fonts.load(this)) ? 1 : 0;
        showPage(R.string.font_title, labels, checked, which -> {
            if (which == checked) return;
            choices.edit().putString(Fonts.PREF, values[which]).apply();
            setResult(RESULT_OK);
            recreate();
        });
    }

    /** Banner title size: the three sizes the banner's own text can take. */
    private void showBannerSizePage() {
        int[] sizes = BANNER_SIZES;
        String[] labels = {getString(R.string.banner_size_small), getString(R.string.banner_size_medium),
                getString(R.string.banner_size_large)};
        int current = choices.getInt(GtvActivity.PREF_BANNER_SP, GtvActivity.BANNER_SP_DEFAULT);
        int checked = 1;
        for (int i = 0; i < sizes.length; i++) if (sizes[i] == current) checked = i;
        showPage(R.string.banner_size_title, labels, checked, which -> {
            choices.edit().putInt(GtvActivity.PREF_BANNER_SP, sizes[which]).apply();
            setResult(RESULT_OK);
            refresh();
        });
    }

    /** The banner sizes in sp, in the order the page lists them; the middle one is the banner's own default. */
    private static final int[] BANNER_SIZES = {34, GtvActivity.BANNER_SP_DEFAULT, 58};

    /** One on/off setting, stored and reflected straight away. */
    private void toggle(String key) {
        choices.edit().putBoolean(key, !choices.getBoolean(key, false)).apply();
        setResult(RESULT_OK);
        refresh();
    }

    /**
     * Every glow style in one list, the current one marked. The row is hidden until the Effects phase teaches
     * GtvActivity to read GlowStyle, but the page is here and ready for it.
     */
    private void showGlowPicker() {
        String[] labels = new String[GlowStyle.COUNT];
        for (int i = 0; i < labels.length; i++) labels[i] = getString(GlowStyle.nameRes(i));
        showPage(R.string.glow_title, labels, GlowStyle.load(this), which -> {
            choices.edit().putInt(GlowStyle.PREF, which).apply();
            setResult(RESULT_OK);
            refresh();
        });
    }

    private void showWallpaperPicker() {
        File dir = Config.dir(this);
        List<File> images = new java.util.ArrayList<>(WallpaperRotation.images(
                dir == null ? null : new File(dir, "wallpapers")));
        // A theme with a single wallpaper.jpg offers that picture too (Config maps the choice back to it).
        File single = dir == null ? null : new File(dir, Config.WALLPAPER_NAME);
        if (single != null && single.isFile()) images.add(0, single);
        String[] labels = new String[images.size() + 1];
        for (int i = 0; i < images.size(); i++) labels[i] = images.get(i).getName();
        labels[images.size()] = getString(R.string.wallpaper_none);
        // The theme's own choice is the last row of the page, where the dialog had it as a third button.
        String[] rows = new String[labels.length + 1];
        System.arraycopy(labels, 0, rows, 0, labels.length);
        rows[labels.length] = getString(R.string.wallpaper_theme_default);
        String current = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        int checked = current == null ? rows.length - 1
                : "none".equals(current) ? images.size()
                : indexOf(images, current);
        showPage(R.string.wallpaper_label, rows, checked, which -> {
            if (which == rows.length - 1) {
                choices.edit().remove(Config.WALLPAPER_CHOICE_KEY).apply();
            } else if (which == images.size()) {
                choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, "none").apply();
            } else {
                choices.edit().putString(Config.WALLPAPER_CHOICE_KEY, images.get(which).getName()).apply();
            }
            setResult(RESULT_OK);
            Toast.makeText(this, R.string.wallpaper_applied, Toast.LENGTH_SHORT).show();
            refresh();
        });
    }

    private static int indexOf(List<File> images, String name) {
        if (name == null) return -1;
        for (int i = 0; i < images.size(); i++) {
            if (images.get(i).getName().equals(name)) return i;
        }
        return -1;
    }

    private void setAdvanced(boolean enabled) {
        advancedEffects = enabled;
        choices.edit().putBoolean("advanced_effects", enabled).apply();
        setResult(RESULT_OK);
        refresh();
    }

    /**
     * About this UI: what it is, which build, and the set it is running on. Read-only, and not focusable, so the
     * remote runs past it instead of stopping on each line. The version comes from the installed package rather
     * than BuildConfig, which this build does not generate.
     */
    private void fillAbout() {
        String version = getString(R.string.unknown);
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
            version = getString(R.string.about_version_value, info.versionName, code);
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            // Cannot happen for our own package, but a missing version must not take the panel down.
        }
        ((TextView) findViewById(R.id.aboutVersion)).setText(getString(R.string.about_version, version));
        ((TextView) findViewById(R.id.aboutLayout)).setText(getString(R.string.about_layout,
                getString(R.string.layout_gtv)));
        ((TextView) findViewById(R.id.aboutTv)).setText(getString(R.string.about_tv,
                android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL));
        ((TextView) findViewById(R.id.aboutAndroid)).setText(getString(R.string.about_android,
                android.os.Build.VERSION.RELEASE, android.os.Build.VERSION.SDK_INT));
    }

    private void refresh() {
        for (int i = 0; i < columnChoices.length; i++) {
            columnChoices[i].setSelected(columns == i + 5);
        }
        for (int i = 0; i < rowChoices.length; i++) {
            rowChoices[i].setSelected(rowsBelowDock == i);
        }
        animationsChoice.setText(getString(R.string.animations_state,
                getString(animations ? R.string.on : R.string.off)));
        advancedChoice.setText(getString(R.string.advanced_effects_state,
                getString(advancedEffects ? R.string.on : R.string.off)));
        advancedChoice.setEnabled(animations);
        advancedChoice.setAlpha(animations ? 1f : 0.5f);
        String wallpaperCurrent = choices.getString(Config.WALLPAPER_CHOICE_KEY, null);
        wallpaperChoice.setText(getString(R.string.wallpaper_state,
                "none".equals(wallpaperCurrent) ? getString(R.string.wallpaper_none)
                        : wallpaperCurrent != null ? wallpaperCurrent
                        : getString(R.string.wallpaper_theme_default)));
        cinematicChoice.setText(getString(R.string.cinematic_state,
                getString(cinematic ? R.string.on : R.string.off)));
        namesChoice.setText(getString(R.string.names_state,
                getString(alwaysShowNames ? R.string.on : R.string.off)));
        clockChoice.setText(getString(R.string.clock_state, idleClockMinutes == 0
                ? getString(R.string.off) : getString(R.string.minutes_short, idleClockMinutes)));
        glowChoice.setText(getString(R.string.glow_state, getString(GlowStyle.nameRes(GlowStyle.load(this)))));
        glowChoice.setEnabled(ambientGlow);
        glowChoice.setAlpha(ambientGlow ? 1f : 0.5f);
        ambientChoice.setText(getString(R.string.ambient_state,
                getString(ambientGlow ? R.string.on : R.string.off)));
        ((TextView) findViewById(R.id.hintRows)).setText(R.string.hint_rows_gtv);
        bannerSizeChoice.setText(getString(R.string.banner_size_state, getString(bannerSizeLabel())));
        bannerBorderChoice.setText(getString(R.string.banner_border_state,
                getString(choices.getBoolean(GtvActivity.PREF_BANNER_BORDER, false) ? R.string.on : R.string.off)));
        bannerFillChoice.setText(getString(R.string.banner_fill_state,
                getString(choices.getBoolean(GtvActivity.PREF_BANNER_FILL, false) ? R.string.on : R.string.off)));
        int unread = EFFECTS_READY ? android.view.View.VISIBLE : android.view.View.GONE;
        for (int id : new int[]{R.id.glowChoice, R.id.hintGlow, R.id.animationsChoice, R.id.hintAnimations,
                R.id.advancedChoice, R.id.hintAdvanced, R.id.cinematicChoice, R.id.hintCinematic,
                R.id.namesChoice, R.id.hintNames}) {
            findViewById(id).setVisibility(unread);
        }
        perfChoice.setText(getString(R.string.perf_state,
                getString(choices.getBoolean(Config.PERF_CARD_KEY, false) ? R.string.on : R.string.off)));
        fontChoice.setText(getString(R.string.font_state, getString(Fonts.nameRes(Fonts.load(this)))));
        previewChoice.setText(getString(R.string.preview_state,
                getString(InputPreview.enabled(this) ? R.string.on : R.string.off)));
        followThemeChoice.setSelected(!hasOnTvChoices());
    }

    /** Which of the three banner sizes is stored; anything else (a hand-edited value) reads as Medium. */
    private int bannerSizeLabel() {
        int sp = choices.getInt(GtvActivity.PREF_BANNER_SP, GtvActivity.BANNER_SP_DEFAULT);
        if (sp == BANNER_SIZES[0]) return R.string.banner_size_small;
        if (sp == BANNER_SIZES[2]) return R.string.banner_size_large;
        return R.string.banner_size_medium;
    }

    private boolean hasOnTvChoices() {
        return choices.contains("columns") || choices.contains(Config.ROWS_BELOW_DOCK_KEY)
                || choices.contains("animations") || choices.contains("advanced_effects")
                || choices.contains(Config.WALLPAPER_CHOICE_KEY) || choices.contains("cinematic_home")
                || choices.contains("always_show_names") || choices.contains("home_idle_clock_minutes") || choices.contains("ambient_glow")
                || choices.contains(Config.PERF_CARD_KEY) || choices.contains(GtvActivity.PREF_BANNER_SP)
                || choices.contains(GtvActivity.PREF_BANNER_BORDER) || choices.contains(GtvActivity.PREF_BANNER_FILL);
    }

    private void open(Intent intent) {
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
        }
    }
}
