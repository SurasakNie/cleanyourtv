package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.view.View;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A small drawing at the top of a choice page that shows what the highlighted option looks like, so a choice can be
 * judged before it is picked. Drawn in code from the same few shapes the launcher itself uses, so it adds no image
 * to the APK. The option numbers are the ones that are stored: GtvActivity.FOCUS_* for the focus style, BANNER_* for
 * the banner and GlowStyle's own for the glow.
 */
final class ChoicePreview extends View {
    static final int FOCUS = 0, BANNER = 1, GLOW = 2, FONT = 3, WALLPAPER = 4;

    private static final int PANEL = 0xFF10131A, TILE_A = 0xFF3B5BA9, TILE_B = 0xFFC0392B, TILE_C = 0xFF2E8B57;

    private final int kind;
    private int option;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float dp;

    ChoicePreview(Context context, int kind, int option) {
        super(context);
        this.kind = kind;
        this.option = option;
        this.dp = context.getResources().getDisplayMetrics().density;
        setLayerType(LAYER_TYPE_SOFTWARE, null);   // shadow layers (the halo) need it
        setFocusable(false);
    }

    /** The option the highlighted row stands for; redraws only when it changes. */
    void setOption(int option) {
        if (option == this.option) return;
        this.option = option;
        invalidate();
    }

    // ---- Wallpaper --------------------------------------------------------------------------------

    private List<File> images;
    private final Map<Integer, Bitmap> pictures = new HashMap<>();
    private int loading = -1;

    /** The pictures the Wallpaper page lists, in row order; the two rows after them are "none" and the theme's own. */
    void setImages(List<File> images) {
        this.images = images;
        invalidate();
    }

    /**
     * Decodes one picture small, off the main thread: a theme's wallpaper can be a full-size JPEG, and decoding it in
     * onDraw on this TV's processor would stall the remote.
     */
    private void load(int index) {
        if (images == null || index < 0 || index >= images.size() || pictures.containsKey(index) || loading == index) return;
        loading = index;
        final File file = images.get(index);
        new Thread(() -> {
            Bitmap decoded = null;
            try {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getPath(), bounds);
                int sample = 1;
                while (bounds.outWidth / (sample * 2) >= 640) sample *= 2;
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = sample;
                decoded = BitmapFactory.decodeFile(file.getPath(), o);
            } catch (RuntimeException | OutOfMemoryError e) {
                decoded = null;
            }
            final Bitmap result = decoded;
            post(() -> {
                if (result != null) pictures.put(index, result);
                if (loading == index) loading = -1;
                invalidate();
            });
        }).start();
    }

    /** The picture behind a small copy of Home's tiles, or a line saying what the row means. */
    private void drawWallpaper(Canvas c, float w, float h) {
        float sh = h - 16 * dp, sw = sh * 16 / 9, left = (w - sw) / 2, top = 8 * dp;
        rect.set(left, top, left + sw, top + sh);
        c.save();
        c.clipRect(rect);
        paint.setShader(null);
        paint.setColor(0xFF0A0C11);
        c.drawRect(rect, paint);
        int count = images == null ? 0 : images.size();
        if (option < count) {
            load(option);
            Bitmap b = pictures.get(option);
            if (b != null) {
                // Centre-crop to the screen's shape, as the launcher fills its wallpaper.
                float scale = Math.max(sw / b.getWidth(), sh / b.getHeight());
                float dw = b.getWidth() * scale, dh = b.getHeight() * scale;
                RectF dst = new RectF(left + (sw - dw) / 2, top + (sh - dh) / 2, left + (sw + dw) / 2, top + (sh + dh) / 2);
                c.drawBitmap(b, null, dst, paint);
            }
        }
        paint.setColor(0xCC1B2029);
        float tw = sw * 0.16f, th = sh * 0.2f, ty = top + sh - th - 6 * dp;
        for (int i = 0; i < 4; i++) {
            float x = left + sw * 0.08f + i * (tw + sw * 0.03f);
            RectF t = new RectF(x, ty, x + tw, ty + th);
            c.drawRoundRect(t, 4 * dp, 4 * dp, paint);
        }
        if (option >= count) {
            // "No wallpaper" and "the theme's own" have no picture of their own to show.
            paint.setColor(0xFFC3CAD4);
            paint.setTextSize(Math.max(12 * dp, sh * 0.12f));
            paint.setTextAlign(Paint.Align.CENTER);
            c.drawText(getContext().getString(option == count ? R.string.wallpaper_none : R.string.wallpaper_theme_default),
                    left + sw / 2, top + sh * 0.4f, paint);
            paint.setTextAlign(Paint.Align.LEFT);
        }
        c.restore();
    }

    // ---- Font -------------------------------------------------------------------------------------

    /** The banner's own lines set in the highlighted font: 0 is Outfit, 1 is CMU Sans (Fonts.OUTFIT, Fonts.CMU_SANS). */
    private void drawFont(Canvas c, float w, float h) {
        Typeface face = getResources().getFont(option == 1 ? R.font.cmu_sans : R.font.outfit_medium);
        paint.setShader(null);
        paint.setTypeface(face);
        paint.setColor(Color.WHITE);
        paint.setTextSize(h * 0.3f);
        c.drawText("App name", 20 * dp, h * 0.38f, paint);
        paint.setColor(0xFFC3CAD4);
        paint.setTextSize(h * 0.15f);
        c.drawText("Last opened 2 days ago", 20 * dp, h * 0.6f, paint);
        c.drawText("Home   Apps   Inputs   0123456789", 20 * dp, h * 0.84f, paint);
        paint.setTypeface(Typeface.DEFAULT);
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, 12 * dp, 12 * dp, paint);
        if (kind == FOCUS) drawFocus(c, w, h);
        else if (kind == GLOW) drawGlow(c, w, h);
        else if (kind == FONT) drawFont(c, w, h);
        else if (kind == WALLPAPER) drawWallpaper(c, w, h);
        else drawBanner(c, w, h);
    }

    private static int darker(int colour) {
        return Color.rgb(Color.red(colour) / 3, Color.green(colour) / 3, Color.blue(colour) / 3);
    }

    /** Three tiles with the middle one selected the way the option says. */
    private void drawFocus(Canvas c, float w, float h) {
        float tw = w * 0.22f, th = tw * 0.62f, corner = 8 * dp;
        float[] cx = {w * 0.2f, w * 0.5f, w * 0.8f};
        int[] colours = {TILE_A, TILE_B, TILE_C};
        for (int i = 0; i < 3; i++) {
            boolean focused = i == 1;
            float scale = 1f, dy = 0f;
            boolean bar = false, halo = false;
            if (focused) {
                switch (option) {
                    case GtvActivity.FOCUS_LIFT: scale = 1.06f; dy = -th * 0.12f; break;
                    case GtvActivity.FOCUS_HALO: scale = 1.1f; halo = true; break;
                    case GtvActivity.FOCUS_UNDERLINE: bar = true; break;
                    case GtvActivity.FOCUS_LIFT_BAR: dy = -th * 0.12f; bar = true; break;
                    default: scale = 1.15f; break;   // Zoom
                }
            }
            float cy = h * 0.5f + dy, sw = tw * scale, sh = th * scale;
            rect.set(cx[i] - sw / 2, cy - sh / 2, cx[i] + sw / 2, cy + sh / 2);
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            if (focused) {
                paint.setShadowLayer(halo ? 18 * dp : 8 * dp, 0, halo ? 0 : 4 * dp, halo ? colours[i] : 0x99000000);
            } else {
                paint.clearShadowLayer();
            }
            paint.setColor(focused ? colours[i] : darker(colours[i]));
            c.drawRoundRect(rect, corner, corner, paint);
            paint.clearShadowLayer();
            if (bar) {
                // Along the bottom edge, clipped to the tile's own corners like the real bar.
                c.save();
                RectF clip = new RectF(rect);
                android.graphics.Path path = new android.graphics.Path();
                path.addRoundRect(clip, corner, corner, android.graphics.Path.Direction.CW);
                c.clipPath(path);
                paint.setColor(Color.WHITE);
                c.drawRect(rect.left, rect.bottom - 4 * dp, rect.right, rect.bottom, paint);
                c.restore();
            }
        }
    }

    /**
     * The glow on a small 16:9 screen, placed with GlowStyle's own spots so the drawing and the launcher agree. A style
     * that moves (Random, Slow drift) shows where it can go as faint marks; Random shape uses ellipses.
     */
    private void drawGlow(Canvas c, float w, float h) {
        float sh = h - 16 * dp, sw = sh * 16 / 9, left = (w - sw) / 2, top = 8 * dp;
        c.save();
        rect.set(left, top, left + sw, top + sh);
        c.clipRect(rect);
        paint.setShader(null);
        paint.setColor(0xFF0A0C11);
        c.drawRect(rect, paint);
        // The row of tiles the glow sits behind.
        paint.setColor(0xFF1B2029);
        float tw = sw * 0.16f, th = sh * 0.2f, ty = top + sh - th - 6 * dp;
        for (int i = 0; i < 4; i++) {
            float x = left + sw * 0.08f + i * (tw + sw * 0.03f);
            RectF t = new RectF(x, ty, x + tw, ty + th);
            c.drawRoundRect(t, 4 * dp, 4 * dp, paint);
        }
        int colour = TILE_B;
        switch (option) {
            case GlowStyle.RANDOM:
            case GlowStyle.RANDOM_2:
            case GlowStyle.RANDOM_3:
                // Anywhere in the top two thirds: three faint places stand for it.
                spot(c, left, top, sw, sh, new GlowStyle.Spot(0.2f, 0.2f, 0.78f, 0.78f), colour, 0.35f);
                spot(c, left, top, sw, sh, new GlowStyle.Spot(0.55f, 0.4f, 0.78f, 0.78f), colour, 0.35f);
                spot(c, left, top, sw, sh, new GlowStyle.Spot(0.82f, 0.12f, 0.78f, 0.78f), colour, 0.35f);
                if (option != GlowStyle.RANDOM) {
                    spot(c, left, top, sw, sh, new GlowStyle.Spot(0.7f, 0.5f, 0.55f, 0.55f), GlowStyle.shifted(colour), 0.45f);
                }
                if (option == GlowStyle.RANDOM_3) {
                    spot(c, left, top, sw, sh, new GlowStyle.Spot(0.3f, 0.55f, 0.55f, 0.55f), GlowStyle.shifted(colour, 90f), 0.45f);
                }
                break;
            case GlowStyle.PER_APP:
                spot(c, left, top, sw, sh, GlowStyle.perApp("com.example.app"), colour, 1f);
                break;
            case GlowStyle.DRIFT:
                // The loop it follows, faint, with one spot on it.
                for (int t = 0; t < 60; t += 6) {
                    spot(c, left, top, sw, sh, GlowStyle.drift(t), colour, 0.18f);
                }
                spot(c, left, top, sw, sh, GlowStyle.drift(20), colour, 0.7f);
                break;
            case GlowStyle.FOLLOW:
                spot(c, left, top, sw, sh, GlowStyle.follow(0.4f), colour, 1f);
                break;
            case GlowStyle.RANDOM_SHAPE:
                spot(c, left, top, sw, sh, new GlowStyle.Spot(0.35f, 0.25f, 0.95f, 0.5f), colour, 0.6f);
                spot(c, left, top, sw, sh, new GlowStyle.Spot(0.72f, 0.3f, 0.5f, 0.9f), colour, 0.5f);
                break;
            case GlowStyle.TWO_GLOWS: {
                GlowStyle.Spot first = GlowStyle.classic(false);
                spot(c, left, top, sw, sh, first, colour, 1f);
                spot(c, left, top, sw, sh, GlowStyle.second(first), GlowStyle.shifted(colour), 0.8f);
                break;
            }
            case GlowStyle.RIBBON: {
                // A diagonal band across the screen instead of a spot.
                paint.setShader(new LinearGradient(left, top + sh * 0.9f, left + sw, top + sh * 0.1f,
                        new int[]{0x00000000, (colour & 0x00FFFFFF) | 0x99000000, 0x00000000},
                        new float[]{0.2f, 0.5f, 0.8f}, Shader.TileMode.CLAMP));
                c.drawRect(rect, paint);
                paint.setShader(null);
                break;
            }
            default:   // Classic
                spot(c, left, top, sw, sh, GlowStyle.classic(false), colour, 1f);
        }
        c.restore();
    }

    /** One glow: a radial fade from the colour to nothing, stretched to the spot's two radii like the launcher does. */
    private void spot(Canvas c, float left, float top, float sw, float sh, GlowStyle.Spot s, int colour, float strength) {
        float cx = left + s.x * sw, cy = top + s.y * sh;
        float radius = sw * 0.6f * (s.rx / 0.78f);
        int alpha = Math.round(0x99 * strength);
        c.save();
        c.translate(cx, cy);
        c.scale(1f, s.ry / s.rx);
        paint.setShader(new RadialGradient(0, 0, radius, (colour & 0x00FFFFFF) | (alpha << 24), colour & 0x00FFFFFF,
                Shader.TileMode.CLAMP));
        c.drawCircle(0, 0, radius, paint);
        paint.setShader(null);
        c.restore();
    }

    /** The banner above a faint row of tiles: glow and name, glass panel, nothing, or a window of the input. */
    private void drawBanner(Canvas c, float w, float h) {
        // The rows the banner sits above.
        paint.setShader(null);
        paint.setColor(0xFF1B2029);
        float tw = w * 0.16f, th = h * 0.2f, ty = h - th - 8 * dp;
        for (int i = 0; i < 4; i++) {
            float x = w * 0.06f + i * (tw + w * 0.03f);
            rect.set(x, ty, x + tw, ty + th);
            c.drawRoundRect(rect, 5 * dp, 5 * dp, paint);
        }
        if (option == GtvActivity.BANNER_OFF) return;
        float left = w * 0.06f;
        if (option == GtvActivity.BANNER_NAME) {
            paint.setShader(new RadialGradient(w * 0.3f, h * 0.35f, w * 0.35f, 0x88C0392B, 0x00C0392B, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        } else if (option == GtvActivity.BANNER_GLASS) {
            rect.set(left - 8 * dp, 8 * dp, left + w * 0.5f, ty - 8 * dp);
            paint.setColor(0xCC0B0D12);
            c.drawRoundRect(rect, 10 * dp, 10 * dp, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1 * dp);
            paint.setColor(0x44FFFFFF);
            c.drawRoundRect(rect, 10 * dp, 10 * dp, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        float titleBase = ty - 16 * dp;
        if (option == GtvActivity.BANNER_LIVE) {
            // A 16:9 window of the picture, with the name under it.
            float ww = w * 0.3f, wh = ww * 9 / 16, top = 8 * dp;
            rect.set(left, top, left + ww, top + wh);
            paint.setShader(new LinearGradient(rect.left, rect.top, rect.right, rect.bottom, 0xFF3B5BA9, 0xFF2E8B57,
                    Shader.TileMode.CLAMP));
            c.drawRoundRect(rect, 6 * dp, 6 * dp, paint);
            paint.setShader(null);
            titleBase = Math.min(titleBase, top + wh + 16 * dp);
        }
        paint.setColor(Color.WHITE);
        paint.setTextSize(Math.max(12 * dp, h * 0.16f));
        c.drawText("App name", left, titleBase, paint);
    }
}
