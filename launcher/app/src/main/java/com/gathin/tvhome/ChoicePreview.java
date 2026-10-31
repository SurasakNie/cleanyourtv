package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * A small drawing at the top of a choice page that shows what the highlighted option looks like, so a choice can be
 * judged before it is picked. Drawn in code from the same few shapes the launcher itself uses, so it adds no image
 * to the APK. The option numbers are the ones GtvActivity stores: FOCUS_* for the focus style, BANNER_* for the banner.
 */
final class ChoicePreview extends View {
    static final int FOCUS = 0, BANNER = 1;

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

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(PANEL);
        rect.set(0, 0, w, h);
        c.drawRoundRect(rect, 12 * dp, 12 * dp, paint);
        if (kind == FOCUS) drawFocus(c, w, h);
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
