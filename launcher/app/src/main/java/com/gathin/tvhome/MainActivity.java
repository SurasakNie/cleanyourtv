package com.gathin.tvhome;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The HOME component, and nothing more: it opens {@link GtvActivity} and finishes.
 *
 * This class used to be the Apple TV layout. The owner removed that layout, but HOME stays here: the TV's
 * default-home setting names {@code com.gathin.tvhome/.MainActivity}, and moving the HOME intent filter to
 * GtvActivity would make Android treat this as a different launcher, leaving Home to be chosen again with the
 * stock launcher likely to win in between.
 *
 * It also carries the recovery screen. Home must never be a dead end, so when GtvActivity cannot be built —
 * it threw while building, or a previous start died before drawing — this screen says so and offers Try again
 * and TV settings instead of redirecting into a crash loop. The Apple TV layout used to be that fallback.
 */
public class MainActivity extends Activity {

    /** The quick settings panel's "Launcher settings" arrives here, since this is the exported component. */
    static final String ACTION_LAUNCHER_SETTINGS = "com.gathin.tvhome.action.LAUNCHER_SETTINGS";

    /** GtvActivity sends this when building Home threw, so the recovery screen shows without a second guess. */
    static final String ACTION_RECOVERY = "com.gathin.tvhome.action.RECOVERY";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ACTION_RECOVERY.equals(getIntent().getAction()) || GtvActivity.startFailed(this)) {
            setContentView(recoveryScreen());
            return;
        }
        startActivity(new Intent(this, GtvActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        if (savedInstanceState == null && ACTION_LAUNCHER_SETTINGS.equals(getIntent().getAction())) {
            startActivity(new Intent(this, LauncherSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        finish();
        overridePendingTransition(0, 0);
    }

    /**
     * Built in code and from nothing but this class: the screen that explains a failure cannot depend on the
     * configuration, the theme files or the fonts, any of which may be what failed.
     */
    private View recoveryScreen() {
        float dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0B0D12);
        root.setPadding((int) (64 * dp), (int) (48 * dp), (int) (64 * dp), (int) (48 * dp));

        TextView title = new TextView(this);
        title.setText(R.string.recovery_title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView body = new TextView(this);
        body.setText(R.string.recovery_body);
        body.setTextColor(0xFFC3CAD4);
        body.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        body.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = (int) (14 * dp);
        root.addView(body, blp);

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.topMargin = (int) (34 * dp);
        root.addView(buttons, rlp);

        TextView retry = button(dp, R.string.recovery_retry, v -> {
            GtvActivity.clearFailure(this);
            startActivity(new Intent(this, GtvActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
            finish();
        });
        TextView settings = button(dp, R.string.tv_settings, v -> {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_LONG).show();
            }
        });
        buttons.addView(retry);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.leftMargin = (int) (16 * dp);
        buttons.addView(settings, slp);
        retry.requestFocus();
        return root;
    }

    /** A plain focusable button: white when focused, so it reads without any of the launcher's own styling. */
    private TextView button(float dp, int textRes, View.OnClickListener onClick) {
        TextView b = new TextView(this);
        b.setText(textRes);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setPadding((int) (28 * dp), (int) (14 * dp), (int) (28 * dp), (int) (14 * dp));
        b.setFocusable(true);
        b.setFocusableInTouchMode(true);
        b.setOnClickListener(onClick);
        style(b, dp, false);
        b.setOnFocusChangeListener((v, focused) -> style((TextView) v, dp, focused));
        return b;
    }

    private static void style(TextView b, float dp, boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(14 * dp);
        g.setColor(focused ? Color.WHITE : 0x33FFFFFF);
        b.setBackground(g);
        b.setTextColor(focused ? 0xFF10131A : Color.WHITE);
    }

    /** Back on the recovery screen would leave the TV with no Home, so it does nothing. */
    @Override
    public void onBackPressed() {
    }

    /** Used by {@link GtvActivity} to come here when building Home threw. */
    static Intent recovery(Context ctx) {
        return new Intent(ctx, MainActivity.class).setAction(ACTION_RECOVERY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION);
    }
}
