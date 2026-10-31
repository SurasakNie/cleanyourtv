# Testing the launcher on the TV

Each build says **what changed and why** first, then what to check. Work through them **oldest-first**: every build
leans on the one before it, so a fault in 2.23 explains faults in 2.24 and later.

Anything that fails: stop on that build and say which line — more useful than a list of everything that happened
afterwards.

**A name that changes.** The setting these checklists call **Rows below dock** is renamed **Favorite apps rows** in
2.30. Until then the TV still shows the old label.

## Before you start

```
.\adb.exe connect 192.168.1.3:5555
.\adb.exe shell dumpsys package com.gathin.tvhome | findstr versionName
.\adb.exe install -r tvhome-2.31.apk
```

**The rule that applies to every build:** nothing on Home may overlap. The banner, the performance card, the rows
and the ⌄⌄ arrow each step aside rather than sit on top of one another. Check at **Rows below dock 0** and again at
**3** — that is where it breaks if it is going to.

---

## 2.18 — the page indicator (**passed on the TV, 26 Oct**)

**What changed.** `GtvActivity.showDot()` animated only two dots — the new one and the one before it — and started
each from its *resting* width. Key repeat moves focus faster than the 140 ms animation, so every dot further back
was stranded part-way open: a row of half-open dashes instead of one pill. It now reads the widths the dots
actually have and moves all of them, so an interrupted run heals itself.

- [x] **Hold** Left and Right across Your apps for a few seconds, then let go: one pill, every other dot small
- [x] A short hold that ends mid-animation leaves no half-open dash

---

## 2.22 — the clock screen is out of the APK (**passed on the TV, 26 Oct**)

**What changed.** The launcher had two clock screens: `ClockActivity` (Home's idle clock) and `ClockDream` (a
screensaver you could pick in the TV's settings). You keep the TV's own screensaver, so both are **undeclared in
the manifest** and the idle timer 2.21 added to `GtvActivity` is gone. R8 then drops the classes and
`dream_clock.xml` from the APK — verified: no clock component in the packaged manifest, no class names in the dex.
The `.java` files stay in the repository. The **Clock** row in Launcher settings is hidden.

**Not touched:** the clock in Home's top-right corner. That is a `TextView` in the top bar on a minute ticker —
a different thing entirely.

- [x] HOME brings up the launcher as before
- [x] The **top-right clock still ticks**
- [x] The TV's own screensaver still starts when the TV is left alone
- [x] Launcher settings has **no Clock row**

---

## 2.23 — Your apps is a scrolling dock

**What changed — the biggest of the five.** Your apps used to be "the first *n* apps of the sorted list", where *n*
was Apps per row. It could not hold more than fitted on screen, and there was no way to choose what was in it.

- It is now its own **horizontally scrolling row** (`dockRow`), outside the grid that holds the Favorite rows.
  *Apps per row* means **how many are on screen**, not how many exist.
- A new `CentreManager` answers RecyclerView's `onRequestChildFocus` itself and scrolls the focused tile to the
  **middle of the screen**; the row clamps at its own ends, so the first and last tiles rest at the edges. It also
  lays out a tile's width either side, so focus cannot fall out of the row at its edges.
- The **last tile is a +** that lists every app not in the row.
- **Hold OK** on an app offers **Remove from Your apps**. The last app cannot be removed.
- The row is the owner's own list in `gtv_dock.txt` (new `DockApps`, built like `Favorites`), **seeded from
  `dock_apps`** the first time, so the row you had carried over.
- Two deliberate consequences: **Move no longer crosses** between Your apps and the Favorite rows (it reorders
  within each), because membership is now changed on purpose with + and hold-OK; and the page indicator **hides
  above 12 apps** rather than drawing a dot for each.
- Home's height arithmetic was reworked, since the row brings its own padding.

Set **Apps per row = 6** before testing.

- [x] Six tiles are on screen
- [x] Holding **Right** brings the next app in, and the selected tile stays in the **middle of the screen**
- [x] The **first** and **last** tiles rest at the edges instead of being dragged to the middle
- [x] Right past the last app reaches the **+**; it opens a list of apps not in the row *(reaching the + was
      exercised by the 2.29 test)*
- [x] Picking one adds it to the end and leaves the selection on it *(but see the known intermittent defect: now
      and then the app that appears is not the one picked — cause not found)*
- [x] **Hold OK** → **Remove from Your apps** takes an app out — *it worked, but the focus afterwards was wrong: it
      did not land on the row's last app and sometimes seemed random (nothing set it on removal). Fixed in 2.30 and
      confirmed on the TV 27 Oct: ten removes, the focus on the last app every time*
- [x] The **last** app cannot be removed (a message says so) — *no clear result at the time; replaced in 2.30 by a
      rule — the Remove entry is hidden once the row holds Apps per row apps — which passed on the TV 27 Oct*
- [x] **Up** from the row lands on the **Home tab**
- [x] **Down** reaches the Favorite rows, or the Apps tab when there are none *(exercised by the 2.29 test)*
- [x] Rows below dock **0** and **3**: nothing overlaps
- [x] Hold OK → **Move** still reorders within the row, and within the Favorite rows — *Your apps: the 2.29 test;
      the Favorite rows: 27 Oct*
- [x] More than 12 apps in the row: the dots disappear

---

## 2.24 — the Inputs row, Banner style, Glass bars

**What changed.**
- **Inputs row on Home**, under the Favorite rows, **off by default** (Layout › Inputs on Home). It is built from
  the same tiles as the Inputs tab — `fillInputs(...)` is now shared by both — so HDMI, AV and Live TV look
  identical in either place. Focusing an input clears the banner's selection, since an input is not an app.
- Down now walks **Your apps → Favorites → Inputs → the Apps tab**.
- **Banner** became one choice page: *Name + glow* (what it was), *Glass panel* (the dark panel with a hairline
  that 2.15 removed), *Off*. It replaces 2.21's two switches, whose values migrate — either one set meant Glass
  panel. With Banner off, **Banner title size** is greyed out.
- **Glass bars: On / Off** for the frosted strip behind the tabs and the clock.
- The height arithmetic counts **sections** now. With three sections on a short screen tiles would shrink past
  reading, so the **Inputs row stands down** below 56 dp — like the banner and the performance card, which
  already step aside. On this TV (density 2.0, inferred from the run — confirm with `wm density`) it **does** trigger, at Favorite rows 2 and 3.

- [x] **Inputs on Home** starts **Off**; switching it on adds the row
- [x] An input on that row opens full screen
- [x] With the row on: Rows below dock **0** and **3**, nothing overlaps *(at 3 the row has stood down, so there is
      nothing to overlap)*
- [x] Each **Banner** style: Name + glow, Glass panel, Off
- [x] With Banner off, **Banner title size** is greyed out
- [x] **Glass bars** off: the strip behind the tabs and the clock goes, the pills stay
- [ ] Down from Your apps reaches Favorites, then Inputs, then the Apps tab — *at Favorite rows 2 the Inputs row is not
      drawn, so the walk goes Favorites → Apps tab. Designed (it stands down below 56 dp — this TV is density 2.0, so
      that is 112 px, and the row has 84 px at 2 rows), but silent. Retired when 2.31 hides the row*

---

## 2.25 — the live input banner

**What changed.** Banner gained a fourth style, **Live input**: a 260 dp 16:9 window of one HDMI port above the
app's name, with **Live input source** to pick the port. It is driven by `InputPreview`, the class that already
tunes, mutes (`setStreamVolume(0f)`) and reports "connecting", "no signal" and "unavailable". Offline through
`TvView` — **no new permission**, which is the point of doing it this way rather than with camera streams.

The window runs **only** while Home is in front with the banner on screen; `onPause`, `open()` and `openInput()`
each stop it first, so the input goes back to the TV before anything else asks for it. The "Last opened" line
steps aside while the window is up, so the banner stays near its old height.

**Worth knowing:** this is the first time anything in the Google TV layout has drawn `InputPreview`'s window — the
instance belonged to the Apple layout and its XML, so the old **Input preview** settings row had been storing a
value nothing read. The banner style owns that switch now and the row is hidden.

**This is the one item that may simply not work on this TV.** A non-system app is not allowed to show a
passthrough input on every set, and it has never been tried here. A clear message instead of a picture is an
answer about the TV, not a bug.

- [x] Banner → **Live input**, then **Live input source** → HDMI 2 (the NVR)
- [ ] The window shows the picture, **or** a clear message — **FAILED 27 Oct: a plain black rectangle with no message.**
      The code shows its status text in every failure, and hides it only when the system reports the video available,
      so this reads as *available but black*. Evidence requested (log + screencap); cause not found
- [x] Open an app, press HOME: Home returns and the window starts again
- [x] **The important one:** with the live banner running, open HDMI from the Inputs tab, or Live TV on the
      remote. The picture must be the TV's own — **not a black screen.** Black means the launcher is holding an
      input it should have handed back: say so and stop

---

## 2.26 — effects, part 1

**What changed.** `GtvActivity` reads `GlowStyle` **at last**. Until now the glow was one fixed radial in the top
corner, and the nine styles in the settings changed nothing at all. Now Corner, Random, Fixed per app, Slow drift
(a ticker that runs only while Home is in front), Follow (above the focused tile), Random shape, Two glows and
Random ×2/×3 (extra glow views in a hue-shifted colour) all do what they say, plus a new **Ribbon** — a diagonal
band instead of a spot.

- **Animations** and **Advanced effects** are honoured too: off means the focus zoom and the page indicator reach
  their places at once instead of sliding; advanced fades the glow's colour between apps rather than cutting.
  `reduced_motion` counts as animations off.
- New **Focus style**: *Zoom* (what every build so far did), *Lift*, *Glass halo* (the tile's own shadow painted
  in the app's colour, Android 9 and up) and *Underline* (a bar in the app's colour, with no zoom). The white
  ring is **deliberately not offered** — it was tried in 2.09 and 2.11 and turned down both times.
- Glow, Animations and Advanced effects are **visible in the panel again**. **Cinematic** and **App names** stay
  hidden: they described the Apple layout's shelf and its always-on tile names and mean nothing here.

- [x] **Glow** now has an effect. Worth trying: *Corner* (the old look), *Follow*, *Fixed per app*, *Slow drift*,
      *Ribbon*. **"Random position and shape" shows a dark bar at the top and a thicker dark area at the bottom, some of
      the time — a bug (the glow view is squashed smaller than the screen); Classic removes it. Fixed in 2.31**
- [x] **Slow drift** moves while Home is on screen and stops when you open an app or another tab
- [x] **Animations** off: the focus zoom and the dots jump instead of sliding
- [x] **Advanced effects** on: moving between apps **fades** the glow's colour
- [x] Each **Focus style**: Zoom, Lift, Glass halo, Underline
- [x] Glow, Animations and Advanced effects are back in the panel; **Cinematic** and **App names** are still gone

---

## 2.27 — the removed layout's config keys leave the APK

**What changed. Nothing you can see; this is cleanup.** 17 keys that only the Apple TV layout ever read —
`app_banners`, `fill_dock`, the `banner_*` and `shelf_*` group, `tile_names` and the rest — were still being
parsed by `Config` and still shipped as string resources, months after the layout itself left the APK in 2.19.

- `Config` no longer reads any of them. The **fields stay as constants** with their old defaults, so the Apple
  sources still in the tree go on compiling — the removal is from the APK, never from the repository.
- In `res/values/config.xml` the 17 entries and their descriptions became **one XML comment**. Comments are not
  compiled, so the names, the defaults and the notes stay readable in the repo and none of it reaches the package.
- `res/raw/keep.xml` is **deleted**. It existed to protect drawables named at run time; with the Apple layout gone
  its only live effect was keeping `banner_tv` in the APK, so resource shrinking now drops that too. The
  `banner_tv` drawable file itself stays in the tree.
- `themes/README.md` lists the keys a theme may still name and says plainly that they are now ignored.

**Verified before the build left here:** `aapt2 dump resources` finds **0** of the 17 keys and no `banner_tv`;
219 tests pass; signer `a215696a…436e`; 3 permissions; **460,147 bytes** against the 921,600-byte gate.

- [x] Home looks and behaves exactly as 2.26 did — this build should be indistinguishable
- [x] Launcher settings opens and every row still works
- [ ] **Follow theme** / a theme push still applies: a theme naming one of the 17 old keys must be **accepted and
      the key ignored**, not rejected — *skipped 27 Oct: no theme file to try*
- [x] Nothing from `com.gathin.tvhome` in logcat

---

## 2.28 — the Inputs tab no longer kills the launcher

**What changed.** A real crash, found on the TV and reproduced from the log. With **Focus style = Glass halo**,
pressing **Down** on the Inputs tab threw `ClassCastException` and the launcher's process **died** — which is why
the screen went black and Home came back: Android restarted it.

`haloColour` asked `dockRow` and then `homeGrid` for a view's adapter position without checking the view belonged
to either. `RecyclerView.getChildAdapterPosition` casts the view's layout parameters to RecyclerView's own without
checking them, so an **input tile** — which lives in a `LinearLayout`, on Home and in the Inputs tab alike — threw.
Down from the Inputs pill or the gear was enough on its own, because `focusContent()` hands the focus to the first
input tile. It now reads the view's parent and asks only the row that owns it. Inputs have no app colour, so their
halo is plain white.

**Worth knowing:** the crash made Android clear the launcher's preferred-activity registration. It stayed the
default home this time, but a home app that keeps crashing can lose that and hand the TV back to Sony's launcher.

- [x] Set **Focus style = Glass halo**. On the **Inputs tab**, press **Down** — the launcher must **not** blink and
      return to Home. Before 2.28 this crashed every time
- [x] Same with the **gear** focused: Down onto the inputs row, no crash
- [x] Switch **Inputs on Home** on, still on Glass halo: move Down onto that row — no crash there either
- [x] The halo still shows the **app's own colour** on Your apps and the Favorite rows
- [x] An input tile's halo is white rather than coloured — correct, an input has no app colour
- [x] The other three focus styles (Zoom, Lift, Underline) are unchanged

---

## 2.29 — Your apps keeps the focus at its right edge

**What changed.** Found by the owner while testing 2.18, which is not something the checklist was looking for:
holding **Right** to the end of Your apps dropped the focus **down into the Favorite rows**, onto their right-hand
end. Holding Left to the other end was fine. It only shows when there are Favorite rows below to fall into.

The cause is that ordinary navigation had **nothing handling Left or Right at all**. `handleEdgeKey` takes Up and
Down; the Left/Right branches in `dispatchKeyEvent` belong to *carrying* a tile during Move. Past the row's last
item the platform's own focus search picked whatever focusable it could reach, and that was a tile in the rows
below — which is also why it was lopsided: a heuristic, not our code treating the ends differently. A new branch
swallows the key at the row's two ends, so the row stops at its first tile and at the **+**, and Down is the only
way out.

**Set Rows below dock to 1 or more first** — with none, there is nothing to fall into and the old behaviour looks
identical.

- [x] Hold **Right** to the end of Your apps: the focus stops on the **+** tile and **stays in the row**
- [x] Keep holding Right: nothing happens — no jump into the Favorite rows, no wrap-around
- [x] Hold **Left** to the other end: it stops on the first tile and stays there
- [x] **Down** from anywhere in the row still reaches the Favorite rows — the route this must not break
- [x] **Up** from the row still lands on the Home tab
- [x] Hold OK → **Move** still carries a tile left and right along the row, and still reorders
- [x] The page-indicator dots still follow the selection (the 2.18 fix, unchanged)
- [x] **Failed on 2.29, fixed in 2.30 and confirmed on the TV 27 Oct:** holding Left or Right in the **Favorite rows** jumps to the neighbouring row instead
      of stopping at the end. Same cause as the Your apps fault — the grid steps to the next item in adapter order —
      in a different container

---

## 2.30 — focus at the ends of every row, the dock after an edit, and two settings

**What changed — seven things, one build, so a failure should name its line.** The owner chose one build for all of
it; every change is a separate commit, and each has its own lines below.

1. **Favorite rows: Left/Right stop at the ends.** Found on 2.29, which fixed Your apps: the same fault one
   container down. The grid stepped to the next item in adapter order, which is the neighbouring row.
2. **Apps tab: Left/Right stop at the ends.** This is a **change in behaviour** — the Apps grid used to wrap from the
   end of one row to the start of the next. The owner chose stopping.
3. **Inputs rows (on Home and in the Inputs tab): the same.**
4. **After adding or removing an app in Your apps the focus lands on the row's last app, every time.** It used to
   be set only when adding — and by a runnable posted before the layout pass, so sometimes the platform chose
   instead. Removing set nothing at all. Now it waits for a layout pass.
5. **Remove from Your apps is hidden while the row holds Apps per row apps or fewer.** The minimum is Apps per row.
6. **"Rows below dock" is now "Favorite apps rows"** (English and Hebrew; the stored value is not renamed).
7. **At Favorite apps rows = 3 the Performance card is forced off** — its settings row greys out and reads Off, with
   a line saying why. The choice you made is kept and comes back when you lower the rows.

**Set Favorite apps rows to 1 or more first** for lines 1 and 2 — with none, there is nothing to jump into.

- [x] **1.** Favorite rows: hold **Right** to the end of a row — the focus stops there; hold **Left** — it stops at the
      start; neither jumps to the row above or below. **Down** and **Up** still move between rows
- [x] **1.** The last Favorite row has fewer apps than a full row: Right stops on its last app
- [x] **2.** Apps tab: hold Right to the end of a row and Left to the start — it stops instead of wrapping to the next row
- [x] **3.** Inputs tab, and the Inputs row on Home (switch **Inputs on Home** on): Left/Right stop at the first and last
      input and do not jump anywhere. *Holding a key can skip the zoom on some icons: expected — key repeat is faster
      than the 140 ms animation, so the tiles in between are focused too briefly to finish zooming. The dock does
      the same*
- [x] **4.** From the **+**, add an app: the focus lands on **the app you added**, at the end of the row. Do it **ten
      times** with different apps — this is the intermittent fault, so one try proves nothing
- [x] **4.** Remove an app: the focus lands on the **last app in the row**, every time (ten tries again)
- [x] **5.** With exactly Apps per row apps in the row, hold OK on one: the menu has **no Remove entry**. Add one more
      and Remove appears. *Expect this on a fresh install: the seeded row is small, so Remove is absent until the
      row has grown past Apps per row*
- [x] **6.** The setting reads **Favorite apps rows** *(not reported on its own; the whole run used it without comment)*
- [ ] **7.** **FAILED 27 Oct** — the row was **not greyed and still toggled, read On, and showed no explanation**, while the
      card **was** gone from Home. Lowering to 2 brought the card back with the setting still On (that half passed). The
      panel's logic reads correctly and the cause is not found; evidence requested (`dumpsys` of the panel at rows = 3,
      plus a screenshot). Set it to **3**: the Performance card row is grey, reads **Off**, and a line explains why; the card is not
      on Home. Switch the card **On** first (rows 0–2), then set rows to 3, then back to 2 — the card returns, and
      the setting still said On
- [x] The other focus paths are unchanged: Move still carries a tile; Down from Your apps still reaches the Favorite rows

**If the intermittent "wrong app appears" fault survives:** it was never reproduced and 2.30's fix is built from
reading the code, not from seeing it. Note whether the **toast names the app you pressed**, whether that app is
in the row, and **which app and which place in the list** it happened with.

---

## 2.31 — the glow bars, the Inputs row hidden, and a clearer name

**What changed — three things, one commit each.**

1. **The dark bars are gone.** With Glow style **Random position and shape**, a dark band about 83 px tall showed
   across the top and a thick dark area at the bottom, with hard edges, some of the time. It was my 2.26 code:
   `placeGlow` squashed the full-screen glow view to make an ellipse, and with a ratio below 1 the view became
   smaller than the screen. The view now only ever grows — the same ellipse, a rectangle that always covers the
   screen. A screenshot of the framebuffer showed the bars, which is how it was known to be the launcher and not the
   TV's panel or the Glass bars.
2. **The Inputs row on Home is out of the APK** (the owner's call). It stood down silently at Favorite rows 2–3 on this
   TV, and what is wanted instead is a live window per input. Kept in the repository behind one flag; the setting
   and its hint are hidden; a value saved earlier is ignored, not erased. The **Inputs tab is unchanged.**
3. **"Animations" is now "Focus & indicator animations"** (English and Hebrew; other languages keep their word). The
   stored value is not renamed, so the choice you made is kept.

- [ ] **1.** Set Glow style to **Random position and shape** and move the focus around Home **thirty times or more**
      (rows, tabs, back to the dock): **no dark bar at the top and no heavy dark area at the bottom, ever.** The glow
      still changes shape and place from move to move
- [ ] **1.** Take a `screencap` while the style is on, at a moment it used to show: the framebuffer has no band
- [ ] **1.** The other styles look as before: Classic, Follow selected app, Fixed per app, Slow drift, Ribbon,
      Random position, Two glows
- [ ] **2.** Launcher settings › Layout has **no "Inputs on Home" row and no hint for it**
- [ ] **2.** Home has no Inputs row at **Favorite apps rows 0, 1, 2 and 3**; Down from the last row goes to the Apps tab
- [ ] **2.** The **Inputs tab** still lists HDMI 1–4, opens an input full screen, and Left/Right stop at its ends
- [ ] **3.** The row reads **Focus & indicator animations: On/Off**; switching it still turns the focus zoom and the dots
      from sliding to jumping, and **Advanced effects** still greys out with it
- [ ] The Performance-card and Live-input faults are **not** touched by this build; they stay open

---

## 2.32 — the live window must not flash

**What changed.** With **Banner › Live input**, at Favorite apps rows 1–3 a black rectangle showed for a fraction of
a second when an app was selected, then went. At 0 rows the HDMI picture showed. From the code (not proven on the
TV): selecting an app showed the banner on a "fits" value that could be stale, so the banner and its window came
up and the next fit hid them again. Now selecting an app re-measures the fit first, and the window starts only one
step later with every condition read again. Every stop (opening an app, opening an input, leaving Home) also
cancels a start still waiting. **On this TV the live window still works only at 0 Favorite rows** — option A,
chosen 27 Oct.

Setup: Launcher settings › Banner = **Live input**, Live input source = **HDMI 2** (the port that showed a picture).
Run `.\adb.exe logcat -c` first.

- [ ] **1.** Favorite apps rows = **0**: Down onto Your apps → the banner shows the **HDMI 2 picture**, muted (as in 2.31)
- [ ] **1.** Still at 0: move Left/Right through Your apps ten times → the picture stays, no black frame between apps
- [ ] **2.** Rows = **1**: select an app (Down onto Your apps, then Left/Right) → **no black rectangle at all**, not even for a moment
- [ ] **2.** Rows = **2**: same → no black rectangle
- [ ] **2.** Rows = **3**: same → no black rectangle
- [ ] **2.** At rows 1–3, also Down into the Favorite rows and back up → no black rectangle
- [ ] **3.** Back at rows 0, with the picture showing: Up to the Home pill → the banner and picture go (by design);
      Down again → they return
- [ ] **4.** With the picture showing: OK on an app → the app opens normally with its sound, and Home → the picture returns
- [ ] **4.** With the picture showing: Inputs tab → OK on HDMI 2 → the TV's own full-screen picture, with sound
      (the hand-back still works)
- [ ] **5.** Banner = Name + glow, then Glass panel: both look as in 2.31 at rows 0 and 3
- [ ] **6.** Launcher settings › About shows **2.32**
- [ ] **7.** `.\adb.exe logcat -d | findstr /i "FATAL AndroidRuntime gathin"` → nothing from `com.gathin.tvhome`

If rows 1–3 still flash, run `.\adb.exe logcat -d > live.txt` straight away and send it: the cause is then
elsewhere (the banner frame itself, say), and reading the code will not find it.

**Result of the owner's run, 31 Oct (2.32 installed, `versionName=2.32` confirmed; density 320 = 2.0 confirmed; the log
has no FATAL / AndroidRuntime).** Rows 0: **no flash**. Rows 1–3: **no window at all** (as option A intends). Item 3
above (the picture goes when the focus is on the Home pill) is **superseded**: the owner wants the window to stay on the
Home page — built as 2.33. The boxes above are not individually ticked; this is the owner's summary.

---

## 2.33 — the live window stays on Home when no app is selected

**What changed.** With **Banner › Live input**, the window vanished as soon as the focus left the app tiles for the
Home tab or the gear, because the banner showed only while an app was selected. Now the live style shows whenever
Home is in front and the banner fits; the app **name and the page dots hide** when no app is selected (they keep their
room, so the window does not move). The other banner styles are unchanged: they still need a selected app. Apps and
Inputs tabs still hide the window and hand the input back. Rows 1–3 still show no window (option A).

Setup: Banner = **Live input**, source = **HDMI 2**, Favorite apps rows = **0**. Run `.\adb.exe logcat -c` first.

- [ ] **1.** Arrive on Home with the focus on the **Home pill** → the HDMI 2 picture is already showing, with no app name
- [ ] **2.** Down onto Your apps → the picture stays, the app name and dots appear under it, **nothing jumps**
- [ ] **3.** Up to the Home pill again → the picture stays; name and dots go
- [ ] **4.** Right to the **gear** → the picture stays
- [ ] **5.** **Apps** tab and **Inputs** tab → the picture goes
- [ ] **6.** Back to Home → the picture returns; HDMI from the Inputs tab still shows the TV's own full-screen picture with sound
- [ ] **7.** OK on an app, then Home → the app opens normally; on return the picture comes back
- [ ] **8.** Rows = **1, 2, 3** → still no window and no black flash
- [ ] **9.** Banner = Name + glow, then Glass panel, then Off: unchanged from 2.32 (needs a selected app; Off shows nothing)
- [ ] **10.** Launcher settings › About shows **2.33**
- [ ] **11.** `.\adb.exe logcat -d | Select-String "FATAL|AndroidRuntime|com.gathin.tvhome"` → no FATAL or AndroidRuntime

If the picture flickers while moving between the pill, the tiles and the gear, send `.\adb.exe logcat -d | Out-File -Encoding utf8 live.txt`.

---

## After each build

```
.\adb.exe logcat -d | findstr /i "FATAL AndroidRuntime gathin"
```

Nothing from `com.gathin.tvhome` is a pass. Crashes from WeTV (`com.tencent.qqlivei18n`) and the permission
controller are other apps' and are recorded in the handoff — ignore them.

## If Home will not start at all

The recovery screen added in 2.19 says "Home could not start", with **Try again** and **TV system settings**. To
reach the TV's own home picker without it, or to go back to a build that worked:

```
.\adb.exe shell am start -a android.settings.HOME_SETTINGS
.\adb.exe install -r tvhome-2.22.apk
```
