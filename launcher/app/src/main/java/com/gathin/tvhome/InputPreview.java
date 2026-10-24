package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvView;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Optional small live window of one HDMI input on Home (Launcher settings, "Input preview").
 * Experimental: some TVs do not let a non-system app show an input, then the window says so.
 * The input is tuned only while Home is in front and released on pause, so the TV's own
 * Inputs and Live TV get the hardware back.
 */
final class InputPreview {
    static final String PREF_ON = "input_preview";
    static final String PREF_SOURCE = "input_preview_source";

    /** One HDMI port: its input id and the name to show. */
    static final class Port {
        final String id;
        final String label;
        Port(String id, String label) { this.id = id; this.label = label; }
    }

    private final Context ctx;
    private final SharedPreferences prefs;
    private final View frame;
    private final TvView tv;
    private final TextView status;
    private String tunedId;

    InputPreview(Context ctx, View frame, TvView tv, TextView status) {
        this.ctx = ctx;
        this.prefs = ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
        this.frame = frame;
        this.tv = tv;
        this.status = status;
        tv.setClipToOutline(true);
        tv.setCallback(new TvView.TvInputCallback() {
            @Override public void onConnectionFailed(String inputId) { showStatus(R.string.preview_unavailable); }
            @Override public void onDisconnected(String inputId) { showStatus(R.string.preview_unavailable); }
            @Override public void onVideoAvailable(String inputId) { status.setVisibility(View.GONE); }
            @Override public void onVideoUnavailable(String inputId, int reason) { showStatus(R.string.preview_no_signal); }
        });
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(PREF_ON, false);
    }

    boolean enabled() { return prefs.getBoolean(PREF_ON, false); }

    /** HDMI ports in name order, without hidden ones and without HDMI-CEC device sub-inputs. */
    List<Port> ports() {
        List<Port> out = new ArrayList<>();
        TvInputManager tim = (TvInputManager) ctx.getSystemService(Context.TV_INPUT_SERVICE);
        if (tim == null) return out;
        List<TvInputInfo> inputs = tim.getTvInputList();
        Set<String> ids = new HashSet<>();
        for (TvInputInfo info : inputs) ids.add(info.getId());
        for (TvInputInfo info : inputs) {
            if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
            if (info.isHidden(ctx)) continue;
            if (info.getParentId() != null && ids.contains(info.getParentId())) continue;
            CharSequence l = info.loadLabel(ctx);
            String label = l == null ? info.getId() : l.toString();
            if (!label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
            CharSequence custom = info.loadCustomLabel(ctx);
            if (custom != null && custom.length() > 0 && !custom.toString().equals(label)) {
                label = custom + " · " + label;
            }
            out.add(new Port(info.getId(), label));
        }
        out.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        return out;
    }

    /** The chosen port, or the first one when none is chosen or it is gone. */
    String sourceId() {
        List<Port> ports = ports();
        String chosen = prefs.getString(PREF_SOURCE, null);
        for (Port p : ports) if (p.id.equals(chosen)) return p.id;
        return ports.isEmpty() ? null : ports.get(0).id;
    }

    void choose(String inputId) {
        prefs.edit().putString(PREF_SOURCE, inputId).apply();
        start();
    }

    /** Home came to the front: show and tune, or stay hidden. */
    void start() {
        if (!enabled()) {
            frame.setVisibility(View.GONE);
            stop();
            return;
        }
        String id = sourceId();
        frame.setVisibility(View.VISIBLE);
        if (id == null) {
            showStatus(R.string.preview_unavailable);
            return;
        }
        if (id.equals(tunedId)) return;
        showStatus(R.string.preview_connecting);
        try {
            tv.setStreamVolume(0f);
            tv.tune(id, TvContract.buildChannelUriForPassthroughInput(id));
            tunedId = id;
        } catch (RuntimeException e) {
            tunedId = null;
            showStatus(R.string.preview_unavailable);
        }
    }

    /** Home went to the background: give the input back to the TV. */
    void stop() {
        if (tunedId == null) return;
        try {
            tv.reset();
        } catch (RuntimeException ignored) {
        }
        tunedId = null;
    }

    private void showStatus(int text) {
        status.setText(text);
        status.setVisibility(View.VISIBLE);
    }
}
