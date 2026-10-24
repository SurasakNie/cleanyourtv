package com.gathin.tvhome;

import android.content.Context;

import java.util.Random;

/**
 * Where and how the cinematic glow is drawn (Launcher settings, Glow style). The glow is a small mask
 * painted in white and tinted per app; a style only decides the mask's centre, radii and whether a second,
 * fainter glow in a shifted colour joins it. Positions are fractions of the screen.
 */
final class GlowStyle {
    static final String PREF = "glow_style";
    static final int CLASSIC = 0, RANDOM = 1, PER_APP = 2, DRIFT = 3, FOLLOW = 4, RANDOM_SHAPE = 5, TWO_GLOWS = 6;
    static final int COUNT = 7;

    /** Centre (x, y) and radii (rx, ry) of one glow, as fractions of the screen width / height. */
    static final class Spot {
        final float x, y, rx, ry;
        Spot(float x, float y, float rx, float ry) { this.x = x; this.y = y; this.rx = rx; this.ry = ry; }
        boolean same(Spot o) { return o != null && o.x == x && o.y == y && o.rx == rx && o.ry == ry; }
    }

    /** The original cinematic glow: high, from the corner away from the shelf's text. */
    static Spot classic(boolean rtl) { return new Spot(rtl ? 0.20f : 0.80f, 0.06f, 0.78f, 0.78f); }

    private static final Random RANDOM_GEN = new Random();

    static int load(Context ctx) {
        int v = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getInt(PREF, CLASSIC);
        return v >= 0 && v < COUNT ? v : CLASSIC;
    }

    static int nameRes(int style) {
        switch (style) {
            case RANDOM: return R.string.glow_random;
            case PER_APP: return R.string.glow_per_app;
            case DRIFT: return R.string.glow_drift;
            case FOLLOW: return R.string.glow_follow;
            case RANDOM_SHAPE: return R.string.glow_random_shape;
            case TWO_GLOWS: return R.string.glow_two;
            default: return R.string.glow_classic;
        }
    }

    /** A spot somewhere in the top two thirds, so the glow never sits under the dock alone. */
    static Spot random(boolean shape) {
        float x = 0.1f + RANDOM_GEN.nextFloat() * 0.8f;
        float y = RANDOM_GEN.nextFloat() * 0.55f;
        if (!shape) return new Spot(x, y, 0.78f, 0.78f);
        float r = 0.45f + RANDOM_GEN.nextFloat() * 0.5f;
        float aspect = 0.55f + RANDOM_GEN.nextFloat() * 1.0f;
        return new Spot(x, y, r, r / aspect);
    }

    /** The same spot every time for one app: derived from its package name. */
    static Spot perApp(String pkg) {
        Random r = new Random(pkg == null ? 0 : pkg.hashCode());
        return new Spot(0.1f + r.nextFloat() * 0.8f, r.nextFloat() * 0.5f, 0.78f, 0.78f);
    }

    /** Slow drift: a gentle loop over the upper screen, t in seconds. */
    static Spot drift(double t) {
        float x = 0.5f + 0.38f * (float) Math.sin(t * 0.11);
        float y = 0.28f + 0.22f * (float) Math.sin(t * 0.07 + 1.3);
        return new Spot(x, y, 0.78f, 0.78f);
    }

    /** Above the focused tile: x is the tile's centre as a fraction of the screen width. */
    static Spot follow(float tileCentreX) { return new Spot(tileCentreX, 0.30f, 0.62f, 0.62f); }

    /** The second glow of TWO_GLOWS: the opposite side, lower and smaller. */
    static Spot second(Spot first) { return new Spot(1f - first.x, Math.min(0.6f, first.y + 0.35f), 0.55f, 0.55f); }

    /** The second glow's colour: the app's colour turned 45° round the hue wheel. */
    static int shifted(int color) {
        float[] hsv = new float[3];
        android.graphics.Color.colorToHSV(color, hsv);
        hsv[0] = (hsv[0] + 45f) % 360f;
        return android.graphics.Color.HSVToColor(hsv);
    }
}
