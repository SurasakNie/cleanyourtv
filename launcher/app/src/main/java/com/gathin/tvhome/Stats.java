package com.gathin.tvhome;

import android.content.Context;
import android.net.TrafficStats;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the TV is doing right now: processor load, memory, storage, temperature, frame rate, network and uptime.
 * One reader per screen, because the processor and network figures are differences between two samples.
 *
 * Everything comes from files and system services this app may already read; nothing needs a new permission and
 * nothing leaves the TV. A figure the TV will not give up (Android hides some of these from apps, and which ones
 * varies by set) is simply left out rather than shown as a dash that never fills in.
 *
 * Read it off the main thread: these are small files, but they are files.
 */
final class Stats {

    /** One figure on the performance card. */
    static final class Item {
        final int labelRes;
        final String value;
        /** Kept when the card has to shrink to stay clear of the Home rows; the rest are dropped first. */
        final boolean essential;
        Item(int labelRes, String value) {
            this(labelRes, value, false);
        }
        Item(int labelRes, String value, boolean essential) {
            this.labelRes = labelRes;
            this.value = value;
            this.essential = essential;
        }
    }

    private final Context ctx;

    Stats(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    /** Every figure this TV will give, in card order. Empty before the second sample on a fresh start. */
    List<Item> read() {
        List<Item> items = new ArrayList<>();
        int cpu = cpuPercent();
        if (cpu >= 0) items.add(new Item(R.string.perf_cpu, cpu + "%", true));
        long[] mem = memoryMb();
        if (mem[0] >= 0) items.add(new Item(R.string.perf_memory, mem[0] + " MB"));
        if (mem[0] >= 0 && mem[1] > 0) {
            items.add(new Item(R.string.perf_memory_used, usedOfTotalMb(mem[1] - mem[0], mem[1]), true));
        }
        long freeBytes = Environment.getDataDirectory().getFreeSpace();
        long totalBytes = Environment.getDataDirectory().getTotalSpace();
        String storage = formatGb(freeBytes);
        if (!storage.isEmpty()) items.add(new Item(R.string.perf_storage, storage));
        if (totalBytes > 0 && freeBytes >= 0 && freeBytes <= totalBytes) {
            items.add(new Item(R.string.perf_storage_used, usedOfTotalBytes(totalBytes - freeBytes, totalBytes), true));
        }
        int temp = temperatureC();
        if (temp > 0) items.add(new Item(R.string.perf_temperature, temp + "°C"));
        int fps = frameRate;
        if (fps > 0) items.add(new Item(R.string.perf_frames, fps + " fps", true));
        String net = networkRate();
        if (net != null) items.add(new Item(R.string.perf_network, net));
        int signal = wifiSignalPercent();
        if (signal >= 0) items.add(new Item(R.string.perf_signal, signal + "%"));
        int link = wifiLinkMbps();
        if (link > 0) items.add(new Item(R.string.perf_link, link + " Mbps"));
        items.add(new Item(R.string.perf_uptime, uptime()));
        return items;
    }

    /** The screen counts its own drawn frames and leaves the figure here; 0 until it has a second to count. */
    volatile int frameRate;

    // ---- processor -------------------------------------------------------------------------------

    /** -2: this source cannot be read (Android 9 hides /proc/stat from apps on many TVs). */
    private static final int CPU_UNREADABLE = -2;
    private long cpuTotalPrev = -1, cpuIdlePrev = -1;
    private boolean procStatDead;
    private long idlePrev = -1, wallPrev = -1;

    /** Busy percent, or -1 until a second sample is in. */
    int cpuPercent() {
        if (!procStatDead) {
            int fromStat = cpuFromProcStat();
            if (fromStat != CPU_UNREADABLE) return fromStat;
            procStatDead = true;
            idlePrev = -1;
        }
        return cpuFromIdle();
    }

    private int cpuFromProcStat() {
        String s = readFirstLine("/proc/stat");
        if (s == null || !s.startsWith("cpu")) return CPU_UNREADABLE;
        String[] t = s.trim().split("\\s+");
        if (t.length < 5) return CPU_UNREADABLE;
        try {
            long user = Long.parseLong(t[1]), nice = Long.parseLong(t[2]), sys = Long.parseLong(t[3]);
            long idle = Long.parseLong(t[4]);
            long iowait = t.length > 5 ? Long.parseLong(t[5]) : 0;
            long irq = t.length > 6 ? Long.parseLong(t[6]) : 0;
            long soft = t.length > 7 ? Long.parseLong(t[7]) : 0;
            long total = user + nice + sys + idle + iowait + irq + soft;
            long idleAll = idle + iowait;
            if (cpuTotalPrev < 0) {
                cpuTotalPrev = total;
                cpuIdlePrev = idleAll;
                return -1;
            }
            long dt = total - cpuTotalPrev, di = idleAll - cpuIdlePrev;
            cpuTotalPrev = total;
            cpuIdlePrev = idleAll;
            if (dt <= 0) return -1;
            return clampPercent((int) Math.round((dt - di) * 100.0 / dt));
        } catch (NumberFormatException e) {
            return CPU_UNREADABLE;
        }
    }

    /** Busy percent from cpuidle time (microseconds) across the cores. -1 until the second sample. */
    private int cpuFromIdle() {
        long idle = 0;
        int cores = 0;
        for (int i = 0; i < 8; i++) {
            boolean any = false;
            for (int state = 0; state < 8; state++) {
                String t = readFirstLine("/sys/devices/system/cpu/cpu" + i + "/cpuidle/state" + state + "/time");
                if (t == null) break;
                try {
                    idle += Long.parseLong(t.trim());
                    any = true;
                } catch (NumberFormatException e) {
                    break;
                }
            }
            if (!any) break;
            cores++;
        }
        if (cores == 0) return -1;
        long wall = SystemClock.elapsedRealtime() * 1000L;
        if (idlePrev < 0) {
            idlePrev = idle;
            wallPrev = wall;
            return -1;
        }
        long di = idle - idlePrev;
        long dw = (wall - wallPrev) * cores;
        idlePrev = idle;
        wallPrev = wall;
        if (dw <= 0) return -1;
        return clampPercent((int) Math.round((dw - di) * 100.0 / dw));
    }

    private static int clampPercent(int v) {
        return Math.max(0, Math.min(100, v));
    }

    // ---- memory and storage ----------------------------------------------------------------------

    /** {available, total} in MB, either -1 when /proc/meminfo did not give it. One pass over the file. */
    long[] memoryMb() {
        long available = -1, total = -1;
        try (BufferedReader r = new BufferedReader(new FileReader("/proc/meminfo"))) {
            String line;
            while ((line = r.readLine()) != null && (available < 0 || total < 0)) {
                if (line.startsWith("MemAvailable:")) available = kbValue(line);
                else if (line.startsWith("MemTotal:")) total = kbValue(line);
            }
        } catch (IOException | NumberFormatException ignored) {
        }
        return new long[]{available, total};
    }

    /** The kilobyte figure on a /proc/meminfo line, in MB. -1 when the line is not shaped as expected. */
    private static long kbValue(String line) {
        String[] t = line.split("\\s+");
        if (t.length < 2) return -1;
        try {
            return Long.parseLong(t[1]) / 1024;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "1.7 / 2.3 GB" from megabytes, or "640 / 2300 MB" when the total is under a gigabyte. */
    private static String usedOfTotalMb(long usedMb, long totalMb) {
        if (totalMb >= 1024) {
            return String.format(Locale.US, "%.1f / %.1f GB", usedMb / 1024.0, totalMb / 1024.0);
        }
        return usedMb + " / " + totalMb + " MB";
    }

    private static String usedOfTotalBytes(long used, long total) {
        double gb = 1024.0 * 1024 * 1024;
        return String.format(Locale.US, "%.1f / %.1f GB", used / gb, total / gb);
    }

    static String formatGb(long bytes) {
        if (bytes <= 0) return "";
        double gb = bytes / (1024.0 * 1024 * 1024);
        return gb >= 10 ? String.format(Locale.US, "%.0f GB", gb) : String.format(Locale.US, "%.1f GB", gb);
    }

    // ---- temperature -----------------------------------------------------------------------------

    /** Which thermal zone answered last time, so the card does not walk all of them every second. */
    private int thermalZone = -1;
    private boolean thermalDead;

    /** Degrees Celsius, or -1 when this TV keeps its sensors to itself (many Sony sets do). */
    int temperatureC() {
        if (thermalDead) return -1;
        if (thermalZone >= 0) {
            int t = readZone(thermalZone);
            if (t > 0) return t;
        }
        for (int zone = 0; zone < 12; zone++) {
            int t = readZone(zone);
            if (t > 0) {
                thermalZone = zone;
                return t;
            }
        }
        thermalDead = true;
        return -1;
    }

    /** A zone reports millidegrees on most sets and whole degrees on a few; both come back as Celsius. */
    private int readZone(int zone) {
        String s = readFirstLine("/sys/class/thermal/thermal_zone" + zone + "/temp");
        if (s == null) return -1;
        try {
            long raw = Long.parseLong(s.trim());
            int c = raw > 1000 ? (int) (raw / 1000) : (int) raw;
            return c > 0 && c < 150 ? c : -1;   // a plausible reading, not a battery or a placeholder
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // ---- network ---------------------------------------------------------------------------------

    private long rxPrev = -1, txPrev, netAtPrev;

    /** Traffic since the last read, like "0.4 MB/s", or null until the second sample. */
    String networkRate() {
        long rx = TrafficStats.getTotalRxBytes(), tx = TrafficStats.getTotalTxBytes();
        long now = SystemClock.elapsedRealtime();
        if (rx == TrafficStats.UNSUPPORTED || tx == TrafficStats.UNSUPPORTED) return null;
        if (rxPrev < 0) {
            rxPrev = rx;
            txPrev = tx;
            netAtPrev = now;
            return null;
        }
        long dBytes = (rx - rxPrev) + (tx - txPrev), dt = now - netAtPrev;
        rxPrev = rx;
        txPrev = tx;
        netAtPrev = now;
        if (dt <= 0 || dBytes < 0) return null;
        double perSecond = dBytes * 1000.0 / dt;
        if (perSecond >= 1024 * 1024) return String.format(Locale.US, "%.1f MB/s", perSecond / (1024 * 1024));
        return String.format(Locale.US, "%.0f KB/s", perSecond / 1024);
    }

    /** Signal strength as a percentage, or -1 on a wired connection or when Wi-Fi says nothing. */
    int wifiSignalPercent() {
        WifiInfo info = wifiInfo();
        if (info == null) return -1;
        int rssi = info.getRssi();
        if (rssi == 0 || rssi < -127) return -1;
        return clampPercent(WifiManager.calculateSignalLevel(rssi, 101));
    }

    int wifiLinkMbps() {
        WifiInfo info = wifiInfo();
        int speed = info == null ? -1 : info.getLinkSpeed();
        return speed > 0 ? speed : -1;
    }

    private WifiInfo wifiInfo() {
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            return info == null || info.getNetworkId() == -1 ? null : info;
        } catch (RuntimeException e) {
            return null;   // SecurityException included: some sets guard Wi-Fi details
        }
    }

    // ---- uptime ----------------------------------------------------------------------------------

    /** How long since the TV last booted, like "3d 4h" or "2h 15m". */
    String uptime() {
        long minutes = SystemClock.elapsedRealtime() / 60000L;
        long days = minutes / (60 * 24), hours = minutes / 60 % 24, mins = minutes % 60;
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + mins + "m";
        return mins + "m";
    }

    // ---- shared ------------------------------------------------------------------------------------

    static String readFirstLine(String path) {
        try (BufferedReader r = new BufferedReader(new FileReader(path))) {
            return r.readLine();
        } catch (IOException e) {
            return null;
        } catch (RuntimeException e) {
            return null;   // a vendor sysfs node can throw rather than report a missing file
        }
    }
}
