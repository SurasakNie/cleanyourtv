# Handoff — CleanYourTV, 25 October 2025

Where the work stands, what is still open, and where everything lives. Read the three sections in order:
**state** (what is on the TV now), **open work** (what to pick up), **keeping the TV healthy**, **map**
(where things are in the repo).

---

## 1. State

| | |
|---|---|
| TV | Sony Bravia KD-49X8500H (UR3), Android TV 9, ADB over the network at `192.168.1.3:5555` |
| Latest build | **2.29** (`versionCode` 229), built and verified here and installed on the TV; **2.30** is planned and not started |
| Last build installed on the TV | **2.29**, tested 26 Oct. **Confirmed at 2.29:** in Your apps, Right stops on the + and stays in the row, Left stops on the first tile, Down still reaches the Favorite rows, Move still works. **Fails at 2.29:** holding Left/Right in the **Favorite rows** jumps to the neighbouring row — see Known bugs. **2.23 is tested** (passes, with two defects, below); **2.24 to 2.27 are not**. Before that **2.28**, installed 26 Oct. **Confirmed at 2.28:** with Focus style = Glass halo, Down on the Inputs tab and Down from the gear onto the inputs row no longer crash — the case that failed every time before; the halo is the app's own colour on Your apps and the Favorite rows and white on an input tile. **Not yet confirmed:** Inputs on Home with Glass halo, the second place the same fault could bite, so 2.28 is not fully tested. Before that, **2.27** was installed 26 Oct 18:21 as a **fresh start**: `install -r` over 2.22, then `pm clear`, so `gtv_dock.txt`, `gtv_favorites.txt`, `order.txt`, `config.json` and every setting were wiped and reseeded. Installing *over the top* rather than uninstalling kept the TV's default-home assignment — the packaged manifest is the HOME app at `versionCode` 227, confirmed in the package cache. Nothing from `com.gathin.tvhome` in logcat, and **Home draws** — the owner operated the dock on it, so `GtvActivity` built, the row has tiles and the D-pad works; 2.19's recovery screen was never seen. **2.24 to 2.27's features are still untested** — see [`docs/TESTING.md`](TESTING.md). (The set had been running **2.22**, not 2.21 as this table said until now; the owner's 26 Oct log shows `versionCode=222` installed before the update.) **Confirmed on the TV at 2.28:** 2.18's page-indicator fix (hold Left/Right across Your apps — one pill, every other dot small, no half-open dash after a hold that ends mid-animation; confirmed months after it shipped) and 2.22's removal (top-right clock ticks, the TV's own screensaver starts, no Clock row in the panel). **Confirmed on the TV at 2.21:** the choice pages (Wallpaper and Font open in the panel, Back returns to the row), all three banner title sizes, the outline and fill switches, and the five rows gone from the panel. Plus 2.19's shim and recovery screen with both buttons. Still unconfirmed: 2.18's page-indicator fix. (2.21's idle clock was never reported on, and 2.22 removed it, so there is nothing left to confirm there) |
| Branch | `claude/zen-euler-0mir9r`. Merged through 2.28 (PR #7); **2.29 is unmerged** |
| Merged so far | PR #1 (0c5f3e3): the Apple TV layout and the debloat work. PR #2 (daad5b4): the Google TV layout at 2.04, this handoff and the repo map. PR #3 (78c356d): 2.05–2.12. PR #4 (bf0f01c): 2.13–2.14. PR #5 (83bd67f): 2.15–2.21. PR #6 (2ea447f): 2.22–2.27. **PR #7 (ddac328): 2.28** |
| Network | **wired Ethernet** (`eth0`, 192.168.1.3). No Wi-Fi address at all, which is why the card's Wi-Fi rows stay blank |
| Signing key | debug keystore, SHA-256 `a215696a…436e`. The owner holds `cleanyourtv-debug.keystore` (alias `androiddebugkey`, password `android`). **Never commit it.** Every build must keep this key, or the TV refuses the update. |
| Screensaver | the TV's own (Google Backdrop). The launcher never changed it; its Clock screensaver stays off. |

### Version numbers
`A.BB` in [`launcher/app/build.gradle`](../launcher/app/build.gradle): a fix or minor change raises BB
(2.19 → 2.20), a major change raises A (→ 3.00). `versionCode` = A×100 + BB and must always rise.

### What each build after the merge added
Newest first; PR #2 merged at 2.04.

| | |
|---|---|
| **2.29** | **Your apps keeps the focus at its right edge.** Holding Right to the end of the row dropped the focus into the Favorite rows, at their right-hand end — found while testing 2.18, and only visible when there are Favorite rows to fall into. `handleEdgeKey` handled Up and Down only; the Left/Right branches in `dispatchKeyEvent` belong to *carrying* a tile during Move, so in ordinary use nothing handled Left or Right at all, and past the last item the platform's own focus search picked whatever it could reach. A new branch swallows the key at the row's two ends — the first tile, and the **+**, which is the row's real last stop — so the row stops there and Down is the only way out. Guarded by `f.getParent() == dockRow` like every adapter-position lookup since 2.28, and `tests/test_focus_styles.py` now expects four |
| **2.28** | **Glass halo crashed the launcher on the Inputs tab.** `haloColour` asked `dockRow` and `homeGrid` for a view's adapter position without checking the view was theirs; `RecyclerView.getChildAdapterPosition` casts the view's layout parameters to its own without checking either, so an input tile — a **LinearLayout** child, on Home and in the Inputs tab alike — threw `ClassCastException` and **killed the process**. Pressing Down from the Inputs pill or the gear was enough, because `focusContent()` puts the focus on the first input tile itself. It now reads `v.getParent()` first and asks only the row that owns the tile, which is also what the old code should have done — it was asking two different RecyclerViews about one view. An input has no app colour, so it gets plain white. New `tests/test_focus_styles.py` holds the rule: every `getChildAdapterPosition` call must establish the view's parent first (verified to fail against the 2.26 code) |
| **2.27** | **The removed layout's config keys leave the APK.** 17 keys only the Apple TV layout ever read — `app_banners`, `fill_dock`, `banner_*`, `shelf_*`, `tile_names` and the rest — are no longer parsed by `Config`, and in `res/values/config.xml` they and their descriptions are now **one XML comment**, which aapt does not compile, so nothing of them reaches the package. `res/raw/keep.xml` is deleted, which lets resource shrinking drop `banner_tv` as well (the drawable file stays). The fields stay on `Config` as constants with their old defaults, so the Apple sources in the tree still compile — the removal is from the APK, as the owner's rule requires. `themes/README.md` records which keys a theme may still name and that they are ignored. Nothing visible changes. **Verified:** `aapt2 dump resources` finds **0** of the 17 keys and no `banner_tv`; APK **460,147 bytes** |
| **2.26** | **Effects, part 1.** `GtvActivity` reads **`GlowStyle`** at last — Corner, Random, Fixed per app, Slow drift (a ticker that runs only while Home is in front), Follow (above the focused tile), Random shape, Two glows and Random ×2/×3 (extra glow views in a hue-shifted colour) — plus the new **Ribbon**, a diagonal band instead of a spot. **Animations** and **Advanced effects** are honoured too: off means the focus zoom and the indicator reach their places at once, and advanced fades the glow's colour between apps instead of cutting. New **Focus style**: Zoom (today's), Lift, Glass halo (the tile's shadow in the app's colour, Android 9 and up) and Underline (a bar in the app's colour, no zoom). The **white ring is not offered** — the owner turned it down twice. Glow, Animations and Advanced effects are **visible in the panel again**; Cinematic and App names stay hidden, since neither means anything in this layout |
| **2.25** | **The banner can show a live input** (§6.7's last phase-2 pick). **Banner → Live input** puts a 260 dp 16:9 window of one HDMI port at the top of the banner, driven by the existing `InputPreview` — muted, offline, **no new permission**. **Live input source** picks the port. The window runs only while Home is in front with the banner on screen: `updateBanner()` is the single gate, and `onPause`, `open()` and `openInput()` each hand the input back before anything else asks the TV for it. **This is the first time anything in the Google TV layout has drawn `InputPreview`'s window** — the instance belonged to the Apple layout, so the old **Input preview** row had been storing a value nothing read; the banner style owns that switch now and the row is hidden |
| **2.24** | **Phase 2's Home items.** An **Inputs row on Home** under the Favorite rows (Layout › Inputs on Home, **off by default**), built from the same tiles as the Inputs tab through a shared `fillInputs(...)`; Down walks Your apps → Favorites → Inputs → the Apps tab. **Banner** becomes one choice page — *Name + glow · Glass panel · Off* — replacing 2.21's two switches, which migrate (either one set meant Glass panel). **Glass bars: On / Off** turns the frosted strip behind the tabs and the clock off. Home's height arithmetic counts sections now, and **the Inputs row stands down rather than squeeze tiles below 56 dp** — at density 2.0 with 2–3 Favorite rows, which this TV (density 1.0) never hits |
| **2.23** | **Your apps becomes a dock the owner keeps.** It is its own scrolling row now, not the first `columns` apps of one list: **Apps per row sets how many are on screen**, moving right brings the next one in, and the selected tile is kept in the **middle of the screen** except at the two ends (`CentreManager` answers `onRequestChildFocus` itself and scrolls to centre; the row clamps at its edges). The **last tile is a +** that lists every app not in the row, and **holding OK on an app offers Remove from Your apps**. The row is the owner's list in **`gtv_dock.txt`** (new `DockApps`, the shape of `Favorites`, MAX 32), seeded from `dock_apps` the first time so an updating TV keeps what it had. The page indicator hides above 12 apps rather than drawing a thicket of dots |
| **2.22** | **The launcher's clock screen is out of the APK** — the owner keeps the TV's own screensaver. The manifest no longer declares `ClockActivity` (Home's idle clock) or `ClockDream` (the selectable screensaver), `GtvActivity` loses the idle timer it gained in 2.21, and the **Clock** row is hidden in the panel. Verified: **0 mentions of either component in the packaged manifest, 0 class names in the dex, and `dream_clock.xml` shrunk out**. `ClockActivity.java`, `ClockDream.java`, `ClockScene.java` and both `dream_clock.xml` files stay in the tree — re-declaring the two components is all it takes to bring the feature back. **The clock in Home's top-right corner is untouched:** it is a `TextView` in the top bar fed by the minute `tick`, nothing to do with this |
| **2.21** | **The settings panel is finished, and the idle clock is back.** Choices now open as **a page inside the panel** (Wallpaper, Font, and Glow when it returns): the list steps aside, the page takes its place at the same width with the current choice marked, and Back returns to the list with the row still focused — no more pop-ups. New: **Banner title size** (Small 34 / Medium 46 / Large 58) and, in Effects, **Banner outline** and **Banner glass fill**, both off by default, which is 2.15's fill and border given back as switches. Five rows the Google TV layout does not read — Glow, Animations, Advanced effects, Cinematic, App names — are **hidden** behind `EFFECTS_READY` in `LauncherSettingsActivity`, values untouched. **Bug fix:** the idle clock (`ClockActivity`) had not started since 2.19 — `MainActivity.onUserInteraction` used to arm it and the shim dropped that; `GtvActivity` arms it now, cancelling on pause so it can never appear over an open app |
| **2.19** | **The Apple TV layout is out of the APK.** `MainActivity` is now a 141-line shim: it keeps the HOME and LEANBACK intent filters (the TV's default-home setting names it, so that must not move) and opens `GtvActivity`. The Apple layout was also the fallback when Home failed to build, so it is replaced by a **recovery screen** in the shim — "Home could not start", with **Try again** and **TV settings** — drawn from nothing but that class, since the configuration, theme or fonts may be what failed. The `layout` preference, `GtvActivity.enabled()` and `fallBack()` are gone; a failure is recorded in `gtv_failed` and cleared when Home next draws. R8 drops the now unreferenced Apple code: the APK falls **598 KB → 440 KB**. The sources stay in the tree: 2.20 deleted them and was reverted on the owner's word — the removal was only ever meant to be from the APK |
| **2.18** | **Page-indicator bug fix.** Holding left or right left a row of half-open dashes instead of one pill: `showDot()` animated only the new dot and the one before it, each from its resting width, so every dot further back was stranded part-way open when key repeat cut the 140 ms animation short. It now starts from the widths the dots actually have and moves all of them, so any interrupted run heals itself. Stepping one app at a time always worked, which is why 2.16 and 2.17 passed |
| **2.17** | The banner sits **18 dp lower**, its own 16 dp of padding now the only clearance it keeps from "Your apps" — that is as low as it can go without crossing the rows, so the next lever would be moving the rows down; the page-indicator dots moved **above** the "Last opened" line, which stops them jumping when an app has never been opened; the Home/Apps/Inputs capsules are frosted glass **with no outline**; and both CMU Sans fonts are **subset**, taking the APK from 893 KB to **598 KB** — 302 KB clear of the 900 KB gate |
| **2.16** | The banner sits just above Your apps instead of floating near the top, and a **page indicator** (an expanding dot) under it shows which dock app is selected. The dots cover **Your apps only** — in the Favorite rows, the Apps tab and Inputs the indicator hides rather than showing a position it does not describe |
| **2.15** | The banner is plain text over the glow — no fill, no outline (**the glass fill is owed back as an effects switch**); settings gains an **About this UI** section (version, layout, TV, Android, and what this is); the **Layout row is gone**, so Google TV is the only layout you can pick — the first part of removing the Apple TV layout |
| **2.14** | The settings panel is wider (420dp) with slightly smaller text, so more rows fit; the banner lost its outline, keeping the glass fill |
| **2.13** | Launcher settings is now a **panel down the right-hand side over Home**, in the TV's own style: Home stays visible and dimmed, the rows are grouped under section headings, and the clock and gear step aside while it is open. Every setting from the one-list version is kept. Choice pages come next |
| **2.12** | The selection frame is gone: a selected tile is simply zoomed and lifted. The owner tried the ring (2.09, 2.11) and dimming the others (2.10) and settled on the zoom alone |
| **2.11** | Selection is the white ring again (2.10's "dim everything else" was tried and turned down for ordinary selection); the carried tile keeps 2.10's lifted, ghosted look with the others faded; the banner shows **only while an app is selected**, so arriving at Home names nothing until Down picks one |
| **2.10** | The selected tile is now told apart by **dimming the others** (a white ring was invisible on white artwork such as Netflix or the Play Store); a tile being moved is lifted and ghosted instead of framed; and Home from another tab no longer leaves the banner naming whatever was selected over there |
| **2.09** | **Reverted 2.08's artwork change** (see below); the carried tile's frame now covers the artwork's edges; the hold-OK menu panel is solid, because tiles read straight through the sheer one over a bright wallpaper |
| **2.08** | The hold-OK menu opens above a tile when there is no room below (bottom-row apps were cut off); a load-average figure stands in where the TV blocks the processor reading; **and an artwork change that was measured worse and reverted in 2.09** |
| **2.07** | The card shrinks before it hides: every figure where there is room, then the essential few (processor, memory used, storage used, frames), then nothing. Before this it vanished entirely at 2 or more Favorite rows |
| **2.06** | Memory and storage also shown as used / total. The owner chose to **keep both** the free and used rows |
| **2.05** | The performance card (Launcher settings → Performance card, off by default) and its `Stats` reader; pressing Home now lands on the Home tab rather than the first app |

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
- Every reply to the owner ends with a status block (🟢 READY / 🟡 INPUT NEEDED / 🔴 BLOCKED).

---

## 2. Open work

### Waiting on a test
The build-by-build checklist is **[`docs/TESTING.md`](TESTING.md)** — work through it oldest-first, since each build leans on the one before.

**2.24 to 2.27 are still untested; 2.23 passed on 26 Oct with two defects** (below) — the owner asked for the builds to be made and checked later. Test 2.23's dock first, since the others build on it. 2.27 is cleanup with nothing to see: its test is that Home, the panel and the themes behave exactly as 2.26 did.

**2.25 carries the one item that may simply not work on this TV:** a non-system app tuning a passthrough input is allowed on some sets and refused on others, and nothing in this launcher had ever tried it. If the Sony refuses, the window says "Preview unavailable" and Home is otherwise untouched — that is an answer about the TV, not a bug to chase. What *would* be a bug: the TV's own Inputs or Live TV coming up dead after the banner has held an input, so test opening HDMI from the Inputs tab while the live banner is running.

For 2.24: switch **Inputs on Home** on and check nothing overlaps at Rows below dock 0 and 3, that an input opens full screen from Home, each of the three **Banner** styles, and **Glass bars** on and off. One thing that looks like a bug and is not: with the row switched on, a screen too short for it leaves the setting On while Home does not draw it — it stands down rather than make every tile unreadable.

2.23 changes Home's structure, so it needs a careful look: six tiles at *Apps per row = 6*, Right bringing the next app in with the selection staying centred, the first and last tiles resting at the edges, **+** adding, hold-OK removing, and **nothing overlapping at Rows below dock 0 and 3**. Up from the row must still land on the Home tab, and Down must reach the Favorite rows (or the Apps tab when there are none).

2.22 was a removal, so its test is that **nothing changed**: Home as before, **the top-right clock still ticking**, the TV's own screensaver still starting, and no Clock row in the panel.

2.18's page-indicator fix (hold left or right across the dock) has never been confirmed on screen either.

**Before installing 2.22 on any other set:** check `settings get secure screensaver_components`. If it names `com.gathin.tvhome/.ClockDream`, point the TV at its own screensaver first, or it falls back to the system default with no warning.

### Known bugs

**Fixed in 2.28, and confirmed on the TV: the Inputs tab killed the launcher.** With **Focus style = Glass halo**, pressing Down from the
Inputs pill or the gear crashed `GtvActivity` with `ClassCastException` in `haloColour`, every time — the screen
went black and Home came back because the process had died and been restarted. The log also showed Android
reacting to its home app dying: `Clearing package preferred activities from com.gathin.tvhome` and
`Adding package as fallback role holder … role: android.app.role.HOME`. It kept the default-home assignment this
time, but **a crashing home app can lose it**, which is why this counted as serious rather than cosmetic. The
recovery screen never appeared because that only catches a failure to *build* Home, not a crash after it is up.

*On reading the evidence, because this will come up again:* logcat keeps its buffer across an install, so a
`FATAL`/`ClassCast` line seen after updating may be the **old** crash still sitting there. After 2.28 went on,
three such lines were still visible and all three predated it — PID 28193 at 22:01:56.603 is the identical line
from the first report, and 28435 and 28743 at 22:03 are the restarts crashing again during the attempt to
reproduce. Run `adb logcat -c` before the check, or the result means nothing.

**Open: after Remove, the focus in Your apps is wrong (2.23).** Hold OK → Remove from Your apps works, but the focus
that follows does not land on the row's last app and sometimes looks random. Cause, from the code: `setInDock`
(`GtvActivity.java:526`) calls `focusDock` only when it *adds* (`if (inDock) dockRow.post(...)`), never when it
removes, and `notifyDataSetChanged()` rebuilds the tile that held the focus, so the platform picks. Fix in 2.30.
The owner's rule: after Remove the focus goes to **the row's last app**.

**Open: sometimes the app picked from the + is not the one that appears (2.23, cause unknown).** **Ruled out by the
code:** a package with several launcher activities entering twice — `loadApps` keeps one app per package
(`seen.add(pkg)`, `GtvActivity.java:397`), so the `com.sony.dtv.timers` pair in the log is not a candidate — and the
menu's loop variable, which is a fresh effectively-final per iteration, with `showMenu` pairing `titles.get(i)` to
`actions.get(i)`. **Leading suspect, shared with the entry above:** `setInDock` calls `notifyDataSetChanged()` and
then posts `focusDock`; a posted runnable can run *before* the layout pass, so the holder lookup returns null and
the focus is left to the platform — a race, which fits "บางครั้ง". **Unproven; it cannot be run here.** If it
survives 2.30, ask the owner whether the toast names the app they pressed, whether that app is in the row, and
which app and which place in the list it happens with.

**Open: the Favorite rows let Left/Right jump to the neighbouring row (found on 2.29).** Holding Left or Right at
the end of a Favorite row moves the focus to the other row instead of stopping. Same root cause as the 2.29 fix:
`homeGrid` is a `GridLayoutManager` (`:1730`), `handleEdgeKey` handles Up and Down only, and at a row's end the
grid steps to the next item in adapter order. Position 0 is the section header, so the column is
`(position − 1) % cols`. The Apps tab grid wraps the same way and the Inputs rows are likely to. Fix in 2.30.

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
 The bottom-row menu was fixed in 2.08 (`GtvActivity.showPanel()` now measures the panel and opens it
above the tile when it will not fit below, the same rule `MainActivity.onTileLongPressed()` uses).

### The APK size budget — resolved in 2.17
**2.27 is 449 KB against the 900 KB gate — 451 KB clear.** The squeeze is over: the two CMU Sans font files were
subset in 2.17, from 830 KB down to 227 KB, with no visible change (811 and 809 mapped characters, every glyph the
launcher draws present, all layout features kept). Thai, Hebrew and Chinese are unaffected — CMU never had those
glyphs, so they already fall back to the TV's own fonts.

History, because it explains the gate: the fonts arrived in PR #1 and took the APK to about 893 KB. CI's size gate
was 440 KB, which that APK could not meet; on the owner's instruction the gate in `.github/workflows/ci.yml` was
**raised to 900 KB**. `main` had never run CI, so nothing flagged the growth when the fonts landed. By 2.16 the
margin was 7 KB, and 2.17 trimmed the fonts rather than raising the gate a second time. The recipe, if the fonts
are ever regenerated:
```bash
pip install fonttools
python3 -m fontTools.subset launcher/app/src/main/res/font/cmu_sans.ttf \
  --unicodes=U+0020-007E,U+00A0-024F,U+0370-03FF,U+0400-04FF,U+2000-206F,U+20A0-20BF,U+2122,U+2190-21FF,U+2500-257F \
  --layout-features='*' --no-hinting --desubroutinize --output-file=cmu_sans.ttf
```
Also stale because of this: the README card image (`docs/images/card.jpg`) still reads **"429 KB launcher"**. The
README text, its alt text and `tools/readme_stats.py` now all say 880 KB, so a re-render will be correct — but the
picture itself could not be rebuilt here: `tools/readme_stats.py` and `readme_card.py` need `Fredoka.ttf`, which is
not in the repo, and this container's network policy refuses `fonts.google.com` and `cdn.jsdelivr.net`. To finish
it: get Fredoka (the variable TTF from Google Fonts), then
`tools/venv/bin/python -I tools/readme_stats.py Fredoka.ttf docs/images/stats.png`, the same for
`readme_featured.py`, then `readme_card.py Fredoka.ttf docs/images/card.png` — and note the README points at
`card.jpg`, so the PNG still has to be converted. Leave the TV-model count alone: `tests/test_examples.py` checks
it against the folders.

### Diagnosed: a streaming app lags (not a launcher bug)
**Symptom:** iQIYI / WeTV / Youku showed a black screen on a fresh start, then slow browsing and stuttering.

**Cause, measured over ADB:** the TV has 2.36 GB of RAM and reported **`status critical`, 301 MB free and 0 KB
cached**. Zero cached means no app can stay warm, so every launch starts from nothing and evicts something else.
**iQIYI alone held 308–425 MB**, five times any other app. The ad-blocking-DNS theory was ruled out (no resolver
set on the TV) and `/data` was 84% full.

**What fixed it (October 2025):**
```powershell
.\adb.exe shell am force-stop com.iqiyi.i18n.tv
.\adb.exe shell pm trim-caches 2G
.\adb.exe shell pm disable-user --user 0 com.google.android.tvrecommendations   # reverse: pm enable <pkg>
```
plus clearing iQIYI's cache on the TV (Settings → Apps → iQIYI → Clear cache; never "Clear data", it logs you out).
Result: **status normal, 741 MB free, 309 MB cached.**

**It comes back.** iQIYI grows again the longer it runs, so the force-stop is the repeatable reset. Nothing here
touches the app, the Play Store or Play services, so updates keep working — that was the owner's condition.

**Our own share, measured honestly:** the launcher is ~39 MB (peaks near 56 MB with the Apps grid open), of which
about **22 MB is app artwork held in the native heap** — bitmaps for every installed app, loaded up front in
`GtvActivity.loadApps()`. Its Java heap is 2.3 MB, so there is no leak. It is a minor contributor next to iQIYI,
**Tried in 2.08 and reverted in 2.09 — measured worse, not better.** Holding no artwork and loading it per tile in
`onBindViewHolder` meant every tile scrolling into view decoded a fresh copy, and the selection reloaded a banner
just to sample the glow colour. The native heap went **21.8 MB → 33.9 MB → 38.0 MB and was still climbing**; the
visible behaviour was fine (no blank tiles), the memory was not. Trading memory for repeated decoding is the wrong
trade on this TV.

**Not a leak — measured and cleared (2.09).** A clean run (`am force-stop`, then Home, then two or three minutes of
Home and the Apps tab with no settings visits) reports **`Activities: 1`, `Views: 59` and a steady native heap of
19.9 MB**. The 38–50 MB seen earlier came from the testing pattern itself: every settings toggle and every APK
install makes Home rebuild, and the discarded screens sit in memory until Android collects them. An hour of about
ten installs and a dozen toggles piles them up; ordinary use does not. Read `Activities` only after a clean start —
right after a visit to Launcher settings, two or three screens legitimately exist.

Worth improving one day, though not a bug: `GtvActivity` calls `recreate()` for **any** settings change
(`onResume`, `onActivityResult`, and the package receiver), which re-decodes every app's artwork. Most settings
could be re-applied without rebuilding.

**If this 18–22 MB is worth reclaiming, the approach to try is downsampling, not on-demand loading:** decode each
banner at roughly tile size (about 207×116 px at 8 per row) instead of its full resolution, keeping one copy per
app as now. That reduces the bytes rather than moving the cost around. Measure
`dumpsys meminfo com.gathin.tvhome` before and after, and do not claim a win without it — 2.08 is the cautionary
tale.

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
The owner decided to remove the Apple TV layout from the launcher. **From the APK, not from the repository:** the
code stays in the tree, and nothing on screen can reach it.

**Done in 2.19, and confirmed on the TV** (both recovery buttons included):
- `MainActivity` is a shim that keeps the HOME intent filter — the TV's default-home setting names
  `com.gathin.tvhome/.MainActivity`, and moving HOME to `GtvActivity` would make Android treat this as a different
  launcher, leaving Home to be chosen again with the stock launcher likely to win in between.
- The Apple layout was the fallback that stopped the TV being left with no Home. It is replaced by the shim's
  **recovery screen**: "Home could not start", with Try again and TV settings. It is drawn in code from nothing
  but that class, because the configuration, a pushed theme or the fonts may be exactly what failed.
- The `layout` preference is gone, with `GtvActivity.enabled()` and `fallBack()`. A failure now sets
  `gtv_failed` in the launcher's preferences (or is inferred from a dead `gtv_starting_pid`), and a successful
  draw clears both.

**Confirmed from the owner's logcat and photos:** Home comes up on HOME; the TV's default-home setting still points here
after installing; "Launcher settings" from the remote's Settings button still opens; and the recovery screen can
be reached on purpose —
`adb shell am force-stop com.gathin.tvhome; adb shell "am start -n com.gathin.tvhome/.MainActivity -a com.gathin.tvhome.action.RECOVERY"`
— with Try again returning to Home and TV settings opening the TV's own settings.

**There is no stage 2. The removal is from the APK, not from the repository** — the owner said so plainly after
a build (2.20) had deleted the sources, and that build was reverted (`5bdbb40`, reverted by `261f00d`). The Apple
TV layout's code, layouts, drawables, strings and documents **stay in the tree**. Do not delete them again.

Stage 1 is all it took, because R8 and resource shrinking drop what nothing references. Measured on the three
APKs:

| Build | Classes in the dex | classes.dex | APK |
|---|---|---|---|
| 2.18, the layout still reachable | 314 | 435 KB | 598 KB |
| **2.19, the shim, sources intact** | **230** | **302 KB** | **440 KB** |
| 2.20, sources deleted (reverted) | 222 | 293 KB | 427 KB |

So stage 1 took 84 classes and 158 KB out of the APK on its own, and deleting the sources would have bought a
further 8 classes and 13 KB. `aapt2 dump resources` on 2.19 lists four layouts — the settings panel, the quick
settings panel, the screensaver and the input row — so `activity_main.xml` and the rest of the Apple layout's
resources are already out of the package.

What is still shipped, and why, if it ever matters: `GlassView` and `SkyView` (AGP generates keep rules from
layout XML, and `activity_main.xml` is still in the source tree when R8 runs), and `Cards`, which is genuinely
reachable — `Config` parses the festive skin, `Festival.parse` calls `Cards.cleanLabel`. Getting those last three
out would mean changing code rather than deleting files, for 13 KB. **The owner's call: leave them.** The 13 KB
buys nothing, and the code is there to be picked up again if any of it comes back in the Google TV layout. Do not
"tidy" this away.

**Off limits, on the owner's instruction:** the remote's Settings button path stays exactly as it is.
`QuickSettingsActivity`, the `ACTION_LAUNCHER_SETTINGS` string and the shim's handling of it were not changed by
stage 1 (that code is 2.18's redirect branch line for line) and are not to be changed later either.

**This is a fork of `Gathin23/cleanyourtv`,** where that layout is the project's main feature, so the removal
diverges permanently and makes merging from upstream painful — the owner chose this knowingly.

### Next phases (from the proposal, [`docs/proposals/google-tv-ui/README.md`](proposals/google-tv-ui/README.md))
1. ~~**Settings redesign**~~ — **done** (2.13–2.14 the panel, 2.21 the choice pages and the banner title size,
   2.24 the banner styles). Glow, Animations and Advanced effects came back in 2.26. **Cinematic** and **App
   names** are still hidden behind `APPLE_ROWS_READY` in `LauncherSettingsActivity`: they described the Apple
   layout's shelf and its always-on tile names, and a later phase has to decide what — if anything — they mean
   here.
2. **Phase 2 layout**: ~~Inputs row on Home~~ (2.24), ~~Live-input banner~~ (2.25), ~~glass bars~~ (2.24) — what
   is left is the **side rail**, **Posters** and **Minimal** layout styles.
3. **Effects** (§4): part 1 is in 2.26 (glow styles, Ribbon, animations, focus styles). Left from §6.7: **glass
   tiles**, **tile light** (reflection), **title glow** and **colour wash**; and from §4.3 the focus extras
   (shine sweep, 3D tilt, breathing), the transitions and the background motion.
4. **Wallpapers** (§5): new sets, the 4K quality fix, online wallpapers (opt-in). **Check the licences first**
   for the sources listed in §8 Q7 (Wikimedia, Earth View, NASA, NatGeo, Apple) before any of them ship.
5. **Hebrew and polish**: right-to-left for the new screens, a Hebrew strings pass, final tidy-up.
6. **Merging**: done per set of builds — PR #1 (the Apple layout and the debloat), PR #2 (2.04), PR #3 (2.05–2.12),
   PR #4 (2.13–2.14), PR #5 (2.15–2.21), PR #6 (2.22–2.27), PR #7 (2.28). The owner is the only person with
   access, so nobody else reviews; merge once CI is green. **A merge does not mean tested** — PR #6 went in with 2.22–2.27 still
   unseen on the TV, at the owner's instruction, so the test backlog outlives the branch.

### Decisions already made (do not re-ask)
- **Ask before changing anything, every time** (the owner's rule, 26 Oct). Any instruction to modify something, in
  whole or in part, is answered with a proposal and a stop — what would change, in which files, the blast radius,
  how to undo it — and then waits for the owner's word. This holds however small or obvious the change looks, and
  it covers documentation as much as code. Reading, searching, `git log`, running the tests and building to verify
  change nothing and need no asking.
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
- Rows below dock sets the number of Favorite rows; Home sits on the bottom edge; ⌄⌄ marks the lowest row and
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
