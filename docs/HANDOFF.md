# Handoff — CleanYourTV, updated 6 November 2026

Where the work stands, what is still open, and where everything lives. Finished history (every build, fixed bugs,
closed investigations) is in [`HISTORY.md`](HISTORY.md); finished test checklists are in [`TESTING-history.md`](TESTING-history.md). Read the three sections in order:
**state** (what is on the TV now), **open work** (what to pick up), **keeping the TV healthy**, **map**
(where things are in the repo).

---

## 1. State

| | |
|---|---|
| TV | Sony Bravia KD-49X8500H (UR3), Android TV 9, ADB over the network at `192.168.1.19:5555` since 6 Nov (was `192.168.1.3`). **The address changes when the TV restarts — check it before every session** ([`TESTING.md`](TESTING.md#before-you-start)) |
| Before it (31 Oct – 7 Nov) | **2.36** (`versionCode` 236), installed 31 Oct by the owner's local session (`versionName=2.36` confirmed, logcat cleared). **6 Nov, first on-screen run:** § 2.34 items 1–3 pass, item 2's dimming is wrong, and two new faults were found (§ 2 *Known bugs*); the Performance-card row at rows = 3 is greyed, so that bug is closed. Everything else in 2.33–2.36 is still unseen — see § 2 *Waiting on a test* |
| Last builds with TV results | **2.32** (31 Oct): no flash at 0 Favorite rows, no window at 1–3; density 320 (= 2.0) confirmed; log clean. **2.33** installed earlier: log clean, no window at 1–3 rows; its 0-row behaviour was never reported. **2.31** (27 Oct): glow bars gone, Inputs-on-Home hidden, animations rename works — reported, not ticked. Older results: [`TESTING-history.md`](TESTING-history.md) |
| On the TV now (since 7 Nov) | **2.37** (`versionCode` 237), installed 7 Nov over 2.36 by the owner's local session. **[`TESTING.md`](TESTING.md) § 2.37 items 1–12 pass**, no crash, no new fault; not run: the focus bar in the Favorite rows and during Move (remote needed). Merged as PR #17 |
| `main` | Every build through **2.37** is merged (2.37 as PR #17, after its TV run). Before it, through 2.36 (PR #14, `c8d8df4`). Four builds — 2.33, 2.34, 2.35, 2.36 — went in **before any TV test**, at the owner's word; each is one revert PR away |
| Branch | `claude/zen-euler-0mir9r`, restarted from `main` after each merge; no unmerged code |
| Merged so far | PR #1 (0c5f3e3): the Apple TV layout and the debloat work. PR #2 (daad5b4): the Google TV layout at 2.04, this handoff and the repo map. PR #3 (78c356d): 2.05–2.12. PR #4 (bf0f01c): 2.13–2.14. PR #5 (83bd67f): 2.15–2.21. PR #6 (2ea447f): 2.22–2.27. **PR #7 (ddac328): 2.28. **PR #8 (93bdc1a): 2.29–2.31**. **PR #9 (0f232ed): 2.32 and the 2.31 handoff**. **PR #10 (2b21a8e): 2.33**. **PR #11 (9bf7b88): 2.34**. **PR #12 (65d9fd5): 2.35**. **PR #14 (c8d8df4): 2.36** |
| Network | **wired Ethernet** (`eth0`, 192.168.1.19 since 6 Nov; given by the router, and it changed on that restart). No Wi-Fi address at all, which is why the card's Wi-Fi rows stay blank |
| Signing key | debug keystore, SHA-256 `a215696a…436e`. The owner holds `cleanyourtv-debug.keystore` (alias `androiddebugkey`, password `android`). **Never commit it.** Every build must keep this key, or the TV refuses the update. |
| Screensaver | the TV's own (Google Backdrop). The launcher never changed it; its Clock screensaver stays off. |

### Version numbers
`A.BB` in [`launcher/app/build.gradle`](../launcher/app/build.gradle): a fix or minor change raises BB
(2.19 → 2.20), a major change raises A (→ 3.00). `versionCode` = A×100 + BB and must always rise.

### What each build added
One row per build, newest first, from 2.05 on: [`HISTORY.md`](HISTORY.md#what-each-build-after-the-merge-added). The latest is **2.36**.

### Build, test, audit
```bash
cd launcher
ANDROID_HOME=/root/android-sdk ./gradlew assembleRelease \
  -x lintVitalAnalyzeRelease -x lintVitalReportRelease -x lintVitalRelease    # lint needs Maven (blocked here)
cd .. && python3 -m unittest discover -s tests </dev/null                      # the </dev/null matters: it hangs without it
apksigner verify --print-certs launcher/app/build/outputs/apk/release/app-release.apk | grep SHA-256
aapt2 dump badging  launcher/app/build/outputs/apk/release/app-release.apk | grep -E '^package|uses-permission'
```
Check the APK exists before copying it anywhere: a failed build leaves the previous one in place, and an old APK
has been sent to the TV that way before. There is no emulator in the cloud container (no KVM), so **layout
changes are proved by the owner's photos**, not by running the app here.

### Checking the TV for crashes
After installing and testing a build, from `C:\Users\<user>\platform-tools`:
```powershell
.\adb.exe logcat -c                                              # clear, then use the TV with the remote
.\adb.exe logcat -d | findstr /i "FATAL.*gathin AndroidRuntime"  # empty = no crash
```
`findstr` treats the two space-separated terms as a plain OR (`.*` is only a wildcard with `/R`), so an empty
result means not even an `AndroidRuntime` line was printed: the app did not crash at all. **2.04 came back empty
after a full test round**, as did 2.03.

Two things in the output are normal, not faults: lines from `uid 2000` with `com.android.commands.input`
(your own `adb shell input` commands) and `BufferQueueProducer` / `SurfaceFlinger` lines when a screen closes.
Clear the log *before* testing, not after — checking straight after `logcat -c` always looks clean.

### House rules this project follows
- Risk levels: RSK-0 irreversible → ask first; RSK-1 costly to undo → do it and explain; RSK-2 → just do it.
- No guessing: state assumptions; never invent infrastructure or settings.
- Verify before saying done: build output, test output, APK audit.
- Say the blast radius, the assumptions and the rollback before a big change.
- Only what was asked. Surface anything else instead of quietly building it.
- **Standing layout rule:** before sending any APK, check both layouts for text or panels overlapping the top bar
  or the rows, at Rows below dock 0–3 and 5–8 apps per row. Ask the owner for 4 photos
  (Apple and Google TV × rows 0 and 3).
- **APK file name (owner's rule, 31 Oct):** every APK sent to the owner is named `tvhome-A.BB.apk` after the version, e.g. `tvhome-2.31.apk`, `tvhome-2.32.apk` — not `app-release.apk`.
- Every reply to the owner ends with a status block (🟢 READY / 🟡 INPUT NEEDED / 🔴 BLOCKED).

---

## 2. Open work

### Waiting on a test
The checklists are in **[`docs/TESTING.md`](TESTING.md)**; finished ones are in [`TESTING-history.md`](TESTING-history.md).

- **2.33–2.36 on screen** (2.36 is installed): work through [`TESTING.md`](TESTING.md) § 2.33, § 2.34, § 2.35 and § 2.36 in one pass. Then the local session runs its step 3 (log), and step 5 for screenshots of the five choice pages with previews (Focus style, Banner, Glow, Font, Wallpaper) — none of the drawings has ever been seen. **Still open after the 6–7 Nov run:** § 2.33 #4 (as written), § 2.34 #2 (dimming bug), #8 (skipped: needs every app in Your apps), § 2.35 #4 (the faint marks do not read as movement); three test pictures (`test-a-landscape.jpg`, `test-b-portrait.jpg`, `test-c-large.jpg`) were pushed to the theme's `wallpapers/` for § 2.36 and are **kept on the TV for later tests** (owner, 7 Nov); Wallpaper is back on Theme default and Home shows no picture; everything else in 2.33–2.36 is ticked in `TESTING.md`. **Done on 6 Nov:** § 2.33 items 1–3, 5–7, 9–11 pass; item 4 fails as written (the gear is reached only through the Apps and Inputs tabs, which hide the window). § 2.34 items 1–3 (results in `TESTING.md`). Item 4 stopped at 2 of 10 adds because of the new Down-after-add fault below. **The previews have now been seen** (screenshots over adb, nothing picked): all five pages draw, follow the highlight and keep the drawing fixed at the top (§ 2.34 #10 and #12, § 2.35 #1–3 and #6, § 2.36 #1, #5 and #7 ticked). Still open from them: the Live input drawing puts "App name" over the bottom of the window; the moving glow styles' faint marks do not read as movement (§ 2.35 #4: the owner was not sure they saw any, no flicker); no theme pictures to try the Wallpaper decoding (§ 2.36 #3, #4, #6); § 2.35 #7 passed later the same night with Banner switched on for the test and back to Off.
- **Long presses need the owner's remote on this set.** `input keyevent --longpress` arrives as a short press (it opened the app), and `sendevent` to `/dev/input/event0`–`event3` is denied: the shell user is not in the `input` group here, so the `CONTRIBUTING.md` workaround does not apply. Anything that needs hold-OK (removing from Your apps, § 2.34 items 7 and 9) is done with the remote.
- **2.31's checklist** — results reported 27 Oct; tick [`TESTING-history.md`](TESTING-history.md) § 2.31 once the owner says so.
- **The dock black flash** — still not reproduced, now also with **Animations off** (6 Nov, see *Known bugs*); capture `black.txt` and `dumpsys meminfo` the moment it is seen.
- **The intermittent "wrong app from the +"** — 2.34 replaced the list with a pick from the Apps tab; [`TESTING.md`](TESTING.md) § 2.34 item 4 is the check. 6–7 Nov: **10 of 10 adds correct** on 2.36 (AIS PLAY, Crunchyroll, FLIXER, GeForce NOW, MangoTV, Music, Spotify, Timers & Clock, Viki, VLC). With the 27 Oct run that is twenty clean adds in two sessions; the owner can decide whether that closes it.
- **The old-key theme check** (2.27) — skipped for lack of a theme file.

**Before installing 2.22 or later on any other set:** check `settings get secure screensaver_components`. If it names `com.gathin.tvhome/.ClockDream`, point the TV at its own screensaver first, or it falls back to the system default with no warning.

### The `Duplicate finish request` lines in the log (read 31 Oct; no change made)
Both the 2.32 and 2.33 logs carry `Duplicate finish request` for `MainActivity` (6–7 per run, never a crash). `MainActivity` is the HOME shim: `onCreate` starts `GtvActivity` and calls `finish()` once (`MainActivity.java:50`), and the same class serves `ACTION_LAUNCHER_SETTINGS` (the remote's Settings button, **not to be touched**). From the code alone there is a single `finish()` on that path, so the system's second request probably comes from the shim being launched again while still finishing; this is **not proven** and nothing in the symptoms is user-visible. Left alone on purpose. `BufferQueueProducer: disconnect: not connected` (3 per run) is graphics noise at surface teardown, also unverified.

### For the next fix batch (owner's decisions, 7 Nov) — built as 2.37, passed on the TV 7 Nov
**All eight items below are in 2.37** (see [`HISTORY.md`](HISTORY.md#what-each-build-after-the-merge-added) for how each was done); the TV check is [`TESTING.md`](TESTING.md) § 2.37.

**Focus styles Underline and Lift + bar: move the bar out of the tile.** Today both draw a 4 dp bar in the app's
own colour *inside* the tile along its bottom edge (`GtvActivity.java` ~2096–2100, colour set ~2130), so on tiles whose
banner ends in that same colour the bar disappears — iQIYI (green on green), Prime Video and bilibili (blue on blue),
HBO Max (dark on dark) — and Lift + bar reads as "Lift + underline". The owner chose:
- **Underline — option A:** the bar sits **just below** the tile, touching it, on Home's dark background, full tile
  width, in the app's colour; an app colour too dark to see against the background falls back to white.
- **Lift + bar:** the tile **rises**, the bar stays **below at full tile width**, with a **visible gap** between the
  raised tile and the bar.
- **The Focus style page's preview** is redrawn to match both.
The tile clips its children, so the bar has to be drawn outside it (by the row or a sibling view), not as a child of
the card. Other options weighed and not chosen: an automatic contrast colour inside the tile, an outlined bar, a short
centred bar.

**Also in this batch (owner, 7 Nov):**
- **Bug — after an add from the +, Down from the Home pill no longer enters Your apps** (*Known bugs* below). Fix,
  then re-run § 2.34 #3–4 with Home › Down between adds.
- **Bug — the + choosing mode dims by recycled tile, not by app** (*Known bugs* below, § 2.34 #2). Set or clear the
  dim on every bind.
- **F — Banner page preview, Live input row:** the drawn "App name" overlaps the bottom of the window; on Home (2.33)
  the name sits under the window. Move the label below the window in the drawing.
- **G — Glow page preview, moving styles** (Random position, Random ×2, Random ×3, Random position and shape, Slow
  drift): the faint marks showing where the glow can go are not visible on this TV (owner unsure, screenshots show
  none; § 2.35 #4). Make them clearly visible without flicker.
- **2.33 item 4 — checklist only, no code (owner chose option A).** From the Home pill the gear is reached only through
  the Apps and Inputs tabs, which switch the page on focus and so hide the live window; Up from the end of Your apps
  goes to the Home pill, not the gear. Accept that: reword 2.33's "What changed" (it says the window should stay with
  the focus on the gear) and item 4 to "the window goes when the focus reaches the Apps tab on the way to the gear".
  Options not chosen: Up from the + to the gear, tabs switching on OK, the gear before the tabs, a dwell delay.

After the batch: a new build for the owner to install, then § 2.34 #2–4, the Focus style items (§ 2.34 #10–13) and
§ 2.35 #4 again on the TV.

### Known bugs
Only the open ones; fixed and closed entries are kept word for word in [`HISTORY.md`](HISTORY.md#known-bugs-fixed-or-closed).

**Fixed in 2.37 and confirmed on the TV (7 Nov, two adds, Home › Down each time): after an add from the +, Down from the Home pill no longer enters Your apps (found 6 Nov on 2.36).** *Cause found in the code:* `focusHomeFirst()` focused position 0 only if it had a view holder; after an add the row is scrolled to its end and, with more apps than fit, position 0 has none, so Down did nothing (and removing the app made the row fit again). 2.37 scrolls back and focuses it after the next layout pass. *Original note:*
Add an app through the + (Home comes back with the new app as the last tile, focused, toast shown), press Home, then
Down: the focus stays on the Home pill, every time. Back and switching to the Apps tab and back do not help.
Reproduced twice, the second time **with the owner's remote**, so it is not an adb artefact. Removing the added app
(hold OK › Remove) makes Down work again. Without an add it does not happen: from the end of the row, Up › Down and
Home › Down both return to the row (the row scrolls back to its start), with adb and with the remote. No crash, no
`FATAL` in the log. It fits the suspect named under the next entry — `setInDock` → `notifyDataSetChanged()` and a
posted `focusDock` that can run before the layout pass — leaving the row's remembered focus pointing at a holder
that is not there; **unproven**.

**Fixed in 2.37 and confirmed on the TV (7 Nov, two choosing sessions): in the + choosing mode, the dimming follows the tile, not the app (found 6 Nov on 2.36).** *Cause found in the code:* the bind computed the dim before `card.setTag(a)`, so `pickDim` read the tile's previous app. 2.37 sets the tag first. *Original note:* On first
opening no tile is dimmed (Netflix, in Your apps, measured as bright as Google Play, which is not). After the focus
has moved along the first row, only bilibili and Disney+ are dimmed; HBO Max, iQIYI, Netflix, Prime Video, WeTV,
YOUKU and YouTube are not. In the next choosing session **Apple TV and BRAVIA notifications were dimmed although
they are not in Your apps**, and Disney+ was not. So the dimmed state stays with a recycled view holder instead of
being set on every bind. The toast is right either way ("Disney+ is already in Your apps"), so only the look is
wrong. Not proven to be related to the wrong-app fault below, though both point at holder state.

**Open: sometimes the app picked from the + is not the one that appears (2.23, cause unknown).** **Ruled out by the
code:** a package with several launcher activities entering twice — `loadApps` keeps one app per package
(`seen.add(pkg)`, `GtvActivity.java:397`), so the `com.sony.dtv.timers` pair in the log is not a candidate — and the
menu's loop variable, which is a fresh effectively-final per iteration, with `showMenu` pairing `titles.get(i)` to
`actions.get(i)`. **Leading suspect, shared with the entry above:** `setInDock` calls `notifyDataSetChanged()` and
then posts `focusDock`; a posted runnable can run *before* the layout pass, so the holder lookup returns null and
the focus is left to the platform — a race, which fits "บางครั้ง". **Unproven; it cannot be run here.** If it
survives 2.30, ask the owner whether the toast names the app they pressed, whether that app is in the row, and
which app and which place in the list it happens with. **Ten adds with different apps in the 27 Oct run produced no wrong app** — 2.30's focus fix may have held, but an intermittent fault is not closed by one clean run, so this stays open.

**Open: Your apps flashes black at the ends of the row (2.27, not reproduced).** Holding Left or Right to run to
the end of Your apps makes the screen go black for an instant — the owner's word was "บางครั้ง", so it is
intermittent. It cannot predate 2.23: a row you can run to the end of only exists since the dock became
scrollable, and the set ran 2.22 before this.

*Tried on 26 Oct, and it did not reproduce:* 15 hold-sweeps left-and-right at **Glow style = Classic**, then 15
more at **Random position**. No black screen either time, and `Choreographer … Skipped` appeared **zero** times
in the whole buffer across both rounds.

*What that rules out, and what it does not:* both rounds ran with **Animations on**, so only the glow was varied.
The leading hypothesis is therefore **untested** — `CentreManager.onRequestChildFocus`
(`GtvActivity.java:1574-1578`) asks for a `smoothScrollBy` the row cannot perform: at either end the focused tile
can never reach the centre, so `to` is never 0 and a scroll is requested on *every* focus event while key repeat
fires them continuously. Testing it means turning **Animations off** and sweeping again. Treat the zero with
suspicion too: no `Skipped` line from *any* app in the whole device is itself odd, so a positive control is
needed before that measurement is trusted again.

*Tried on 6 Nov with Animations off, and it did not reproduce:* 15 sweeps from the first tile past the + and back,
each run one `input keyevent` call with 14 presses (as fast as a held key), screen recorded. `dumpsys gfxinfo
com.gathin.tvhome`, reset just before, counted **281 frames, 0 janky**, 50th/99th percentile 5/9 ms, no missed
Vsync and no slow UI thread — that counter is the positive control: it sees frames, and none were slow.
`Choreographer … Skipped` was again zero device-wide; no `FATAL`. The video (`sweep-anim-off.mp4`, owner's local
copy) could not be checked frame by frame here. So the `CentreManager` hypothesis did not show as dropped frames
with Animations off; it is not ruled out for a held key on the real remote, and memory was healthy (about 840 MB
available), which leaves the next paragraph's explanation standing.

*Most likely on today's evidence:* it needs memory pressure. The set had just been cleared and restarted, and
`dumpsys meminfo` straight after the test read `status normal`, 486 MB free, 117 MB cached — unusually healthy
for this TV, which §3 records reaching `status critical` with 0 KB cached after heavy streaming use. If that is
it, the flash returns after days of use, not on a fresh install.

*Not the same fault as 2.28's crash*, on the evidence there is: a full buffer captured over the 30 sweeps holds
no `FATAL` and no `ClassCast` line. Weak evidence, since no flash was seen during that capture either — so the
first thing to check when it next happens is still whether a crash is in the log.

*To catch it next time*, run these the moment it is seen, while the buffer is still warm:
```
.\adb.exe logcat -d > black.txt
.\adb.exe shell dumpsys meminfo | findstr /i "Total RAM Free RAM status"
```

Fixed in 2.21: the idle clock did nothing between 2.19 and 2.20 (the shim dropped `MainActivity`'s
`onUserInteraction` timer; `GtvActivity` arms it now).

Two crashes in this TV's logcat are **other apps'**, seen while testing 2.19 and recorded so they are not chased again:
- `com.tencent.qqlivei18n:push` (WeTV) throws `IllegalStateException: WorkManager is not initialized properly` from its recommendation-channel code roughly **every 30 minutes** (18:10, 19:07, 20:07, 20:37, 21:07, 21:37, 22:21 on 25 Oct). Their bug; it wakes a process and burns a little CPU each time. `pm disable-user` on that push component would stop it if it ever matters.
- `com.google.android.permissioncontroller` NPEs in `SettingsFragment.onActivityCreated` (a null ActionBar) when the TV's default-app settings screen opens. Sony/Google's, not ours.
- `com.android.systemui` throws `IllegalStateException: Task already scheduled or cancelled` from `com.sony.dtv.systemui.infoboard.InfoBoard.startUpdateClockTimer`, by way of `onConfigurationChanged` → `startClock`. Sony's info-board clock scheduling its timer twice on a configuration change. Seen 26 Oct 00:17.
- `com.sony.dtv.configsettings` throws `IllegalArgumentException: Service not registered` from `DisplaySoundActivity.onClear`, unbinding a service it never bound, when the Display & Sound screen closes. Sony's. Seen 26 Oct 00:21.

**Lines in our own log that look alarming and are not**, so they are not chased: `Duplicate finish request for
ActivityRecord … MainActivity` (the HOME shim finishing itself as it hands over to `GtvActivity`, seen every time Home
starts), `Toast already killed` (a toast replaced before it expired), and `BufferQueueProducer … disconnect: not
connected` when an activity's surface is torn down.
 The bottom-row menu was fixed in 2.08 (`GtvActivity.showPanel()` now measures the panel and opens it
above the tile when it will not fit below, the same rule `MainActivity.onTileLongPressed()` uses).

### The APK size budget
Resolved in 2.17; the full account (what was trimmed, and what would save ~600 KB more) is in [`HISTORY.md`](HISTORY.md#the-apk-size-budget--resolved-in-217). Latest: 2.36 is 466,027 bytes against the 921,600-byte gate.

### Diagnosed: a streaming app lags (not a launcher bug)
The diagnosis and the fixes the owner can apply are in [`HISTORY.md`](HISTORY.md#diagnosed-a-streaming-app-lags-not-a-launcher-bug).

### The performance card: four figures this TV will not give
The card (Launcher settings → Performance card, added in 2.05) shows memory, storage, frames, network and uptime
on the KD-49X8500H. Four of its figures stay blank here, each for a known reason:

| Figure | Why | Possible follow-up |
|---|---|---|
| **Processor** | Android 9 blocks apps from `/proc/stat`, and this set also refuses the `cpuidle` fallback, so both sources in `Stats.cpuPercent()` are dead. The Apple layout's stats line has the same gap | **Done in 2.08:** `Stats.loadAverage()` reads `/proc/loadavg` instead and the card shows **Load (1 min)**. A load figure, not a percentage: 1.00 means about one core's worth of work. If that file is closed too, the row stays out |
| **Temperature** | The thermal sensors are not readable by apps on this set (`Stats.temperatureC()` probes zones 0–11, then gives up). No public API on Android 9 replaces it | None |
| **Wi‑Fi signal**, **Link speed** | **Confirmed: the TV is wired.** `ip -o addr` shows an address on `eth0` and nothing on `wlan0`, so there is no Wi-Fi to report and leaving the rows out is correct, not a fault | A **Network type** row (Wi‑Fi / Ethernet / offline) was offered and declined as clutter: it would never change on a wired set. The detection already exists in `MainActivity` (`NetworkCapabilities.TRANSPORT_ETHERNET`, which drives the top-bar pill and its `ic_ethernet` icon) |

Nothing is built for these: the owner has not asked for them, and two of the three have no fix.

### Done: the Apple TV layout is out of the APK
The sources stay in the tree; how it was done and the size figures are in [`HISTORY.md`](HISTORY.md#done-the-apple-tv-layout-is-out-of-the-apk).

### Next phases (from the proposal, [`docs/proposals/google-tv-ui/README.md`](proposals/google-tv-ui/README.md))
1. ~~**Settings redesign**~~ — **done** (2.13–2.14 the panel, 2.21 the choice pages and the banner title size,
   2.24 the banner styles). Glow, Animations and Advanced effects came back in 2.26 (Animations became *Focus & indicator animations* in 2.31). **Cinematic** and **App
   names** are still hidden behind `APPLE_ROWS_READY` in `LauncherSettingsActivity`: they described the Apple
   layout's shelf and its always-on tile names, and a later phase has to decide what — if anything — they mean
   here.
2. **Phase 2 layout**: ~~Inputs row on Home~~ (2.24 — **hidden again in 2.31**, kept behind
   `INPUTS_ROW_READY`), ~~Live-input banner~~ (2.25 — **shows black on this TV, open**), ~~glass bars~~ (2.24) — what
   is left is the **side rail**, **Posters** and **Minimal** layout styles, and the owner's real want behind the Inputs
   row: **a live window for the inputs, as the Apple layout had** — one window with a chooser is buildable, one per
   input at once most likely is not (see Decisions), and it waits on Live input showing a picture at all.
3. **Effects** (§4): part 1 is in 2.26 (glow styles, Ribbon, animations, focus styles). Left from §6.7: **glass
   tiles**, **tile light** (reflection), **title glow** and **colour wash**; and from §4.3 the focus extras
   (shine sweep, 3D tilt, breathing), the transitions and the background motion.
4. **Wallpapers** (§5): new sets, the 4K quality fix, online wallpapers (opt-in). **Check the licences first**
   for the sources listed in §8 Q7 (Wikimedia, Earth View, NASA, NatGeo, Apple) before any of them ship.
5. **Hebrew and polish**: right-to-left for the new screens, a Hebrew strings pass, final tidy-up.
6. **Merging**: done per set of builds — PR #1 (the Apple layout and the debloat), PR #2 (2.04), PR #3 (2.05–2.12),
   PR #4 (2.13–2.14), PR #5 (2.15–2.21), PR #6 (2.22–2.27), PR #7 (2.28), PR #8 (2.29–2.31). The owner is the only person with
   access, so nobody else reviews; merge once CI is green. **A merge does not mean tested** — PR #6 went in with 2.22–2.27 still
   unseen on the TV, and PR #8 with 2.31 not yet installed, both at the owner's instruction, so the test backlog
   outlives the branch.

### Decisions already made (do not re-ask)
- **Ask before changing anything, every time** (the owner's rule, 26 Oct). Any instruction to modify something, in
  whole or in part, is answered with a proposal and a stop — what would change, in which files, the blast radius,
  how to undo it — and then waits for the owner's word. This holds however small or obvious the change looks, and
  it covers documentation as much as code. Reading, searching, `git log`, running the tests and building to verify
  change nothing and need no asking.
- **Decided 27 Oct, for 2.31:** the **Inputs-on-Home row is hidden** — out of the APK, kept in the repo behind one
  flag, like `EFFECTS_READY`; its code, its row-end rule and its tests stay and the stored value is untouched. The
  panel's **"Animations" becomes "Focus & indicator animations"** (the stored key `animations` is unchanged). The
  Glow bars are fixed so the glow view only ever grows.
- **What the owner actually wants behind "Inputs on Home":** a **mini live window per input, as the Apple TV layout had**
  (`MainActivity`, commit `6aa614e`). Two things to know before it is mistaken for a small ask. The original was **one
  window plus an OK menu** (open full screen / switch port), not one per input. And a passthrough input uses the TV's
  single video path, so **several inputs cannot play at once**; one live window with a chooser is buildable, a row of
  live windows most likely is not. It also waits on Live input showing a picture here at all.
- **Found while building 2.30, for the owner to decide:** the rows setting's label string exists in **eight** locales,
  not two. Only English and Hebrew were renamed; **Italian, Dutch, Spanish, Uzbek, Indonesian, Norwegian and
  Portuguese** keep their old wording ("rows below the dock"), which still describes the position correctly. They
  are left for someone who can check a translation. Also: **Hide Remove at `cols`** means a freshly seeded row (a
  few apps at Apps per row = 6) has **no Remove at all** until it has grown past six — intended, but worth knowing.
- **Decided 26 Oct, for 2.30** (so they are not re-asked): after Remove, the focus goes to **the row's last app**.
  The Remove entry is **hidden when Your apps holds Apps per row apps or fewer** — the minimum is Apps per row —
  and `setInDock`'s `dock.size() <= 1` guard stays as the last line of defence. The setting **"Rows below dock" is
  renamed "Favorite apps rows"** — the visible text only, the stored key `rows_below_dock` is unchanged so no saved
  value is lost. At **Favorite apps rows = 3** the Performance card is **forced off**: its row is greyed and reads
  Off with a hint saying why, the stored value is not overwritten, and the card returns when the rows are lowered.
  Left/Right **stops at the ends of the Favorite rows, the Apps tab and the Inputs rows** (the Apps tab changes
  from wrapping to stopping). All of this ships as **one build, 2.30**.
- **Where the live input window is, and where it is not** (asked and answered 26 Oct, so it is not re-asked). The
  mini window that showed a picture per input, where OK offered *open full screen* or *switch to another port*,
  belonged to **`MainActivity` — the Apple TV layout's Home**, under its top-bar pills; it arrived in `6aa614e`
  and was switched on from Launcher settings › Input preview. It **left the APK in 2.19** with that layout,
  because the window itself lived in `activity_main.xml`. The **Inputs tab of the Google TV layout has never had
  a preview**: `fillInputs` builds a `FrameLayout` and a `TextView`, never a `TvView`. The only live window in
  the code today is `buildLiveWindow()` (`GtvActivity.java:1299-1310`), which 2.25 added for the **banner**. So
  three things from the old behaviour are still absent — it is on the banner rather than the Inputs tab, it shows
  **one** chosen port rather than a picture per input, and there is **no OK menu**. The owner is trying
  Banner › Live input as it stands before deciding whether any of that comes back.
- Google TV layout is the default (`default_layout` in `config.xml`).
- Your apps **was** the same dock as the Apple TV layout, kept in `order.txt`. Since 2.23 it is the owner's own list in `gtv_dock.txt` (`DockApps`), seeded from `dock_apps`; `order.txt` still decides the sort order of the apps, not which ones are in the row. **Move no longer crosses between Your apps and the Favorite rows** — it reorders within each — because the row's membership is now changed on purpose with + and hold-OK, and a move across the boundary would change it silently.
- Favorite apps are the owner's own list (`gtv_favorites.txt`), **Google TV layout only**; the Apple layout keeps
  showing all apps under the dock.
- Favorite apps rows (called *Rows below dock* until 2.30) sets the number of Favorite rows; Home sits on the bottom edge; ⌄⌄ marks the lowest row and
  Down from it opens the Apps tab.
- Both Google TV popups use the tab style; the Apple layout keeps its own.
- The performance card keeps **both** the free and the used rows for memory and storage, and shrinks to the
  essential figures before hiding itself (2.07). No Network type row.
- Pressing Home leaves the selection on the **Home tab**, not on the first app, and the banner stays hidden until
  an app is selected.
- **A selected tile is told apart by the zoom and lift alone — no frame, no dimming.** Three styles were tried on
  the TV before this one settled: a white ring (2.09, 2.11), which says nothing on white artwork such as Netflix or
  the Play Store, and dimming every other tile (2.10), which the owner turned down. Dimming survives only while a
  tile is being carried. Please do not "improve" any of it back.
- Continuous effects stay off by default; **the screensaver stays the TV's own** — the launcher's clock screen was taken out of the APK in 2.22 (files kept). Do not re-declare `ClockDream` or start `ClockActivity` from Home.

---

## 3. Keeping the TV healthy

The TV is a 2.36 GB set running heavy streaming apps, so memory and storage fill up again over weeks of use. When
it starts feeling slow, run these from `platform-tools`. Both are safe to repeat, and **neither affects an app's
ability to update** — that was the owner's condition.

```powershell
.\adb.exe shell am force-stop com.iqiyi.i18n.tv        # the biggest single consumer: 308-425 MB
.\adb.exe shell pm trim-caches 2G                       # freed about 700 MB the first time
```

Check whether it helped — look for `status normal` and a non-zero cached figure:
```powershell
.\adb.exe shell "dumpsys meminfo | grep -E 'Total RAM|Free RAM|Used RAM'"
.\adb.exe shell dumpsys diskstats | head -5
```

Healthy numbers as of October 2025, after the clean-up: **status normal, 741 MB free, 309 MB cached, 27% of
`/data` free.** Before it: critical, 301 MB free, **0 KB cached**, 16% free.

Notes for whoever picks this up:
- **Zero cached is the red flag**, not low free memory. With nothing cached, every app launch is a cold start,
  which is what made Youku show a black screen on first open (and only the first — the second was warm and fine).
- Apps still hold about **1 GB of cache** that `trim-caches` will not take. The TV's own **Settings → Apps** lists
  apps by size and clears each one's cache; that is more reliable than anything queryable over ADB, because
  `adb shell` cannot read inside `/data/data`.
- `com.google.android.tvrecommendations` is **disabled** (it only fed rows on Sony's stock home). Reverse with
  `pm enable com.google.android.tvrecommendations`.
- Sony's `com.sony.dtv.tvx` holds ~64 MB and was left alone: on some Sony sets it is tied to Live TV or the
  inputs, so check what depends on it before touching it.
- **Never** disable a streaming app, the Play Store or Play services to save memory. That breaks updates.
- Clearing an app's **cache** is safe; clearing its **data** logs you out.

**2 November 2026: Youku black screen and lag again — this time the Sony DLNA renderer, not memory.** After more
than a day in standby (standby does not restart Android), Youku showed a black screen and series played with lag. A
**full restart** (power off and on, not standby) fixed it; the owner confirmed. What the numbers say, from the owner's
local-session report:
- In the two minutes the fault was seen, `com.sony.huey.dlna.module:dmr` and its parent used about **210% of the
  400% CPU** (four cores), mostly in the kernel; `dmr` had run up **5 h 36 min** of CPU since 04:04 that night. After
  the restart they used 12 s in 56 minutes. What woke them at 04:04 is unknown.
- **Memory was not the trigger this time:** before the restart MemAvailable was 848 MB and Cached 896 MB (not the
  zero-cached red flag above); after it MemAvailable was lower (736 MB, then 495 MB) and low-memory kills came faster.
  Swap (100 MB) is full within a minute of every boot — that is structural, not a sign of trouble on its own.
- **Load average is no guide on this set.** It sits at 30–35 all the time because about 30 Sony/MediaTek driver
  threads wait in the uninterruptible (D) state, which Linux counts as load. Use `top`'s idle figure or
  `dumpsys cpuinfo` instead.
- The launcher used 0% CPU throughout. A `com.android.systemui` crash at boot (`InfoBoard.startUpdateClockTimer`,
  `Task already scheduled or cancelled`) is Sony's own and was restarted by the system.

This is an inference from which numbers changed, not a proof. To check it next time the TV feels slow after standby:
```powershell
.\adb.exe shell ps -A -o PID,STIME,TIME,NAME | Select-String dlna   # TIME in hours for dmr = this fault again
.\adb.exe shell netstat -tn                                          # while it is busy: which device on the LAN is talking to it
.\adb.exe shell dumpsys cpuinfo | Select-Object -First 15
```
**6 November 2026: the renderer looped again, and turning it off fixed it — a restart alone does not.** The set had
been restarted on 4 Nov about 19:05. On 6 Nov at 00:02 the two DLNA processes were using **184% and 92%** of the 400%
(CPU idle only 60–66%, almost all of it kernel time) with Home on screen and nobody watching. Inside `dmr`, two
threads (`Thread-13`, `Thread-16`) each spun at about 90% of a core — a busy loop; the other 25 threads were idle.
From their CPU time the loop began about 16:30–16:45 on 5 Nov, roughly 21 hours after boot (an estimate).
`netstat -tun` showed **no DLNA client at all**: the LAN connections to the TV were Google Cast (ports 8008/8009)
and adb. So nothing was casting; the renderer loops on its own.

The setting is in Sony's Home network screen, `com.sony.dtv.homenetwork/.HomeNetworkSettingsActivity` (the
`renderersettings` package has no screen of its own). It can be opened with
`.\adb.exe shell am start -n com.sony.dtv.homenetwork/.HomeNetworkSettingsActivity`; the owner then turned
**Renderer** off with the remote. Within seconds the two processes' CPU time stopped rising and CPU idle went back to
359–362%, **without a restart**. Two minutes later, when iQIYI needed memory, the low-memory killer removed both
processes (`lowmemorykiller: Kill 'com.sony.huey.dlna.module:dmr'`), and they did not come back. iQIYI and then Youku
played with no black screen and no lag, 250–330% CPU idle, no crash. Google Cast is a separate service on 8008/8009
and is not affected by the Renderer switch.

Checked again at 22:48 the same day, 21 h after a restart (the point where the loop began last time): `dmr` not
running, CPU idle 321%.

**7 Nov 19:05, past 24 h (up 1 day 17 h): the Renderer stays off, but the parent module looped on its own.**
`dmr` was not running. The parent `com.sony.huey.dlna.module` (started 6 Nov 23:31) had run up **8 h 48 min of CPU**,
yet it did not move over a 30 s sample and its 26 live threads totalled 50 s — so a thread spun for hours and then
ended (`ps -T -o TID,TIME,CMD -p <pid>` shows this: process time far above the live threads' sum). When it ran is
unknown; the log no longer reaches back. Its remaining log lines show the DLNA **ControlPoint** (the part that browses
home media servers, not the Renderer) bound to **`wlan0`** with no IP (`IFUP: 0`, `LINKUP: 0`) on this Ethernet-only
set — a plausible trigger for the busy loop, not proven. CPU idle was 327–346% when measured. **Owner's choice:
monitor only.** If lag returns, check `dlna.module` first; the next steps would be any media-server switch in Sony's
Home network screen, or disabling `com.sony.huey.dlna.module` in Settings › Apps (reversible; Sony's Media Player
would lose home-server browsing, Google Cast is unaffected). Also still open: whether the TV's own software update
fixes either loop.

**6 Nov 23:31: one more Youku black screen, not the renderer.** During § 2.33 item 7 (Banner = Live input), Youku
opened from Home and stayed black while loading. It was a cold start (process started 23:31:35), Youku logged its
launch as done at 23:31:40 and then sat idle; CPU idle 343%, memory status normal (825 MB available), `dmr` not
running, and the launcher's live window had been released with no TV-input session left (`dumpsys tv_input`:
`mainSessionToken: null`). Opening Youku again — with the live window still on — worked. This matches the older note
above (black on a cold first open, fine the second time); the live window is not implicated on this evidence.

## 4. Map of the repository

A short version. The full one, with every launcher class grouped by job and every test explained, is
[`MOC.md`](MOC.md).

```
README.md                  What the project is, quick start, the launcher's own section (settings, keys, versioning)
PROMPT.md                  The brief this project was built from
CONTRIBUTING.md            How changes are made
SECURITY.md                How to report a problem
SHOWCASE.md                Screenshots of the result

launcher/                  The Android TV launcher (com.gathin.tvhome), plain Java, no network, no services
  app/build.gradle           versionCode / versionName (A.BB), minSdk 26, signing
  app/src/main/
    java/com/gathin/tvhome/  See the file guide below
    res/values/config.xml    Dock order, hidden apps, columns, rows, feature switches — read this first
    res/values/strings.xml   English strings; every one needs a Hebrew twin in res/values-iw/ (a test enforces it)
    res/layout/              Apple TV layout XML (the Google TV screen is built in code)
  build-and-install.sh/.ps1  Build and push to the TV

tools/                     Python and shell for the TV itself
  tv.py                      ADB helper: probe the TV, run commands, push files
  disable.*/restore.*        The debloat and its undo
  theme.*                    Push or reset a theme
  check.py, recovery.py      Checks and the recovery record

themes/                    Five themes (config.json + wallpaper); themes/README.md lists every key
examples/                  Per-device debloat records (one folder per TV model, hundreds of them)
docs/                      This handoff, troubleshooting, the remote-key guide, module and card specs
  MOC.md                     Map of Content: every folder and file, and where to start for a given task
  images/                    Every screenshot and rendered image the documents use
  proposals/google-tv-ui/    The full UI proposal: layout, settings, effects, wallpapers, ideas, owner answers
tests/                     Python tests; CI runs them (.github/workflows/ci.yml). No Android tests run in CI.
```

### Launcher files that matter most
| File | What it does |
|---|---|
| `MainActivity.java` | The **HOME component**, and since 2.19 nothing else: it opens `GtvActivity` and finishes. It also holds the **recovery screen** shown when Home cannot be built. It was the Apple TV layout until 2.19. |
| `GtvActivity.java` | The **Google TV layout**: tabs, banner, the Home grid (Your apps + Favorites), Apps, Inputs, menus, Move. |
| `Config.java` | Reads `config.xml` plus theme JSON plus the owner's on-TV choices into one object. |
| `LauncherSettingsActivity.java` | On-TV Launcher settings (the screen the redesign replaces). |
| `Order.java` | The dock order (`order.txt`), shared with the removed Apple layout and so still a file of its own. |
| `Favorites.java` | The Google TV Favorite apps list (`gtv_favorites.txt`). |
| `LibraryApps.java` | Home shortcut choices from the app library; `Favorites.java` follows its file pattern. |
| `InputPreview.java` | HDMI ports and the input preview. |
| `Fonts.java`, `GlowStyle.java` | The font and glow choices. |
| `ClockDream.java` | The launcher's own Clock screensaver (not selected on this TV). |

### Finding things quickly
- A string on screen → `grep -rn "the text" launcher/app/src/main/res/values/strings.xml`, then grep its name in `java/`.
- A setting → `config.xml` first (every key is commented), then `Config.java`, then where the field is read.
- Why Home looks the way it does → `GtvActivity.build()`. (`res/layout/activity_main.xml` is the Apple layout: kept in the tree, not in the APK, and nothing can reach it.)
- What the owner already decided → `docs/proposals/google-tv-ui/README.md` §6.7 and §8.

---

## 5. Working with the owner

They are a lead mechanical engineer and the project manager: technical, short on time, and they test every build on
the real TV. What works best:
- One APK per round, named `tvhome-<version>.apk`, sent in the chat.
- A short numbered list of what to test, in the order they would press the buttons.
- Their feedback comes as quoted replies under each test item. "Good" means that item is done.
- They often add new items at the end of a round; treat those as the next round, not this one.
- When they say "just note for now", write it down (here, or in the proposal) and do not build it.
