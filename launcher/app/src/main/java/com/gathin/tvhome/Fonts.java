package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Launcher settings, Font: Outfit (the layouts' own font) or CMU Sans Serif. For CMU every TextView in a
 * tree gets the CMU face, bold where the Outfit face was medium or heavier. Text the serif has no glyphs
 * for (Thai, for one) falls back to the system font per character.
 */
final class Fonts {
    static final String PREF = "font";
    static final String OUTFIT = "outfit", CMU_SANS = "cmu_sans";

    private static Typeface regular, bold;

    private Fonts() { }

    static String load(Context ctx) {
        String v = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getString(PREF, OUTFIT);
        // "cmu_serif" was the first CMU choice (v18); it now means the sans.
        return CMU_SANS.equals(v) || "cmu_serif".equals(v) ? CMU_SANS : OUTFIT;
    }

    static int nameRes(String font) {
        return CMU_SANS.equals(font) ? R.string.font_cmu_sans : R.string.font_outfit;
    }

    /** Applies the chosen font to every TextView under root; Outfit leaves the layouts as they are. */
    static void apply(View root) {
        if (root == null || !CMU_SANS.equals(load(root.getContext()))) return;
        if (regular == null) {
            regular = root.getResources().getFont(R.font.cmu_sans);
            bold = root.getResources().getFont(R.font.cmu_sans_bold);
        }
        walk(root);
    }

    private static void walk(View v) {
        if (v instanceof TextView) {
            TextView t = (TextView) v;
            Typeface old = t.getTypeface();
            boolean heavy = old != null && (old.isBold()
                    || (Build.VERSION.SDK_INT >= 28 && old.getWeight() >= 500));
            if (old != regular && old != bold) t.setTypeface(heavy ? bold : regular);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) walk(g.getChildAt(i));
        }
    }
}
